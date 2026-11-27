# db/ — migrations Flyway (schema `azzo_app`)

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Este arquivo fica em `db/` e **não** em `db/migration/` de propósito,
para o Flyway não varrer nada que não seja `.sql`.

## Fatos

- Migrations em `db/migration/`, nome `V<número>__descricao.sql`. Última em 27/11/2026: **V188**. Conferir a maior antes de criar.
- `out-of-order: true` e `baseline-on-migrate: true` (baseline 0). Duas branches com o mesmo número são problema: confira `develop`.
- **Nunca edite uma migration já aplicada**: o checksum quebra e a API não sobe. Corrija com uma migration nova.
- **Quem roda o Flyway:** o dono do banco (`FLYWAY_DB_USERNAME`). **A API roda como `azzo_api`**, sem superusuário, sem posse das
  tabelas: não cria nem altera tabela, não faz `TRUNCATE`, não desliga gatilho. Tabelas, sequências e funções criadas pelo dono
  herdam o direito por `ALTER DEFAULT PRIVILEGES`. Detalhe e script em `scripts/banco/papel-da-api.sql`.
- O `REFRESH` das views materializadas de relatório passa por uma **função criada na V165** (a API não pode dar `REFRESH` direto).
- Timestamps em UTC.

## Antes de mesclar uma migration

1. **Leia o banco de produção, só leitura:** gatilhos (`pg_trigger`), `CHECK`, índices e dependências da tabela.
   `ssh azzo-prod` + `docker exec azzo-postgres psql`, schema `azzo_app`. Em 02/10/2026 a **V141 passou no CI e derrubou a API por
   ~15–20 min** em laço de reinício, porque `audit_events` tem o gatilho `trg_deny_audit_events_mutation` (append-only). Teste com
   repositório mockado e o CI **não** pegam isso.
2. **Aditiva é de baixo risco** (tabela nova, coluna com `DEFAULT`). **`UPDATE`/`DELETE` em dado existente não é**: rode a consulta
   equivalente em `SELECT` na produção e confira quantas linhas muda.
3. Se a migration depende de um Postgres real e você não pôde ensaiá-la, **diga isso explicitamente** ao pedir o merge.
4. Tabela nova precisa de `tenant_id` (isolamento entre salões) e de índice nas colunas filtradas.
5. Combina com a entidade em `entity/` e as `@Query` nativas em `repository/`: renomear coluna quebra SQL nativo sem aviso de compilação.

## Depois do deploy

Confira: imagem nova, contêiner `healthy` e "Started Application" no log. Uma migration que falha deixa a API em laço de reinício;
reverta o merge **antes** de investigar.
