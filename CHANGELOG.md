# Notas de versão

Cada versão estável (tag `X.Y.Z` em `master`) e o que mudou desde a anterior.

## 1.1.0 — 22/11/2026

Mudanças desde a versão estável anterior (07/11/2026). 53 commits.

### Novidades
- **nfse:** notas na Receita - distribuicao do ADN por NSU, leitura do XML, apuracao e exportacao com as notas emitidas fora do Azzo (V163) (`1b3dd35`)
- **nfse:** notas na Receita mostram quando e por que a nota foi cancelada (do evento) (`da328ed`)
- **nfse:** detalhe do cancelamento (tipo, codigo, processamento, autor, evento, nota substituta) nas notas da Receita (`e16c6b9`)
- **auditoria:** trilha solida - hash completo, cadeia selada, conferencia e fiscal na trilha (`7515e51`)
- **equipe:** foto do profissional e do usuario (envio, remocao, URL assinada, auditoria) (`b0e9ed1`)
- **banco:** API pronta para conectar sem superusuario (refresh por funcao, Flyway com o dono, script do papel) (`0c83d89`)
- **relatorios:** ranking dos profissionais com criterios e pontuacao do dono (V166) (`8443458`)
- **pagamentos:** fase 0 - interface de operadoras, contas, cobrancas, aviso generico e consulta de reserva (V167) (`3070d1f`)
- **pagamentos:** fase 1 - comanda cobra pela operadora (Pix, maquininha, link), estorno e cancelamento na operadora (V168) (`20f056b`)
- **pagamentos:** o que o PDV pode oferecer (Pix, link, maquininhas) (`cd940d9`)
- **pagamentos:** QR Code do Pix pendente na comanda (`5c9c0db`)
- **pagamentos:** fase 2 - Stone / Pagar.me (Pix, link, maquininha Stone pelo Stone Connect, estorno parcial) (`e37fc98`)
- **pagamentos:** fase 3 - Cielo (Pix pela API E-commerce, estorno, aviso com cabecalho combinado) (`91ec09a`)
- **pagamentos:** fase 4 - PagBank (Pix pela API de pedidos, estorno, aviso assinado) (`4327760`)
- **pagamentos:** fase 4 - Rede (Pix pela e.Rede com OAuth, devolucao, aviso com cabecalho combinado) (`b0bae39`)
- **pagamentos:** fase 5 - taxas da operadora por conta, liquido e previsao estimados, despesa de taxa ao fechar a comanda, recebiveis (V169) (`5bd935c`)
- **pagamentos:** fase 7 - clube de assinaturas no cartao recorrente (Mercado Pago), aviso e consulta de reserva (V170) (`7ef904c`)
- **clube:** rotas para assinar e listar assinaturas (portadas), auditoria de assinar e cancelar (`5ca0b33`)
- **pagamentos:** disponiveis diz se ha assinatura no cartao (`075de1c`)
- **pagamentos:** Pix na chave do proprio salao (BR Code estatico) com confirmacao pelo caixa (V172) (`b37ef87`)
- **pagamentos:** opcao do perfil para confirmar Pix na chave (V173); rotas do PDV com permissao do PDV (`3c3c1af`)
- **pagamentos:** piloto por salao (PAGAMENTOS_SALOES_PILOTO) (`f7ca7f9`)
- **pagamentos:** maquininha Cielo Smart pela nuvem (Order Manager, V174) (`418bdb6`)
- **email:** todo e-mail automatico sai de no-reply@ (MAIL_FROM_NO_REPLY); MAIL_FROM vira o suporte (`6b85ac5`)
- **clientes:** pontos e pacotes da cliente (/clients/{id}/loyalty e /packages), saldo na resposta (etapa 3) (`29971a9`)
- **pdv:** produto com preco de venda do estoque (V176), lista para o caixa, fidelidade da comanda e desfazer resgate (etapa 4) (`9473fc6`)
- **fidelidade,pacotes:** expiracao dos pontos, estorno com escolha do dono, auditoria, valor da sessao proporcional, validade e exclusao do pacote (V177, etapas 5 e 6) (`751143d`)
- **fidelidade:** pontos devolvidos voltam com o vencimento original ou novo, escolha do dono ou de quem tem a opcao no perfil (V178) (`547e98d`)
- dono pode dar os relatorios de Faltas e Abandono a outro perfil (V179) (`cdeca0c`)
- cupom de desconto, gerador de cupons e vale-presente (API) (`eb96a63`)
- **cupons:** cupom só para alguns serviços (V182) (`46d0eda`)
- **cupons:** o dono escolhe se a comanda aceita mais de um cupom (V183) (`55d8ef7`)
- **cupons:** códigos gerados com os 6 dígitos do salão na frente (V184) (`eaefc49`)
- **vales:** miolo do código do vale pelo hash de um UUID com o documento do salão (`b0908bd`)
- **vales:** código do vale só com o hash (sem os 6 dígitos do salão) (`b82b612`)
- **saude:** anamnese e ficha de atendimento por cliente (V185) (`1569803`)
- **area-da-cliente:** conta da cliente com convite, login por telefone/e-mail/CPF e exames (V186) (`279d60e`)
- **area-da-cliente:** lacunas da revisao (V187) (`71aee31`)
- **area-da-cliente:** saloes da Azzo, agendar, chat com o salao e painel (V188) (`2191ff4`)
- **area-da-cliente:** sessao no corpo para o app (cabecalho X-Cliente-App) (`396296a`)

### Correções
- **nfse:** cliente do ADN com HTTP/2 (ALPN), User-Agent e nova tentativa - producao derrubava o HTTP/1.1 (`aa8ff10`)
- **nfse:** cliente do ADN envia a cadeia completa do certificado A1 (producao derrubava so com o certificado final) (`4931700`)
- **nfse:** emissao pela Sefin envia a cadeia completa do certificado A1 (producao exige) (`1d4f625`)
- **auth:** recusas na trilha em transacao propria, limite na troca de senha, nome antes/depois no perfil e senha limitada a 72 bytes (`842d8ae`)
- **fidelidade:** resgate sem condicao de corrida, historico de pontos e valor arredondado para baixo (V175) (`504e5e3`)
- **pacotes,fidelidade:** validacoes da API e mensagens (relatorio de 14/11, etapa 2) (`d0e0192`)
- **clube:** mensagem diz quantas sessoes restam na assinatura quando o item pede mais (`b14f858`)
- perfil do salao novo sem erro 500 e telefone repetido com mensagem clara (`7a01fba`)
- defeitos medios e baixos da analise de 15/11 e testes de integracao de volta no CI (`2349aef`)
- **saude:** rota pública do link e tela do dono fora de /configuracoes (`4dec016`)

### Manutenção
- docs: **env:** exemplo de SMTP pela Mailtrap (decisao do dono) e variavel do piloto de pagamentos (`53542b8`)

### Outros
- wip(pagamentos): fase 1 - provedor Mercado Pago (Point, Pix, link) e maquininhas (`f8375d5`)
- wip(pagamentos): fase 6 - lado do Azzo do Conector TEF (pareamento, fila, resultado, V171) (`07f3dfe`)

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
