# util/ — utilitários sem estado

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta.

## O que tem

| Assunto | Classes |
|---|---|
| Log seguro | `LogSanitizer`, `IntegrationLogSanitizer` (sanitiza o payload antes de gravar em `integration_logs`), `CorrelatedLogging` (correlaciona log e trace) |
| Documentos | `CnpjValidator`, `DocumentoValidator` |
| Telefone | `TelefoneBr` (DDD + 10 ou 11 dígitos, com ou sem o `55` que o WhatsApp grava) |
| Texto e código | `TextoSemAcento`, `SlugUtil`, `CodigoAleatorio`, `EstoqueTextoUtil`, `CelulaCsv` |
| Datas e números | `DataUtil`, `NumericUtil`, `JsonUtil` |
| Regras compartilhadas | `JornadaDoProfissional`, `IndicadoresDoCliente` |
| SQL de relatório | `ReceitaDeClientesSql`, `ReceitaDeServicosSql` |

## Regras

- **Estáticos e sem dependência de Spring.** Se precisar de estado ou de bean, o lugar é `service/`.
- **Antes de criar um validador novo, procure aqui.** `TelefoneBr` existe porque, até 21/10/2026, a API aceitava qualquer sequência
  (um número de 13 dígitos sem o `55` entrou como válido).
- **Log:** qualquer coisa que possa ter token, cookie, senha, documento ou dado de saúde passa por `LogSanitizer`.
- `CelulaCsv` evita injeção de fórmula em planilha exportada: use-a em toda exportação CSV.
- Mudou um validador? Rode os testes de `src/test/.../util/`; muitos controllers dependem dele.
