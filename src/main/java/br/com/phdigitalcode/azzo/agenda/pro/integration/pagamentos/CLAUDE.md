# integration/pagamentos/ — pagamento integrado (operadoras)

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Plano completo: `PLANO-PAGAMENTOS.md` na raiz do projeto.

> **Em produção este módulo está OCULTO para todos** até haver um salão piloto (`PAGAMENTOS_SALOES_PILOTO` no `environment`
> do `api` em `compose.yml` + valor no `.env`; o compose não usa `env_file`). A tela
> `/configuracoes/integracoes/pagamentos` está escondida por decisão do dono (02/11/2026). **Não apague código nem rotas.**
> Ao mexer aqui, lembre o dono de que está oculto e pergunte se é hora de reativar.

## Modelo

`ProvedorDePagamento` é a interface de uma operadora. O **dinheiro vai para a conta do próprio salão** na operadora: o Azzo
cria a cobrança com as credenciais dele, recebe o aviso (webhook) e concilia. **Nenhum dado de cartão passa pelo Azzo**
(maquininha, checkout ou tokenização da operadora).

Cada operadora implementa só o que oferece e declara em `capacidades()`; o resto lança `NaoSuportado` (mesmo desenho dos
provedores da NFS-e, `NfseProviderAdapter`). Contrato: `codigo()`, `nome()`, `capacidades()`, `camposDeCredencial()` (o que a tela
pede para conectar), `validarCredenciais()` (sem cobrar), `criarCobranca()`, `consultar()` (reserva para o webhook perdido) e,
por padrão `NaoSuportado`, `cancelar()` e `estornar()`.

## Provedores

| Pasta | Provedor | Observação |
|---|---|---|
| `asaas/` | Asaas | O mesmo Asaas cobra a assinatura do salão (ver `integration/`) |
| `cielo/` | Cielo e Cielo Smart (maquininha) | Faltam credenciais de teste do dono; Cielo LIO antiga sem integração |
| `mercadopago/`, `pagarme/`, `pagbank/`, `rede/` | operadoras | Cada uma com seu `*Provedor` |
| `pixchave/` | Pix na chave do próprio salão (`BrCodeDoPix`, `PixNaChaveProvedor`) | Sem operadora: gera o BR Code; quem dá "Recebi" é opção do perfil |
| `tef/` | TEF (conector local) | Adiado; `ConectorTefController` + `VendasTefSemRespostaScheduler` |

Getnet consta no contrato do `ProvedorDePagamento` mas **não tem implementação**. Contas conectadas ficam em
`ServicoDeContasDePagamento`; taxas em `ServicoDeTaxasDePagamento`/`CalculoDaTaxaDePagamento`; liberação por salão em
`LiberacaoDePagamentos`.

## Cuidados

- **Idempotência:** o webhook pode chegar duas vezes ou nunca. Sempre faça `consultar()` como reserva e nunca baixe um
  pagamento duas vezes.
- **Credenciais cifradas** (`security/EncryptionService`); a tela nunca recebe o valor de volta.
- **Testar só em sandbox** (a Cielo tem; o dono ainda precisa providenciar as credenciais de teste, ver `LEIA-PRIMEIRO.md`);
  cobrança real só com o dono. O dono digita as credenciais na tela local, nunca no chat.
- Operadora nova = classe nova que implementa a interface + registro + teste. Não ponha regra de operadora fora desta pasta.
