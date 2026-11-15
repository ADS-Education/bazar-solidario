# Bazar Solidário da FAM

Projeto de Software, Experiência Prática III (T01). O sistema cadastra peças doadas, lista as disponíveis e registra vendas. A organização das peças para reúso se relaciona ao ODS 12, meta 12.5.

## Como rodar

1. Crie um banco chamado `bazar` no PostgreSQL. Abra esse banco no DBeaver ou pgAdmin e execute o arquivo `banco.sql`.
2. Configure `BAZAR_DB_PASSWORD` com a senha do banco e `BAZAR_PASSWORD` com a senha que você quer usar na tela de login. O usuário da tela é `demo` por padrão.
3. No NetBeans, use **Arquivo > Abrir Projeto** e escolha a pasta `bazar-solidario` (onde fica o `pom.xml`). Use o JDK 21 ou superior e clique em **Executar Projeto**. Se o NetBeans perguntar pela classe principal, escolha `bazar.Bazar`.
4. No navegador, abra [http://127.0.0.1:8080](http://127.0.0.1:8080).

No Windows, configure as duas variáveis em **Editar as variáveis de ambiente da sua conta** e reabra o NetBeans para que ele as carregue. Se o banco estiver em outro endereço ou usar outro usuário, configure também `BAZAR_DB_URL` e `BAZAR_DB_USER`. Os padrões são `jdbc:postgresql://127.0.0.1:5432/bazar` e `postgres`. Se mudar o usuário do sistema, defina `BAZAR_USER`.

Para executar pelo terminal, use `mvn compile exec:java` na pasta do projeto, com as mesmas variáveis configuradas. O servidor desta versão escuta apenas na máquina local.

## Como testar

Entre com o usuário `demo` e a senha configurada. Consulte a camiseta azul de código #21, cadastre uma calça preta e registre a venda. Ao voltar à lista, a calça deve ter saído. Os dados ficam no PostgreSQL.
