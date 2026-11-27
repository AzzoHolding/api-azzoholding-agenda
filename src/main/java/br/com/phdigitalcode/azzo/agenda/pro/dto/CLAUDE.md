# dto/ e mapper/ — contratos de entrada e saída

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Aqui, só esta pasta.

## O que é

Os objetos que cruzam a fronteira HTTP (~108 classes). **Tudo que o front lê ou envia passa por aqui**, então mudar um DTO é
mudar um contrato com outros projetos.

## Quem consome

- As zonas Next.js `app/azzo-agenda/frontend/azzo-web-*`, pelo pacote `@azzo/api-types` (em
  `azzo-design-system/packages/api-types`) e pelo `@azzo/api-client`.
- O app da cliente (`app/azzo-agenda-cliente`), nas rotas da área da cliente.
- O `api_assistant` e o `api_gerenciamento`, nas rotas `/api/v1/internal/**`.

Mudou nome, tipo ou obrigatoriedade de campo? **Atualize os consumidores no mesmo lote**, ou o front quebra só em produção.

## Regras

- **Campo desconhecido no corpo é ignorado e só vai para o log** (`config/CamposDesconhecidosConfig`): um nome errado devolve 200 e
  não muda nada. Teste o contrato de ponta a ponta, não só o status.
- Validação com Bean Validation (`@Valid` nos controllers; erros viram `ErrorResponse` com `code`, `message`, `details`).
- Nunca devolva **entidade** nem campo interno (hash, tenant de outro salão, segredo cifrado). DTO de saída é lista branca.
- Dado pessoal: devolva só o necessário; telefone passa por `util/TelefoneBr` (aceita 10 ou 11 dígitos, com ou sem o 55).
- Datas: o banco é UTC; o DTO deve deixar claro o fuso (instante ISO ou data local do salão).
- Há **`mapper/`** só com um mapper; a maior parte da conversão é feita no serviço.
