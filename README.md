# Bazar Solidário da FAM

Projeto de Software (T01). O sistema ajuda a cadastrar peças doadas, consultar as disponíveis e registrar vendas. O projeto se relaciona ao ODS 12, meta 12.5.

## Versão de demonstração

Esta versão usa dados de exemplo na memória, sem acessar o PostgreSQL. As alterações ficam disponíveis enquanto o servidor estiver ligado e são perdidas ao reiniciá-lo. O arquivo `banco.sql` e as consultas da versão anterior foram preservados para a futura integração com o banco. Categoria e valor ainda precisarão ser adicionados ao esquema e às consultas quando o PostgreSQL for reconectado.

Depois do login, o painel mostra a quantidade de peças e os gráficos de situação e de vendas nos últimos sete dias. A aplicação também permite cadastrar categoria e valor sugerido, buscar uma peça por nome ou código, filtrar por categoria e consultar o histórico de vendas. O painel mostra vendas de hoje e as últimas vendas. Os gráficos usam Chart.js carregado pela internet; se não carregar, os números continuam visíveis.

## Como executar

1. Instale Java 21 e Maven ou use o NetBeans com JDK 21.
2. Use o usuário `demo` e a senha `123456` na tela de entrada.
3. Na pasta do projeto, execute `mvn compile exec:java`. No NetBeans, abra o projeto e execute a classe `bazar.Bazar`.
4. Acesse http://127.0.0.1:8080 no navegador. O servidor desta versão aceita apenas acessos da própria máquina.

## Como testar

Entre com `demo` e a senha `123456`. No painel, observe os totais e gráficos. Cadastre uma doação com categoria e valor, busque a peça pelo nome ou código, filtre pela categoria, registre a venda e confira o histórico e a atualização do painel. Ao reiniciar a aplicação, os dados de exemplo voltam ao estado inicial.

A rotina de CI testa a API com dados simulados. A conexão com PostgreSQL será retomada após a aprovação dos testes desta etapa.
