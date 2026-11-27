# CLAUDE.md — api_agenda (API do salão)

Orientação para quem for mexer **nesta API**. O contexto geral do Azzo (estrutura de pastas, fluxo de
publicação, regras de commit) está no `CLAUDE.md` e no `LEIA-PRIMEIRO.md` da raiz do projeto
(`../../../../`). Este arquivo só cobre o que é específico daqui.

## O que é

A API de **produção** do produto Azzo Agenda: tudo que o salão usa (agenda, clientes, comanda/PDV,
financeiro, estoque, fiscal/NFS-e, WhatsApp/chat, fidelidade, área da cliente). É consumida pelas zonas
Next.js em `../../frontend/` (a zona `app` repassa `/api/*` para cá, na mesma origem, para o cookie
httpOnly funcionar) e é chamada pelo `api_assistant` e pelo `api_gerenciamento` por rotas internas.

- Spring Boot **4.1.1**, Java **25**, Maven (`./mvnw`), Spring **MVC** (servlet, **não** WebFlux).
- JPA/Hibernate + **Flyway**, Postgres, schema **`azzo_app`**, `ddl-auto: none`, `open-in-view: false`.
- Pacote raiz: `br.com.phdigitalcode.azzo.agenda.pro`. Porta `8080`.
- Código migrado do Quarkus original (`azzo-agenda-pro`): muitos comentários citam "equivalente Spring de…".
  O `README.md` desta pasta está **desatualizado** (cita 124 migrations e Spring Boot 4.1.0); confie no código.

## Como o código está organizado

Organizado **por tipo**, não por módulo. Um módulo funcional atravessa várias pastas, então para achar um
assunto procure pelo nome em `controller/`, `service/`, `repository/`, `entity/`, `dto/`:

| Pasta | O que tem |
|---|---|
| `controller/` | REST, prefixo `/api/v1/...` (~57 controllers) |
| `service/` | regra de negócio (~180 classes; nomes em português `Servico*` ou inglês `*Service`, conviveram) |
| `entity/`, `repository/`, `specification/` | JPA |
| `dto/`, `mapper/` | contratos de entrada/saída |
| `security/` | JWT, RBAC, tenant, rate limit, filtros |
| `scheduler/` | tarefas agendadas (~29) |
| `integration/` | clientes externos: Asaas, Meta/WhatsApp, Telegram, MinIO, e-mail, CNPJ, ViaCEP, auditoria |
| `integration/pagamentos/` | provedores de pagamento (asaas, cielo, mercadopago, pagarme, pagbank, rede, pixchave, tef) |
| `saude/` | anamnese, ficha de atendimento, fotos, **área da cliente** (`/minha-area`), LGPD de saúde |
| `config/`, `exception/`, `util/`, `health/` | configuração, `GlobalExceptionHandler`, utilitários |

## Mapa dos módulos funcionais

