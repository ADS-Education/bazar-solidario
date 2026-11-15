package bazar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Bazar {
    private static final Map<String, Instant> SESSOES = new HashMap<>();
    private static final String TIPO_TEXTO = "text/plain; charset=UTF-8";
    private static final int SESSAO_SEGUNDOS = 30 * 60;

    public static void main(String[] args) throws IOException {
        for (String variavel : new String[]{"BAZAR_DB_PASSWORD", "BAZAR_PASSWORD"}) {
            String valor = System.getenv(variavel);
            if (valor == null || valor.isBlank()) {
                System.err.println("Configure a variável " + variavel + " antes de executar.");
                return;
            }
        }

        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        servidor.createContext("/", Bazar::atender);
        servidor.start();
        System.out.println("Bazar Solidário da FAM: http://127.0.0.1:8080/");
    }

    private static void atender(HttpExchange troca) throws IOException {
        String rota = troca.getRequestMethod() + " " + troca.getRequestURI().getPath();
        try {
            if (rota.equals("GET /") || rota.equals("GET /style.css") || rota.equals("GET /app.js")) {
                String arquivo = rota.equals("GET /") ? "index.html" : rota.substring(5);
                String tipo = switch (arquivo) {
                    case "index.html" -> "text/html; charset=UTF-8";
                    case "style.css" -> "text/css; charset=UTF-8";
                    default -> "text/javascript; charset=UTF-8";
                };
                try (InputStream conteudo = Bazar.class.getResourceAsStream("/web/" + arquivo)) {
                    troca.getResponseHeaders().set("Content-Type", tipo);
                    troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                    troca.getResponseHeaders().set("Content-Security-Policy",
                            "default-src 'self'; frame-ancestors 'none'; form-action 'self'");
                    byte[] dados = conteudo.readAllBytes();
                    troca.sendResponseHeaders(200, dados.length);
                    troca.getResponseBody().write(dados);
                }
                return;
            }

            if (!rota.equals("POST /api/entrar") && !rota.equals("POST /api/sair")
                    && !rota.equals("GET /api/pecas") && !rota.equals("POST /api/pecas")
                    && !rota.equals("POST /api/vendas")) {
                responder(troca, 404, "Página não encontrada.");
                return;
            }

            if (rota.startsWith("POST ") && !"1".equals(troca.getRequestHeaders().getFirst("X-Bazar"))) {
                responder(troca, 403, "Requisição não permitida.");
                return;
            }

            if (rota.equals("POST /api/entrar")) {
                Map<String, String> dados = lerFormulario(troca);
                String usuario = System.getenv().getOrDefault("BAZAR_USER", "demo");
                if (!usuario.equals(dados.get("usuario")) || !System.getenv("BAZAR_PASSWORD").equals(dados.get("senha"))) {
                    responder(troca, 401, "Usuário ou senha inválidos.");
                    return;
                }
                Instant agora = Instant.now();
                SESSOES.values().removeIf(vencimento -> !vencimento.isAfter(agora));
                SESSOES.remove(lerSessao(troca));
                String sessao = UUID.randomUUID().toString();
                SESSOES.put(sessao, agora.plusSeconds(SESSAO_SEGUNDOS));
                troca.getResponseHeaders().set("Set-Cookie", "bazar=" + sessao + "; Path=/; HttpOnly; SameSite=Strict");
                responder(troca, 200, "Sessão iniciada.");
                return;
            }

            String sessao = lerSessao(troca);
            Instant agora = Instant.now();
            Instant vencimento = SESSOES.get(sessao);
            if (vencimento == null || !vencimento.isAfter(agora)) {
                SESSOES.remove(sessao);
                responder(troca, 401, "Entre para continuar.");
                return;
            }

            if (rota.equals("POST /api/sair")) {
                SESSOES.remove(sessao);
                troca.getResponseHeaders().set("Set-Cookie", "bazar=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
                responder(troca, 200, "Sessão encerrada.");
                return;
            }

            SESSOES.put(sessao, agora.plusSeconds(SESSAO_SEGUNDOS));
            try (Connection banco = DriverManager.getConnection(
                    System.getenv().getOrDefault("BAZAR_DB_URL", "jdbc:postgresql://127.0.0.1:5432/bazar?connectTimeout=5"),
                    System.getenv().getOrDefault("BAZAR_DB_USER", "postgres"),
                    System.getenv("BAZAR_DB_PASSWORD"))) {
                switch (rota) {
                    case "GET /api/pecas" -> listar(troca, banco);
                    case "POST /api/pecas" -> cadastrar(troca, banco);
                    case "POST /api/vendas" -> vender(troca, banco);
                }
            }
        } catch (IllegalArgumentException erro) {
            responder(troca, 400, "Dados inválidos.");
        } catch (SQLException erro) {
            System.err.println("Falha no banco. Código SQL: " + erro.getSQLState());
            responder(troca, 503, "Não foi possível acessar o banco. Tente novamente.");
        } finally {
            troca.close();
        }
    }

    private static void listar(HttpExchange troca, Connection banco) throws SQLException, IOException {
        String sql = """
                SELECT COALESCE(json_agg(json_build_object('id', p.id, 'description', p.descricao)
                                         ORDER BY p.id), '[]'::json)
                FROM peca p
                WHERE NOT EXISTS (SELECT 1 FROM venda v WHERE v.peca_id = p.id)
                """;
        try (var consulta = banco.prepareStatement(sql); var resultado = consulta.executeQuery()) {
            resultado.next();
            responder(troca, 200, resultado.getString(1), "application/json; charset=UTF-8");
        }
    }

    private static void cadastrar(HttpExchange troca, Connection banco) throws SQLException, IOException {
        String descricao = lerFormulario(troca).getOrDefault("descricao", "").strip();
        if (descricao.isBlank() || descricao.length() > 100 || descricao.indexOf(0) >= 0) {
            responder(troca, 400, "Informe a descrição com até 100 caracteres.");
            return;
        }

        try (var comando = banco.prepareStatement("INSERT INTO peca (descricao) VALUES (?) RETURNING id")) {
            comando.setString(1, descricao);
            try (var resultado = comando.executeQuery()) {
                resultado.next();
                responder(troca, 201, "Doação salva. Código #" + resultado.getInt("id") + ".");
            }
        }
    }

    private static void vender(HttpExchange troca, Connection banco) throws SQLException, IOException {
        int id = Integer.parseInt(lerFormulario(troca).getOrDefault("id", ""));
        if (id <= 0) {
            responder(troca, 400, "Código da peça inválido.");
            return;
        }

        String sql = """
                INSERT INTO venda (peca_id)
                SELECT id FROM peca WHERE id = ?
                ON CONFLICT (peca_id) DO NOTHING
                """;
        try (var comando = banco.prepareStatement(sql)) {
            comando.setInt(1, id);
            if (comando.executeUpdate() == 1) {
                responder(troca, 201, "Venda registrada.");
            } else {
                responder(troca, 409, "Esta peça já não está disponível.");
            }
        }
    }

    private static Map<String, String> lerFormulario(HttpExchange troca) throws IOException {
        byte[] bytes = troca.getRequestBody().readNBytes(4097);
        if (bytes.length > 4096) {
            throw new IllegalArgumentException("Formulário muito grande.");
        }

        Map<String, String> dados = new HashMap<>();
        for (String campo : new String(bytes, StandardCharsets.UTF_8).split("&")) {
            String[] partes = campo.split("=", 2);
            if (partes.length == 2) {
                dados.put(partes[0], URLDecoder.decode(partes[1], StandardCharsets.UTF_8));
            }
        }
        return dados;
    }

    private static String lerSessao(HttpExchange troca) {
        String cookies = troca.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String cookie : cookies.split(";")) {
                String valor = cookie.trim();
                if (valor.startsWith("bazar=")) {
                    return valor.substring(6);
                }
            }
        }
        return "";
    }

    private static void responder(HttpExchange troca, int codigo, String texto) throws IOException {
        responder(troca, codigo, texto, TIPO_TEXTO);
    }

    private static void responder(HttpExchange troca, int codigo, String texto, String tipo) throws IOException {
        byte[] dados = texto.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", tipo);
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.sendResponseHeaders(codigo, dados.length);
        troca.getResponseBody().write(dados);
    }
}
