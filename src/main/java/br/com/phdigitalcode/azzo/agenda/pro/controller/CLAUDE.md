# controller/ — camada REST

Contexto geral da API: `../../../../../../../../../../CLAUDE.md` (raiz do `api_agenda`). Aqui, só esta pasta.

## O que é

Os endpoints HTTP (~57 controllers, prefixo `/api/v1/...`). **Controller só traduz HTTP**: valida entrada, resolve o
tenant, chama um `service/` e devolve DTO. Regra de negócio fica em `service/`.

## Convenções que o código segue

- **Papel + permissão:** `@PreAuthorize("hasAnyRole('OWNER','PROFESSIONAL','STAFF')")` no controller **e**
  `@RequiresPermission("pos:coupons")` para a permissão fina (AOP em `security/RequiresPermissionAspect`). Rota nova sem
  os dois fica aberta demais ou fechada demais; confira nos controllers vizinhos.
- **Tenant:** `ContextoTenant.obterTenantIdOuFalhar()` (de `security/`). Nunca receba `tenantId` do cliente.
- **Validação:** `@Valid` no corpo; erros viram `ErrorResponse` pelo `exception/GlobalExceptionHandler`
  (`code`, `message`, `details`, `path`, `timestamp`, `traceId`). Lance exceção; não monte `ResponseEntity` de erro à mão.
- **Campo desconhecido no corpo é ignorado**, mas deixa rastro no log (`config/CamposDesconhecidosConfig`). Se o front
  mandar o nome errado, a chamada volta 200 e nada muda: olhe o log.
- **Fora de `/api`:** webhooks entram por `/webhook/*` (WhatsApp, Telegram, Asaas, pagamentos). O Caddy manda esse caminho
  direto para a API; essas rotas não têm JWT, quem as protege é o próprio controller (token/assinatura).
- **Rotas internas:** `/api/v1/internal/**` usam `X-Internal-Api-Key` por serviço (`security/InternalApiKeyFilter`), não JWT.

## Onde está cada assunto

| Assunto | Controllers |
|---|---|
| Sessão e conta | `Auth`, `Acessos`, `Onboarding`, `Settings`, `MenuConfig` |
| Agenda | `Agendamentos`, `SpecialClosure`, `Servicos`, `Profissionais`, `Specialties`, `PublicSalons` |
| Clientes e saúde | `Clientes`, `LgpdTitular`, `ReactivationConfig`, e `saude/SaudeController` + `AreaDaClienteController` |
| Caixa e financeiro | `Comanda`, `FechamentoCaixa`, `Financeiro`, `Commission`, `TaxasDePagamento`, `Dashboard`, `Relatorios`, `RankingDeProfissionais` |
| Vendas especiais | `Cupons`, `Vales`, `Packages`, `MembershipPlans` |
| Pagamentos e cobrança | `Pagamentos`, `ConectorTef`, `WebhookDePagamento`, `TenantPaymentWebhook`, `Billing`, `Checkout` |
| Fiscal | `Fiscal`, `Nfse`, `NfseReceita`, `AssistenteDaNfse` |
| Estoque | `Estoque` |
| Chat e mensageria | `Chat` (inclui o SSE `/chat/stream`), `WhatsApp`, `WhatsAppWebhook`, `Telegram`, `TelegramWebhook`, `Webhook`, `MetaPlatformPublic` |
| Plataforma e admin | `SystemAdmin`, `InternalPlans`, `AssistantInternal`, `TenantToggle`, `Auditoria`, `Notification`, `Suggestion` |
| Auxiliares | `Address`, `CnpjConsulta`, `PublicCnpj`, `PublicLegalDocuments`, `StorageProxy` |

## Cuidados

- `/api/v1/chat/stream` é SSE e **não pode ser comprimido** pelo proxy; não adicione filtro que bufferize a resposta.
- `StorageProxyController` serve arquivos do MinIO pela API (latência alta, ~700 ms de média medida em 25/11/2026).
- Mudou um DTO ou uma rota que o front consome? Atualize também `app/azzo-agenda/frontend/azzo-design-system/packages/api-types`
  e as zonas `azzo-web-*`.
- Controller novo precisa de teste em `src/test/.../controller/`. O projeto **não usa `@WebMvcTest`** (0 ocorrências): a maioria é
  teste de unidade da classe, e só uns poucos usam `@SpringBootTest`/`MockMvc`. Copie o padrão do teste vizinho.
