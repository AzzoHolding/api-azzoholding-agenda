# service/ — regra de negócio

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta.

## O que é

Onde mora a regra de negócio (~180 classes). Os nomes misturam português (`Servico*`) e inglês (`*Service`) porque o
código veio do Quarkus e foi evoluindo; **siga o padrão da família da classe que você está mexendo**, não renomeie.

## Convenções

- **Transação:** ~590 usos de `@Transactional`. Serviço grava; controller não abre transação.
- **Depois do commit:** `AfterCommitExecutor` roda uma tarefa **depois** do commit da transação atual, numa thread de fundo
  (se não houver transação, roda na hora, também em fundo). Use para efeito externo (WhatsApp, e-mail, webhook) que não
  pode sair se a transação desfizer.
- **Tenant:** todo método de negócio recebe ou resolve o tenant; consulta sem filtro de tenant vaza dados entre salões.
- **Máquinas de estado e idempotência** no fiscal (`FiscalDocumentStateMachine`, `NfseFiscalStateMachine`,
  `FiscalIdempotencyService`, `NfseIdempotencyService`): não pule a transição nem repita a emissão fora delas.
- **Workers assíncronos** (`FiscalJobWorker`, `NfsePdfJobWorker`, `NfseStatusPollingWorker`, `NotificationPersistenceWorker`)
  são chamados por `scheduler/`. Eles precisam ser seguros para reexecução.

## Famílias de serviço (para achar o que procura)

| Família | Serviços |
|---|---|
| Agenda | `ServicoAgendamentos`, `AppointmentService`, `AppointmentSettingsService`, `ServicoPublicBooking`, `ServicoConfirmacaoDeAgendamento`, `AppointmentDepositMaintenanceService`, `AppointmentNoShowProcessingService`, `ReminderProcessingService` |
| Clientes | `ClienteService`, `ServicoImportacaoClientes`, `ProcessadorImportacaoClientesService`, `ClienteImportFileParser` |
| LGPD | `ServicoLgpdTitular`, `ServicoAnonimizacaoTitular`, `ServicoExportacaoTitular`, `ServicoOptOutDoTitular`, `RastrosDoTitular`, `LgpdRetentionService`, `ServicoEliminacaoFimDeContrato`, `RetentionService` |
| Caixa e financeiro | `ServicoComanda`, `ServicoFechamentoCaixa`, `ServicoFinanceiro`, `CommissionService`, `ServicoDeTaxasDePagamento`, `CalculoDaTaxaDePagamento`, `ServicoDashboard`, `ServicoRelatorios`, `ServicoCatalogoRelatorios`, `ServicoRankingDeProfissionais`, `CalculoDoRanking` |
| Vendas especiais e fidelidade | `ServicoDeCupons`, `ServicoDeVales`, `ServicoPackages`, `ServicoClientMemberships`, `ServicoMembershipPlans`, `ServicoDePontos` |
| Pagamentos e cobrança | `ServicoDePagamentos`, `ServicoDeContasDePagamento`, `ServicoDoConectorTef`, `ServicoBilling`, `BillingAdminService`, `BillingPaymentHandler`, `CheckoutService`, `ServicoDeAssinaturasNoCartao`, `LiberacaoDePagamentos` |
| Fiscal (NFS-e e NF) | `ServicoFiscal`, `Fiscal*` (XML, assinatura, DANFE, sequência, regras, cálculo de imposto), `Nfse*`, `AbrasfDirectProviderAdapter`, `SefinNacionalProviderAdapter`, `NfseProviderRouterService`, `CofreDoCertificado`, `ServicoDeNotasDaReceita`, `AssistenteDaNfse`, `ApuracaoMensalDaNfse` |
| Estoque | `ServicoEstoque`, `EstoqueMovimentacaoService`, `CalculadoraEstoque`, `ProcessadorImportacaoEstoqueService` |
| Chat e mensageria | `ChatService`, `ChatRealtimePublisher`, `ChatMessageStatusService`, `ChatMessageRetentionService`, `MetaPlatformService`, `MetaSignedRequestService`, `RegistroDeEnvioDoWhatsapp`, `SaudeDoEnvioWhatsApp`, `ReactivationConfigService` |
| Notificação e e-mail | `NotificationService`, `NotificationPublisher`, `NotificationRequestedEvent`, `EmailJob*`, `EmailTemplateRendererService`, `ServicoConfirmacaoDeEmail` |
| Acesso e menu | `AuthService`, `ServicoPerfisDeAcesso`, `MenuService`, `MenuCatalogService`, `MenuOverrideService`, `LicenseStatusService`, `FiscalAccessService` |
| Auditoria | `SeladorDaAuditoria`, `ConferenciaDaAuditoria`, `AuditQueryService`, `AuditRetentionPurgeService`, `AuditoriaFiscal` |
| Plataforma | `ServicoOnboarding`, `ServicoSalonProfile`, `ServicoLiberacaoDeDocumentoDeTrial`, `AddressService`, `CnpjConsultaService`, `CnpjLookupCache` |

## Cuidados

- **Dois construtores num bean** (por exemplo, um atalho de teste): o principal leva `@Autowired`, e é preciso um teste que
  instancie pelo Spring. Já derrubou a API em produção por 8 min (20/10/2026).
- Efeitos colaterais externos (Asaas, Meta, SEFAZ/NFS-e) ficam atrás de `integration/`; não chame HTTP direto daqui.
- **Auditoria é append-only e encadeada por hash:** não tente "corrigir" evento gravado; grave outro.
- Pontos de pagamento integrado estão **ocultos em produção** (`PAGAMENTOS_SALOES_PILOTO`); mantenha o código funcionando.