| Módulo | Onde olhar | Cuidados |
|---|---|---|
| **Auth e sessão** | `AuthController`, `security/JwtService`, `AuthCookieService`, `RefreshTokenService`, `TotpService` | JWT RSA em cookies `AZZO_ACCESS_TOKEN`/`AZZO_REFRESH_TOKEN`; MFA (TOTP); senha com política (`PasswordPolicyValidator`) |
| **Multi-tenant** | `security/ContextoTenant` | `tenant_id` vem do JWT; o header `X-Tenant-Id` só vale no fluxo interno do assistant. **Falha se faltar tenant** (sem default silencioso). Toda consulta de negócio tem de filtrar por tenant |
| **Permissões** | `@PreAuthorize("hasAnyRole('OWNER','PROFESSIONAL','STAFF')")` + `@RequiresPermission("pos:coupons")` (AOP em `RequiresPermissionAspect`) | Papel grosso (`@PreAuthorize`) **e** permissão fina (`@RequiresPermission`); perfis em `ServicoPerfisDeAcesso`; cache em `RbacPermissionCache`/`MenuRouteCache` |
| **Licença/plano** | `LicenseFilter`, `LicenseStatusService`, `ServicoLiberacaoDeDocumentoDeTrial` | Plano vencido: `402 PLAN_EXPIRED` em toda rota autenticada não-ADMIN, exceto login/registro/refresh, catálogo público, perfil do salão, menus, `/me` e o módulo de billing (senão ninguém paga). `/webhook/*` é isento; quem o barra é o próprio controller. O filtro ficou sem porte até 28/09/2026 |
| **Agenda e agendamento** | `AgendamentosController`, `ServicoAgendamentos`, `AppointmentService`, `ServicoPublicBooking`, `SpecialClosureController`, `ServicoConfirmacaoDeAgendamento` | Conflito de horário (`AppointmentConflictException`); sinal/depósito expira (`AppointmentDepositExpirationScheduler`); no-show e lembretes por scheduler; agendamento público em `PublicSalonsController` |
| **Clientes e saúde** | `ClientesController`, `ClienteService`, `ServicoImportacaoClientes`, pasta `saude/` | Importação roda em scheduler assíncrono (`ProcessadorImportacaoClientesScheduler`); anamnese/ficha/fotos são dado sensível |
| **LGPD** | `LgpdTitularController`, `ServicoAnonimizacaoTitular`, `ServicoExportacaoTitular`, `LgpdRetentionService` | Cliente anonimizado fica com nome `[ANONIMIZADO]` (o front depende disso); o startup exige `LGPD_CONTACT_EMAIL` em prod |
| **Comanda, caixa, financeiro** | `Comanda`, `FechamentoCaixa`, `Financeiro`, `CommissionController`, `TaxasDePagamento`, `RecurringTransactionScheduler` | `commission_percent` é numérico (V99); taxas de pagamento em `CalculoDaTaxaDePagamento` |
| **Cupons, vales, pacotes, fidelidade** | `CuponsController`, `ServicoDeCupons`, `ServicoDeVales`, `ServicoPackages`, `ServicoDePontos`, `ServicoMembershipPlans` | Vale = hash de UUID + documento; cupom de lote leva os 6 dígitos do salão; expiração por scheduler |
| **Pagamentos integrados** | `PagamentosController`, `ServicoDePagamentos`, `integration/pagamentos/*`, `ConectorTefController`, `WebhookDePagamentoController`, `TenantPaymentWebhookController` | Em produção **oculto para todos** até haver salão piloto (`PAGAMENTOS_SALOES_PILOTO`). Não apagar o código |
| **Cobrança do salão (assinatura)** | `BillingController`, `ServicoBilling`, `CheckoutController`, `Asaas*BillingHandler` | Asaas (PIX, boleto, assinatura); `ConsultaDeCobrancasScheduler` |
| **Fiscal / NFS-e** | `FiscalController`, `NfseController`, `ServicoFiscal`, `Nfse*`, `FiscalDocumentStateMachine`, `CofreDoCertificado` | Provedores `AbrasfDirectProviderAdapter` e `SefinNacionalProviderAdapter`; `FISCAL_PROVIDER` default `mock`; máquinas de estado, idempotência, polling e PDF por scheduler; certificado cifrado. Salão sem CNPJ tem tratamento próprio |
| **Estoque** | `EstoqueController`, `ServicoEstoque`, `ProcessadorImportacaoEstoqueScheduler` | Importação XLSX (corpo máximo 20 MB no Caddy) |
| **Chat, WhatsApp, Telegram** | `ChatController`, `WhatsAppController`, `WhatsAppWebhookController`, `TelegramWebhookController`, `MetaEmbeddedSignupGateway`, `ChatRealtimePublisher` | `/api/v1/chat/stream` é **SSE** (mensagem nova em tempo real) e **não pode ser comprimida** pelo proxy; há heartbeat. Templates de WhatsApp têm scheduler de status; retenção de mensagens por scheduler |
| **Integração com o assistant** | `AssistantInternalController` (`/api/v1/internal/assistant/**`), `AssistantApiClient`, `AssistantCacheInvalidationService` | O assistant chama **esta** API para saber serviços, profissionais e horários livres |
| **Rotas internas** | `security/InternalApiKeyFilter` | Header `X-Internal-Api-Key`, **uma chave por serviço** (SEG-018): a do assistant só vale em `/internal/assistant/**`, a da Gestão só em `/internal/plans/**` (`InternalPlansController`). O caminho é comparado normalizado (SEG-001) |
| **Notificações e e-mail** | `NotificationService`, `NotificationPublisher`, `EmailJobScheduler`, `EmailJobProcessor` | E-mail por fila de jobs (executor virtual) |
| **Menus e configurações** | `MenuConfigController`, `MenuService`, `MenuOverrideService`, `SettingsController` | O menu do front vem daqui (`/config/menus/current`) |
| **Relatórios e dashboard** | `Relatorios`, `Dashboard`, `RankingDeProfissionais`, `ReportMaterializedViewRefreshScheduler` | Views materializadas atualizadas por scheduler (leva 1–5 s); o `REFRESH` passa por uma função criada na V165 |
| **Auditoria** | `AuditService`, `SeladorDaAuditoria`, `ConferenciaDaAuditoria`, `AuditoriaController` | Tabela `audit_events` é **append-only** (gatilho `trg_deny_audit_events_mutation`) e encadeada por hash; retenção por scheduler |
| **Admin do sistema** | `SystemAdminController`, `ServicoOnboarding`, `OnboardingController` | Cadastro de planos feito por SQL não gera o registro `ADMIN_PLAN_CREATE` |

