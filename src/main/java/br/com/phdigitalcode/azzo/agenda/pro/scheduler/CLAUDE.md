# scheduler/ — tarefas agendadas

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta.

## O que é

~29 tarefas `@Scheduled` que mantêm o sistema andando sem ação do usuário. O scheduler só **dispara**; a regra fica em
`service/` (muitos `*Service`/`*Worker`).

## Convenções

- **`fixedDelay` com atraso inicial** (ex.: `PT5M` e `initialDelay PT30S`), de propósito: equivale ao `concurrentExecution = SKIP`
  do Quarkus original, ou seja, a próxima execução só é marcada **depois** que a anterior termina. Não troque por `fixedRate`
  nem por `cron` sem pensar em sobreposição.
- **Não há ShedLock nem lock distribuído.** Hoje há **uma só instância** da API em produção, então cada tarefa roda uma vez.
  **Com duas réplicas, todas as tarefas rodariam em dobro** (lembrete duplicado, cobrança duplicada, NFS-e duplicada). Antes de
  escalar, é preciso trava por tarefa. Algumas consultas já usam `SKIP LOCKED` (`AgendamentoRepository`,
  `AuditEventRepository`) e `SeladorDaAuditoria`/`ServicoAgendamentos` também têm trava própria.
- **Uma thread só para todas as tarefas.** Nenhum `application*.yml` configura `spring.task.scheduling`, então vale o padrão
  do Spring Boot (pool de 1 thread, não verificado em runtime): as ~29 tarefas rodam **em fila**, e uma lenta atrasa as demais
  (a atualização das views de relatório leva 1–5 s). Tarefa nova não pode ser um loop longo nem fazer chamada externa sem
  timeout. Se algum dia as tarefas atrasarem, o primeiro ajuste é `spring.task.scheduling.pool.size`.
  (O envio de e-mail e a importação têm executores próprios, configurados em `application.yml`.)
- Toda tarefa precisa ser **idempotente** e tolerar falha no meio: ela roda de novo no próximo ciclo.

## Tarefas por assunto

| Assunto | Tarefas |
|---|---|
| Agenda | `ReminderScheduler` (dois disparos), `AppointmentNoShowScheduler`, `AppointmentDepositExpirationScheduler`, `CheckoutIntentExpirationScheduler` |
| Mensageria | `WhatsAppBookingReactivationScheduler`, `WhatsAppTemplateStatusScheduler`, `ChatMessageRetentionScheduler`, `EmailJobScheduler` |
| Fiscal | `FiscalDanfeJobScheduler`, `NfsePdfJobScheduler`, `NfseStatusPollingScheduler`, `NfseOperationalAlertScheduler` |
| Cobrança e assinatura | `ConsultaDeCobrancasScheduler`, `ConsultaDeAssinaturasNoCartaoScheduler`, `ClientMembershipCancellationScheduler`, `LicenseExpirationNotificationScheduler`, `VendasTefSemRespostaScheduler`, `LiberacaoDeDocumentoDeTrialScheduler` |
| Fidelidade e vales | `ExpiracaoDePontosScheduler`, `ExpiracaoDeValesScheduler`, `RecurringTransactionScheduler` |
| Importação | `ProcessadorImportacaoClientesScheduler`, `ProcessadorImportacaoEstoqueScheduler` |
| LGPD e auditoria | `LgpdRetentionScheduler`, `AuditRetentionPurgeScheduler`, `SeladorDaAuditoriaScheduler`, `ConferenciaDaAuditoriaScheduler` |
| Relatórios | `ReportMaterializedViewRefreshScheduler` (atualiza as views por uma função criada na V165) |

## Cuidados

- Tarefa nova: registre aqui, dê um atraso inicial diferente das vizinhas (para não subirem todas juntas) e escreva teste
  em `src/test/.../scheduler/`.
- Tarefa que envia algo ao cliente (WhatsApp, e-mail) deve registrar o envio **antes de repetir** para não mandar duas vezes.
- Mudou o intervalo? Procure se outro processo depende dele (ex.: polling de NFS-e e alerta operacional).
