# exception/ — erros e resposta padrão

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta.

## O que é

Um ponto só transforma exceção em resposta HTTP: `GlobalExceptionHandler` (`@RestControllerAdvice`). O corpo de erro é sempre
`ErrorResponse`: `code`, `message`, `details`, `path`, `timestamp`, `traceId`.

## Exceções tratadas por nome

`ConstraintViolationException` e `MethodArgumentNotValidException` (validação), `HttpMessageNotReadableException` (corpo inválido),
parâmetro ausente ou de tipo errado, `NoResourceFoundException` (404), `AccessDeniedException` (403), e as de domínio:
`AppointmentConflictException` (horário ocupado), `AsaasException`, `FiscalProviderException`, `FiscalStateTransitionException`,
`NfseProvedorRecusouException`, `NfseProvedorSemRespostaException`, `NfseStateTransitionException`, `CnpjApiIndisponivelException`,
`CnpjNaoEncontradoException`, `ApiClientErrorException`.

## Regras

- **Lance a exceção de domínio e deixe o handler responder.** Não monte `ResponseEntity` de erro nos controllers, para o formato e o
  `traceId` ficarem iguais em todo lugar (o front lê o `code`).
- **Não vaze detalhe interno:** mensagem para o usuário sem stack, SQL ou nome de tabela. O detalhe vai para o log (com `traceId`).
- O `code` é parte do contrato com o front: **não renomeie** um `code` existente sem avisar os consumidores.
- Exceção nova de integração ou de domínio precisa ser registrada no handler, senão vira 500 genérico.
- `ContextoTenant` lança `IllegalStateException` se faltar tenant: é de propósito (falha explícita, **sem default silencioso**). O handler
  trata `IllegalArgumentException` e `IllegalStateException` como erro esperado, e não como "inesperado"; não "conserte" isso com um tenant padrão.
