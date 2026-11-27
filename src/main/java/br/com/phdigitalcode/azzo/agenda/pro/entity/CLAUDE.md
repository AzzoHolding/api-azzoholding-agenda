# entity/ — modelo JPA

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta.

## O que é

~130 entidades JPA (Lombok `@Getter`/`@Setter`). **O banco é a fonte da verdade e o Hibernate não cria nada** (`ddl-auto: none`):
mudou campo, tabela ou coluna aqui, precisa de **migration** em `src/main/resources/db/migration/` (ver `db/CLAUDE.md`).

## Convenções

- **Nomes das classes misturam português e inglês** (`Agendamento`, `Cliente`, `Comanda`, `Cupom` ao lado de `CheckoutOrder`,
  `CommissionRule`, `EmailJob`). **O nome da tabela é em inglês** e diferente da classe (`Agendamento` → `appointments`). Sempre leia
  o `@Table(name = "...")`; "nome de tabela chutado" já causou bug.
- **Quase todas têm `tenant_id`** (mais de cem classes). É a chave de isolamento entre salões: nunca exponha entidade sem filtrar por ele.
- `AuditEvent` e as tabelas de auditoria são **append-only** (gatilho no banco) e encadeadas por hash; não há atualização de linha.
- Entidade **não é DTO.** Não devolva entidade em controller; use `dto/` (evita vazar campo interno e quebra por lazy loading, já que
  `open-in-view` está desligado).

## Famílias (para achar a entidade)

Agenda (`Agendamento*`, `AppointmentDeposit`, `AppointmentCustomerNote`), cliente e fidelidade (`Cliente`, `ClientMembership*`,
`ClientPackage*`, `ConsumoDePontos`), caixa (`Comanda*`, `Commission*`, `Cobranca`, `ContaDePagamento`), vendas especiais (`Cupom`, vale,
pacotes), estoque (`Estoque*`), chat (`ChatConversationEntity`, `ChatMessageEntity`), fiscal/NFS-e, auditoria (`Audit*`), e-mail
(`EmailJob`, `EmailTemplateConfig`, `EmailVerificationToken`), checkout (`CheckoutIntent`, `CheckoutOrder`).

## Cuidados

- Campo novo `NOT NULL` sem `DEFAULT` quebra a migration em tabela com dados. Use `DEFAULT` ou preencha em etapas.
- Renomear coluna exige migration **e** ajuste das `@Query` nativas do `repository/` (elas não acusam erro em tempo de compilação).
