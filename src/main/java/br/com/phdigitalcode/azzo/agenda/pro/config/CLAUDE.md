# config/ — configuração do Spring

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta. A segurança HTTP em si (filtros, rotas
públicas) está em `SecurityConfig` e é descrita em `security/CLAUDE.md`.

## O que tem

| Classe | Para que serve |
|---|---|
| `SecurityConfig` | `SecurityFilterChain`, `@EnableMethodSecurity`, rotas públicas, ordem dos filtros, CORS |
| `StartupSecurityValidator` | No profile `prod`, **aborta a subida** se segredos críticos faltarem ou estiverem com valor padrão inseguro; em dev só avisa |
| `LgpdContactStartupValidator` | Exige `LGPD_CONTACT_EMAIL` em prod |
| `CacheConfig` | Caffeine: `rbac-user-permissions` (5 min, 200 mil entradas) e `menu-routes-by-tenant-role` (5 min, 10 mil) |
| `CamposDesconhecidosConfig` | Campo desconhecido no JSON é **ignorado e logado**, de propósito (recusar quebraria clientes antigos) |
| `JacksonLegacyConfig` | Bean do `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2), usado por construtor em ~23 classes portadas |
| `MailConfig` | SMTP (`MAIL_*`); em produção sai pela Mailtrap |
| `TracingConfig` | Tracing (Tempo). **Não** gera trace das rotas do actuator (Prometheus coleta a cada 30 s, o Docker consulta a saúde) |
| `DevHttpsConnectorConfig` | HTTPS só em dev, com `cert.pem`/`key.pem` locais (ignorados pelo git) |

## Cuidados

- **Dois `ObjectMapper`:** o Spring Boot 4 usa Jackson 3 (`tools.jackson...`) por padrão, e o código portado usa o Jackson 2 pelo bean
  da `JacksonLegacyConfig`. Ao injetar, confira o `import`; o tipo errado compila e quebra em runtime.
- **Em dev nada falha por segredo ausente; em prod tudo falha.** Subir local não prova que a produção sobe: confira as variáveis do
  `.env.example`. Valores reais só no `.env` do servidor.
- Mudou `SecurityConfig`? Teste uma rota pública, uma autenticada, uma interna (`/api/v1/internal/**`) e um webhook (`/webhook/*`).
- `application.yml` define `open-in-view: false`, `ddl-auto: none`, Flyway com `out-of-order: true` e `baseline-on-migrate: true`.
