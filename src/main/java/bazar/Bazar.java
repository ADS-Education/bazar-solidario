package bazar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Bazar {
    private static final Map<String, Instant> SESSOES = new HashMap<>();
    private record Peca(int id, String descricao, String categoria, BigDecimal valor) {}
    private record Venda(int pecaId, LocalDate dataVenda) {}
    private static final List<Peca> PECAS = new ArrayList<>(List.of(
            new Peca(21, "Camiseta azul", "Roupas", new BigDecimal("15.00")), new Peca(22, "Calça jeans", "Roupas", new BigDecimal("25.00")),
            new Peca(23, "Vestido floral", "Roupas", new BigDecimal("30.00")), new Peca(24, "Bolsa preta", "Acessórios", new BigDecimal("20.00")),
            new Peca(25, "Blusa branca", "Roupas", new BigDecimal("15.00")), new Peca(26, "Tênis infantil", "Calçados", new BigDecimal("22.00"))));
    private static final List<Venda> VENDAS = new ArrayList<>(List.of(
            new Venda(25, LocalDate.now().minusDays(2)), new Venda(26, LocalDate.now().minusDays(1))));
    private static int proximoId = 27;
    private static final String TIPO_TEXTO = "text/plain; charset=UTF-8";
    private static final int SESSAO_SEGUNDOS = 30 * 60;

    public static void main(String[] args) throws IOException {
        String host = System.getenv().getOrDefault("BAZAR_HOST", "127.0.0.1");
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer servidor = HttpServer.create(new InetSocketAddress(host, porta), 0);
        servidor.createContext("/", Bazar::atender);
        servidor.start();
        System.out.println("Bazar Solidário da FAM iniciado na porta " + porta);
    }

    
    private static void atender(HttpExchange troca) throws IOException {
        String rota = troca.getRequestMethod() + " " +  troca.getRequestURI().getPath();
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
                            "default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; frame-ancestors 'none'; form-action 'self'");
                    byte[] dados = conteudo.readAllBytes();
                    troca.sendResponseHeaders(200, dados.length);
                    troca.getResponseBody().write(dados);
                }
                return;
            }

            if (!rota.equals("POST /api/entrar") && !rota.equals("POST /api/sair")
                    && !rota.equals("GET /api/pecas") && !rota.equals("POST /api/pecas")
                    && !rota.equals("POST /api/vendas") && !rota.equals("GET /api/dashboard")
                    && !rota.equals("GET /api/historico")) {
                responder(troca, 404, "Página não encontrada.");
                return;
            }

            if (rota.startsWith("POST ") && !"1".equals(troca.getRequestHeaders().getFirst("X-Bazar"))) {
                responder(troca, 403, "Requisição não permitida.");
                return;
            }

            if (rota.equals("POST /api/entrar")) {
                Map<String, String> dados = lerFormulario(troca);
                if (!"demo".equals(dados.get("usuario")) || !"123456".equals(dados.get("senha"))) {
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
            switch (rota) {
                case "GET /api/pecas" -> listarExemplo(troca);
                case "POST /api/pecas" -> cadastrarExemplo(troca);
                case "POST /api/vendas" -> venderExemplo(troca);
                case "GET /api/dashboard" -> dashboardExemplo(troca);
                case "GET /api/historico" -> historicoExemplo(troca);
            }
        } catch (IllegalArgumentException erro) {
            responder(troca, 400, "Dados inválidos.");
        } finally {
            troca.close();
        }
    }

    private static boolean vendida(int id) {
        return VENDAS.stream().anyMatch(venda -> venda.pecaId() == id);
    }

    // Escapa os caracteres pra descrição nao quebrar o JSON.
    private static String json(String valor) {
        StringBuilder saida = new StringBuilder("\"");
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '\"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> { if (c < 32) saida.append(String.format("\\u%04x", (int) c)); else saida.append(c); }
            }
        }
        return saida.append("\"").toString();
    }

    private static void listarExemplo(HttpExchange troca) throws IOException {
        List<String> itens = new ArrayList<>();
        for (Peca p : PECAS) {
            if (!vendida(p.id())) itens.add("{\"id\":" + p.id() + ",\"description\":" + json(p.descricao()) + ",\"categoria\":" + json(p.categoria()) + ",\"valor\":" + p.valor().toPlainString() + "}");
        }
        responder(troca, 200, "[" + String.join(",", itens) + "]", "application/json; charset=UTF-8");
    }

    private static void cadastrarExemplo(HttpExchange troca) throws IOException {
        Map<String, String> dados = lerFormulario(troca);
        String descricao = dados.getOrDefault("descricao", "").strip();
        if (descricao.isBlank() || descricao.length() > 100 || descricao.indexOf(0) >= 0) {
            responder(troca, 400, "Informe a descrição com até 100 caracteres.");
            return;
        }
        String categoria = dados.getOrDefault("categoria", "");
        if (!List.of("Roupas", "Calçados", "Acessórios", "Outros").contains(categoria)) {
            responder(troca, 400, "Selecione uma categoria válida."); return;
        }
        BigDecimal valor;
        try {
            valor = new BigDecimal(dados.getOrDefault("valor", ""));
            if (valor.scale() > 2 || valor.signum() < 0 || valor.compareTo(new BigDecimal("999999.99")) > 0) throw new NumberFormatException();
        } catch (NumberFormatException erro) {
            responder(troca, 400, "Informe um valor válido de até R$ 999.999,99."); return;
        }
        int id = proximoId++;
        PECAS.add(new Peca(id, descricao, categoria, valor));
        responder(troca, 201, "Doação salva. Código #" + id + ".");
    }

    private static void venderExemplo(HttpExchange troca) throws IOException {
        int id = Integer.parseInt(lerFormulario(troca).getOrDefault("id", ""));
        if (id <= 0) { responder(troca, 400, "Código da peça inválido."); return; }
        boolean existe = PECAS.stream().anyMatch(p -> p.id() == id);
        if (!existe || vendida(id)) { responder(troca, 409, "Esta peça já não está disponível."); return; }
        VENDAS.add(new Venda(id, LocalDate.now()));
        responder(troca, 201, "Venda registrada.");
    }

    private static void dashboardExemplo(HttpExchange troca) throws IOException {
        List<String> dias = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate data = LocalDate.now().minusDays(i);
            long total = VENDAS.stream().filter(v -> v.dataVenda().equals(data)).count();
            dias.add("{\"data\":" + json(data.toString()) + ",\"total\":" + total + "}");
        }
        long vendasHoje = VENDAS.stream().filter(v -> v.dataVenda().equals(LocalDate.now())).count();
        String corpo = "{\"vendasHoje\":" + vendasHoje + ",\"cadastradas\":" + PECAS.size() + ",\"disponiveis\":" + (PECAS.size() - VENDAS.size())
                + ",\"vendidas\":" + VENDAS.size() + ",\"vendasPorDia\":[" + String.join(",", dias) + "]}";
        responder(troca, 200, corpo, "application/json; charset=UTF-8");
    }

    private static void historicoExemplo(HttpExchange troca) throws IOException {
        List<String> itens = new ArrayList<>();
        for (int i = VENDAS.size() - 1; i >= 0; i--) {
            Venda v = VENDAS.get(i);
            for (Peca p : PECAS) if (p.id() == v.pecaId()) {
                itens.add("{\"id\":" + p.id() + ",\"description\":" + json(p.descricao()) + ",\"categoria\":" + json(p.categoria()) + ",\"valor\":" + p.valor().toPlainString() + ",\"dataVenda\":" + json(v.dataVenda().toString()) + "}");
            }
        }
        responder(troca, 200, "[" + String.join(",", itens) + "]", "application/json; charset=UTF-8");
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
