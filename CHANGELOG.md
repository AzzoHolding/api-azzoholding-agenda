# Notas de versão

Cada versão estável (tag `X.Y.Z` em `master`) e o que mudou desde a anterior.

## 1.0.0 — 07/11/2026

Mudanças desde a versão estável anterior (24/10/2026). 50 commits.

### Novidades
- **financeiro:** busca por descricao e contagem por situacao da conciliacao (`793f01f`)
- **financeiro:** situacao da conciliacao com divergencia e aviso de duplicidade (`5982496`)
- **financeiro:** resumo informa se quem pede pode gerenciar (`73914ab`)
- transferencia com baixa real e cancelamento de comanda com decisao do valor pago (`0ecbf13`)
- **auth:** sessao devolve a URL do logo do salao para o topo do menu (`9ad2fff`)
- **agenda:** perfil decide quem agenda para outros profissionais; notificacoes por escopo (`8344f10`)
- **agenda:** opcoes separadas para agendar e para editar ou cancelar de outros (`80e5720`)
- **clientes:** profissional ve so nome e telefone; notificacoes pelo agendamento dele (`2a5e72c`)
- **pdv:** opcoes do perfil para comandas de todos e para lancar e dar desconto (`d39cc10`)
- **pdv:** cancelar comanda com a opcao de operar todas (`6ab901a`)
- **financeiro:** conciliacao por dia ou por mes, conforme o modo do salao (`d7d4f4a`)
- **financeiro:** conciliacao do mes com intervalo (`48c886e`)
- **financeiro:** lancamentos ligados a conciliacao do periodo e historico paginado (`29bc03b`)
- **nfse:** DPS no leiaute 1.01 da NT 009, conferida no XSD oficial, e IBS/CBS (`2989503`)
- **fiscal:** certificado A1 trancado por chave que so existe com a senha (`ea47c5f`)
- **fiscal:** apuracao mensal pelas NFS-e e cancelamento do Emissor Nacional (`272f48e`)
- **nfse:** assistente de configuracao (dados pelo CNPJ e conferencia) (`12081d4`)
- **nfse:** DANFSe v2.0 da NT 008/2026 e PDF gerado logo apos o pedido (`c81954a`)
- **whatsapp:** coexistencia no Embedded Signup - numero do app Business nao e registrado de novo e pede contatos e historico (`1dfbf88`)

### Correções
- **financeiro:** conciliacao com estado explicito e conciliado sem edicao (`5468d9a`)
- **auditoria:** detalhe do evento devolve os dados, e nao o JsonNode como bean (`83fa887`)
- **api:** corpo ausente ou JSON malformado responde 400, e nao 500 (`93b4a58`)
- **agenda:** excluir agendamento sem passar pela colecao de itens (`611657c`)
- exportacoes CSV que nunca terminavam e relatorio de clientes inativos (`8375cf0`)
- **chat:** limpeza so apaga o texto do que foi lido e tem mais de 2 meses (`de5cdf0`)
- **relatorios:** conta de data de clientes inativos que o banco recusava (`fe52f34`)
- **menu:** opcao de agendar para outros logo depois de Agenda (`9594b19`)
- **nfse:** emissao pelo Emissor Nacional no formato da API e falhas gravadas (`f9f617b`)
- **nfse:** provedor so entre os que o sistema sabe chamar (`cd89396`)
- **nfse:** rascunho herda o codigo nacional e o NBS da configuracao (`ce278aa`)
- **nfse:** falar com a Receita e com a prefeitura em HTTP/1.1 (`9262384`)
- **nfse:** declaracao UTF-8 no XML enviado a Sefin (`7f14d6d`)
- **nfse:** assinatura do leiaute nacional sem prefixo de namespace (`4901c4e`)
- **nfse:** PDF tambem para nota com cancelamento pedido ou recusado (`0e63665`)
- **nfse:** rascunho herda o codigo municipal do servico (cTribMun) (`b29e766`)
- **agenda:** historico sem codigo cru e dia passado sem horario livre (`73ef847`)
- **ci:** imagem sem atestacao e limpeza que nao apaga partes recentes sem tag (a tag develop ficava sem manifesto) (`930301d`)

### Manutenção
- chore: **whatsapp:** Graph API padrao v25.0 (v18.0 de 2023), igual ao gerenciamento (`cf1b1e0`)

### Outros
- Agenda: chave so para teste de carga que ignora a trava do envio (`0e2fc02`)
- Webhook: resposta a midia e botao tocado vai ao assistente (`3f7fd8e`)
- Expediente do salao pela tabela que a tela grava (assistente e perfil) (`3da7948`)
- Horarios (colunas time) sem o deslocamento de +3h do fuso (`cca62d7`)
- Inconsistencias do teste de telas (25/10) (`88aef44`)
- Inconsistencias do teste de telas, parte 2 (25/10) (`96a0eab`)
- Remove logs de queda da JVM entrados por engano e ignora os proximos (`e5b9456`)
- Troca de senha: mensagens de erro sem a palavra que o handler esconde (`28ed981`)
- Codigos de erro do fiscal sem acento: casam com o mapa de mensagens (`ff1f77b`)
- NFS-e: o backend calcula o que a tela nao mandou (linha, totais e ISS) (`670ce59`)
- Inconsistencias pendentes do teste de telas (26/10) (`c6d6e3c`)
- Expurgo de auditoria: leitura da config e transacao por tenant (`f4d527c`)
