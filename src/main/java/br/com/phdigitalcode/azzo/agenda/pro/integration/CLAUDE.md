# integration/ — clientes de sistemas externos e serviços de apoio

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Pagamentos têm pasta própria: `pagamentos/CLAUDE.md`.

## O que tem

| Assunto | Classes |
|---|---|
| Asaas (cobrança do salão e do cliente) | `AsaasClient`, `AsaasService`, `AsaasPixService`, `AsaasBoletoService`, `Asaas*BillingHandler`, `AsaasPendingPaymentCleanupService`, `TenantAsaasChargeService`, `TenantDepositPaymentService`, `AsaasDtos` |
| Assistente (conversa) | `AssistantApiClient` (chama o `api_assistant`: `/message`, `/admin/cache`, `/admin/seed-reminder`), `AssistantCacheInvalidationService` |
| WhatsApp / Meta | `WhatsAppClient`, `MetaEmbeddedSignupClient`, `MetaEmbeddedSignupGateway`, `WhatsAppAppointmentNotificationService` |
| Telegram | `TelegramBotClient` |
| Consulta de CNPJ e CEP | `BrasilApiCnpjClient`, `CnpjWsClient`, `ViaCepClient` |
| Arquivos | `MinioStorageService` (MinIO; `MINIO_*`) |
| E-mail | `EmailJobService`, `CredentialsEmailService`, `*EmailPayload` (aviso da cliente, verificação, reset de senha) |
| Notificação | `NotificacaoService` |
| Auditoria | `AuditService`, `AuditEventCommand`, `AuditConstants`, `HashesDaAuditoria` |

## Convenções

- **HTTP com `RestClient`** (Spring Framework 7, em ~8 classes), tratando `RestClientResponseException`. Confira que a chamada nova
  tem timeout de conexão e de leitura: uma chamada externa sem timeout trava a thread única dos schedulers (ver `scheduler/CLAUDE.md`).
- **Vários clientes têm um construtor extra de teste** que recebe um `RestClient` pronto. **O construtor principal precisa de
  `@Autowired`**, e é preciso um teste que instancie pelo Spring: esse padrão já derrubou a API em produção (20/10/2026).
- **Payloads vão para `integration_logs` sanitizados** (`util/IntegrationLogSanitizer`); nunca grave token, chave ou dado de
  cartão em log.
- **Falha externa vira exceção própria** (`AsaasException`, `FiscalProviderException`, `CnpjApiIndisponivelException`), mapeada em
  `exception/GlobalExceptionHandler`. Não deixe `RestClientException` vazar até o usuário.
- **Consultas de CNPJ** têm cache (`CnpjLookupCache`) e rate limit na rota pública (`security/PublicCnpjRateLimiter`); há mais de um
  provedor (`BrasilApiCnpjClient`, `CnpjWsClient`) e a falha vira `CnpjApiIndisponivelException`.

## Cuidados

- Webhook de entrada (Asaas, WhatsApp, Telegram) é validado no controller; aqui ficam só as chamadas **de saída**.
- Segredos guardados por salão devem ficar **cifrados** (`security/EncryptionService`; certificado fiscal em `CofreDoCertificado`).
  Confira como o segredo vizinho é gravado antes de criar um novo, e **nunca devolva o valor em DTO**.
- Mudou contrato do `api_assistant`? Ajuste também `api_assistant/.../AssistantController` e a chave `INTERNAL_API_KEY_ASSISTANT`.
- A auditoria é **append-only e encadeada por hash**: use `AuditService`, não grave direto na tabela.
