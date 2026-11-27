# src/test — testes do api_agenda

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só os testes.

## Como está organizado

~280 arquivos, espelhando `src/main` (`controller`, `service`, `repository`, `security`, `scheduler`, `saude`, `integration`, `util`, …).

| Tipo | Nome | Roda no CI? | Precisa de |
|---|---|---|---|
| Unidade | `*Test` | Sim (`./mvnw -B clean verify -Dtest='!*IntegrationTest'`) | nada |
| Integração | `*IntegrationTest` (~12) | Sim, em etapa separada (`./mvnw -B test -Dtest='*IntegrationTest'`) | **Docker** (Testcontainers com Postgres) |

Teste vermelho em qualquer etapa **impede a publicação da imagem**.

## Regras e armadilhas

- **Não há `@WebMvcTest`** neste projeto; poucos testes usam `@SpringBootTest`/`MockMvc`. Copie o padrão do teste vizinho.
- **Suíte verde não prova que a API sobe.** Quase nenhum teste carrega o contexto do Spring. Em 20/10/2026 a API entrou em laço de
  reinício em produção (bean com dois construtores sem `@Autowired`) com **2.437 testes passando**. Para bean novo, escreva um teste que
  o instancie pelo Spring (`AnnotationConfigApplicationContext`). Depois do deploy, confira a subida no servidor.
- **Repositório mockado não pega erro de SQL nem gatilho.** Para o que escreve em tabela, use `*IntegrationTest` (Postgres real).
- Rodar rápido: `./mvnw -o test`; uma classe: `./mvnw -o test -Dtest=NomeDoTeste`.
- Sem Docker local os `*IntegrationTest` não rodam; não os marque como "passou" nesse caso, diga que não rodaram.
- Dado de teste nunca usa documento, telefone ou e-mail reais de cliente.
