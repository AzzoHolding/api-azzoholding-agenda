# saude/ — anamnese, ficha de atendimento e área da cliente

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. Planos de origem (raiz do projeto): `PLANO-ANAMNESE-E-FICHA.md` e
`PLANO-AREA-DA-CLIENTE.md`. **Aqui há dado de saúde: dado sensível pela LGPD.** Trate cada mudança como mudança de segurança.

## O que tem

| Assunto | Classes |
|---|---|
| Entrada e regras | `SaudeController`, `AcessoDeSaude`, `Saude` |
| Anamnese e ficha | `AnamneseDaCliente`, `FichaDeAtendimento`, `FotosDaFicha`, `ExamesDaCliente`, `LinhaDoTempoDaCliente`, `ModelosDeSaude`, `ModelosProntos`, `RelatoriosDeSaude` |
| Arquivos | `ArquivosProtegidos` |
| LGPD | `LgpdDeSaude` |
| Área da cliente (`/minha-area`) | `AreaDaClienteController`, `AreaDaCliente`, `ContasDaCliente`, `LinksDaCliente`, `AgendaDaCliente`, `AssinaturasDaCliente`, `PainelDaCliente`, `ChatDaArea` |

## Quem vê o quê (`AcessoDeSaude`)

- **Dono:** tudo.
- **Profissional:** anamnese e fotos **só de clientes que ela atende** (tem agendamento com ela) e conforme as opções do perfil; ficha só
  dos atendimentos dela, ou de outras profissionais se tiver "ver fichas de outras".
- **Equipe sem a opção de saúde (recepção):** só pendências, nunca o conteúdo.
- Alertas de segurança chegam também à profissional que atende, mesmo sem a opção de saúde.
- **Toda leitura de dado de saúde ou foto é auditada** (sem gravar o conteúdo). Não adicione leitura sem passar por essa trilha.

## Arquivos e fotos

Fotos e imagens de assinatura ficam **cifradas (AES-GCM) no Postgres**, não no MinIO (decisão para não depender dele). A imagem é
decodificada e regravada: sai **sem EXIF** (inclusive localização GPS) e no tamanho certo. Não troque isso por upload cru.

## Área da cliente

Tudo parte da **conta da cliente na sessão**; cada salão só entra pelo vínculo ativo. A cliente vê os dados de todos os salões
dela; um salão continua vendo só o próprio cadastro. **Decisão do dono (19/11/2026):** a cliente **não** vê a observação interna da
profissional (ficha e seções), as notas internas (adendo do tipo `NOTA`) nem os alertas manuais do salão; ficha em rascunho
também não aparece. O app Flutter (`app/azzo-agenda-cliente`) usa estas rotas, com sessão no corpo e `X-Cliente-App: 1`.

## Cuidados

- Mudou rota ou DTO da área? Atualize o app da cliente **e** a web (`azzo-web-app`, `/minha-area`). Textos finais dos termos da área
  ainda são rascunho (pendência do dono).
- Nenhum dado de saúde em log, e-mail ou mensagem de WhatsApp. Use `util/LogSanitizer`.
- Exclusão e anonimização de cliente precisam cobrir esta pasta (`LgpdDeSaude`).