## Banco e migrations (leia antes de criar uma)

- Migrations em `src/main/resources/db/migration/` (V1…V188 até 27/11/2026). `out-of-order: true`. **Nunca edite
  uma migration já aplicada**; crie uma nova.
- **Flyway roda com o dono do banco** (`FLYWAY_DB_USERNAME`). A API roda como o papel **`azzo_api`**, sem
  superusuário e sem posse das tabelas: lê e grava dados, mas **não cria/altera tabela, não faz TRUNCATE e não
  desliga gatilho**. Tabelas novas herdam os direitos por `ALTER DEFAULT PRIVILEGES`. Ver `scripts/banco/papel-da-api.sql`.
- **Antes de mesclar migration que escreve em tabela existente, leia o banco real de produção** (somente
  leitura: gatilhos em `pg_trigger`, `CHECK` e índices da tabela). Em 02/10/2026 a V141 passou no CI e derrubou a
  API por ~15 min porque `audit_events` é append-only. Teste com repositório mockado e o CI não pegam isso.
  Migration aditiva (coluna com DEFAULT, tabela nova) é de baixo risco; `UPDATE` em dado existente não é.
- Datas no banco em **UTC** (`jdbc.time_zone: UTC`).

## Armadilhas já pagas

1. **Bean com dois construtores:** marque o principal com `@Autowired` e crie um teste que instancie o bean pelo
   Spring. Em 20/10/2026 o `InternalApiKeyFilter` entrou em laço de reinício por 8 min em produção porque a suíte
   toda passava sem carregar o contexto.
2. **Suíte verde não prova que a API sobe.** Depois de cada deploy confira: imagem nova, contêiner `healthy` e
   "Started Application" no log.
3. **Em `prod` o startup é fail-closed:** exige `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`, `ENCRYPTION_KEY`,
   `LGPD_CONTACT_EMAIL` e `META_APP_SECRET`. Variáveis de rate limit usam o prefixo `*_RATE_LIMIT_*`.
4. **`RateLimitFilter`, auditoria e billing usam o primeiro valor de `X-Forwarded-For`.** Se um dia entrar CDN/proxy na frente
   (há plano em `PLANO-CDN-CLOUDFLARE.md` na raiz), o Caddy precisa confiar no proxy (`trusted_proxies`), senão
   todo usuário vira o IP do proxy.
5. **Arquivos locais ignorados pelo git** (não commite nem copie o conteúdo): `cert.pem` e `key.pem` (HTTPS de dev,
   `DevHttpsConnectorConfig`), chaves RSA de dev em `src/main/resources/privateKey.pem`/`publicKey.pem`, e os
   `hs_err_pid*.log`/`replay_pid*.log` (relatórios de queda da JVM; podem ser apagados).

## Comandos

```bash
./mvnw -o test                                         # unitários, mais rápido que esperar o CI
./mvnw -o test -Dtest=SpecialClosureControllerTest     # uma classe
./mvnw -B clean verify -Dtest='!*IntegrationTest'      # o que o CI roda (unitários)
./mvnw -B test -Dtest='*IntegrationTest'               # integração: Testcontainers, precisa de Docker
./mvnw -o -q -DskipTests compile                       # só compilar
```

- Rodar local: pela config `api-agenda-local` do `.claude/launch.json` da raiz (perfil `dev`, porta 8080). Precisa
  do Postgres local (contêiner Docker `azzo-local-pg`, banco `azzo`, `127.0.0.1:5432`, mantido sempre de pé; não
  recrie). Swagger em `/swagger-ui.html` (só fora de `prod`); saúde em `/actuator/health`.
- O CI (`.github/workflows/docker-image.yml`) roda unitários, depois integração com Testcontainers, e só então
  publica a imagem. **Teste vermelho impede a publicação.**
- Variáveis de ambiente: veja `.env.example` (nomes). Valores de produção ficam só em `/opt/azzo-app/.env` no
  servidor; nunca coloque segredo em arquivo versionado.

## Publicação

`branch a partir de develop` → commit → `merge --no-ff` → push → **conferir o CI** → conferir em produção
(Watchtower publica a imagem `:develop` em ~1 min). Nunca publicar em `master` no dia a dia. Commits sem
coautoria do Claude. Mudança que o dono ainda não viu rodando local não vai para `develop`.
