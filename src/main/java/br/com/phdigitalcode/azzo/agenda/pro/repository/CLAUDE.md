# repository/ e specification/ — acesso a dados

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta (e `specification/`).

## O que é

~150 repositórios Spring Data JPA (um por entidade, mesmo nome com sufixo `Repository`), com ~260 `@Query`, dos quais ~66
**nativas**. Filtros dinâmicos ficam em `specification/` (`EstoqueSpecifications`, `NotificationSpecifications`,
`TenantSpecialClosureDateSpecifications`, `WhatsAppBookingReactivationCycleSpecifications`). Consultas grandes de leitura vivem em
repositórios de consulta à parte, por exemplo `AgendamentoQueryRepository`.

## Regras

1. **Toda consulta de negócio filtra por tenant.** Esqueceu o filtro, vazou dado entre salões. Revise o `WHERE` de toda `@Query` nova.
2. **Consulta nativa:** o `default_schema: azzo_app` vale só para o JPA. Para SQL cru, confirme que o schema está resolvido.
   E lembre que a API roda como o papel `azzo_api`, que **só lê e grava**: `@Query` nativa não pode fazer DDL, `TRUNCATE` nem
   desligar gatilho. Nova tabela precisa de migration (rodada pelo dono do banco) e herda o direito por `ALTER DEFAULT PRIVILEGES`.
3. **Tabelas append-only:** `audit_events` tem gatilho que **proíbe** UPDATE/DELETE. Não escreva consulta que altere ali.
4. **Concorrência:** onde vários processos disputam a mesma linha (agendamento, selagem da auditoria) já há `FOR UPDATE SKIP LOCKED`
   (`AgendamentoRepository`, `AuditEventRepository`). Copie o padrão; não invente trava nova.
5. **Fuso:** o banco e o JDBC trabalham em UTC (`jdbc.time_zone: UTC`). Converta para o fuso do salão na camada de serviço.
6. **Lote de leitura:** `default_batch_fetch_size: 64` está ligado; ainda assim cuidado com N+1 em listagens grandes.

## Cuidados

- Mudou uma consulta que escreve em tabela existente? **Leia o banco de produção** (gatilhos, `CHECK`, índices) antes de mesclar:
  a V141 passou no CI e derrubou a API por ~15 min (02/10/2026).
- Teste de repositório real é `*IntegrationTest` (Testcontainers com Postgres); teste com repositório mockado **não** pega erro de SQL.
