# api_agenda

Migracao Spring Boot do backend `azzo-agenda-pro` (Quarkus). Ver
`backend/spring-boot-app/MIGRACAO-QUARKUS-SPRING.md` para o inventario completo e o registro do
que foi migrado em cada etapa.

- Java 25 (LTS)
- Spring Boot 4.1.0
- Spring MVC (`spring-boot-starter-web`, servlet — **nao** WebFlux)
- Spring Data JPA + Hibernate + Flyway (schema `azzo_app`, mesmo banco do Quarkus original)
- Spring Security (`SecurityFilterChain` + `@EnableMethodSecurity`, JWT RSA custom)

## Rodando localmente

Pre-requisitos: JDK 25, Maven, PostgreSQL 16 rodando (local ou via `docker-compose up postgres`).

```bash
# Subir apenas o Postgres (reaproveita o docker-compose do azzo-agenda-pro ou o deste projeto):
docker compose up -d postgres

# Compilar e rodar os testes:
mvn clean verify

# Subir a aplicacao em modo dev (perfil dev usa privateKey.pem/publicKey.pem do classpath):
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A aplicacao sobe em `http://localhost:8080`. Swagger UI (apenas fora do perfil `prod`):
`http://localhost:8080/swagger-ui.html`. Health check: `http://localhost:8080/actuator/health`.

## Variaveis de ambiente (mesmos nomes do Quarkus original)

| Variavel | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Datasource PostgreSQL |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | Chaves RSA do JWT (obrigatorias em `prod`) |
| `ACCESS_TOKEN_TTL_MINUTES`, `REFRESH_TOKEN_TTL_DAYS` | TTL de tokens |
| `AUTH_COOKIE_SECURE`, `AUTH_COOKIE_SAME_SITE`, `AUTH_COOKIE_DOMAIN` | Cookies `AZZO_ACCESS_TOKEN`/`AZZO_REFRESH_TOKEN` |
| `ENCRYPTION_KEY` | Chave AES-256 (MFA secret) — obrigatoria em `prod` |
| `INTERNAL_API_KEY` | Chave para rotas `/api/v1/internal/*` (assistant-api) |
| `CORS_ORIGINS` | Origens permitidas (separadas por virgula) |
| `PUBLIC_BOOKING_BASE_URL` | Base URL do frontend (link de reset de senha) |
| `LGPD_CONTACT_EMAIL`, `META_APP_SECRET` | Validados no startup em `prod` (fail-closed) |
| `LOGIN_RATE_LIMIT_*`, `REGISTER_RATE_LIMIT_*`, `FORGOT_PASSWORD_RATE_LIMIT_*`, `RESET_PASSWORD_RATE_LIMIT_*`, `FISCAL_RATE_LIMIT_*` | Buckets de rate limiting (bucket4j) |

## Docker

```bash
docker compose up --build
```

## Escopo migrado nesta etapa (Etapa 3 + Etapa 4)

- Esqueleto completo do projeto (pom, config, estrutura de pacotes, 124 migrations Flyway
  copiadas sem alteracao, Dockerfile, docker-compose).
- Dominio `security/common`: `GlobalExceptionHandler` (equivalente ao `ApiExceptionMapper`),
  `SecurityConfig`/`SecurityFilterChain`, `JwtAuthenticationFilter` (bridge cookie->Bearer +
  validacao JWT + checagem de revogacao), RBAC fino (`RequiresPermission` + AOP), rate limiting
  (bucket4j-core), `EncryptionService`, `TotpService`, `SecurityHeadersFilter`,
  `StartupSecurityValidator`.
- Dominio `auth`: login, refresh, logout, forgot-password, reset-password, `/me` com fidelidade
  total ao Quarkus original. `register` e uma versao simplificada (ver javadoc de
  `AuthServiceImpl`) ate os modulos `billing`/`audit`/`tenant`/`professionals` serem migrados.

Pendencias detalhadas: ver `MIGRACAO-QUARKUS-SPRING.md`.
