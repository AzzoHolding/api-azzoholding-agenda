package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseReceitaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseDocumentoDaReceitaEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseReceitaCursorEntity;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseDocumentoDaReceitaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseReceitaCursorRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Notas na Receita (07/11/2026, pedido do dono): busca no Ambiente de Dados Nacional todas as NFS-e
 * em que o salao e prestador, tomador ou intermediario — inclusive as emitidas fora do Azzo — e os
 * eventos de cancelamento.
 *
 * <p>So roda com a senha do certificado (o A1 fica trancado por ela): e um botao, nao uma rotina
 * noturna. Cada busca continua do maior NSU ja recebido. Cada documento e gravado na sua propria
 * transacao: se a Receita parar no meio, o que veio fica, e a proxima busca segue dali.
 */
@Service
public class ServicoDeNotasDaReceita {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoDeNotasDaReceita.class);
  /** Lotes de ate 50: 40 lotes = 2.000 documentos por clique. O resto vem no proximo. */
  static final int MAX_LOTES_POR_BUSCA = 40;
  private static final long PAUSA_ENTRE_LOTES_MS = 1000;

  private final ContextoTenant contextoTenant;
  private final NfseCertificateUnlockService unlockService;
  private final ClienteDaDistribuicaoAdn adn;
  private final NfseDocumentoDaReceitaRepository documentos;
  private final NfseReceitaCursorRepository cursores;
  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private final EncryptionService encryptionService;
  private final DanfseRenderer danfseRenderer;
  private final TransactionTemplate transacao;

  public ServicoDeNotasDaReceita(
      ContextoTenant contextoTenant,
      NfseCertificateUnlockService unlockService,
      ClienteDaDistribuicaoAdn adn,
      NfseDocumentoDaReceitaRepository documentos,
      NfseReceitaCursorRepository cursores,
      FiscalTaxConfigRepository fiscalTaxConfigRepository,
      EncryptionService encryptionService,
      DanfseRenderer danfseRenderer,
      PlatformTransactionManager transactionManager) {
    this.contextoTenant = contextoTenant;
    this.unlockService = unlockService;
    this.adn = adn;
    this.documentos = documentos;
    this.cursores = cursores;
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
    this.encryptionService = encryptionService;
    this.danfseRenderer = danfseRenderer;
    this.transacao = new TransactionTemplate(transactionManager);
  }

  public NfseReceitaDtos.BuscaResponse buscar(NfseReceitaDtos.BuscaRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String ambiente = ambiente(pedido == null ? null : pedido.ambiente);
    String senha = unlockService.resolvePasswordFromToken(pedido == null ? null : pedido.unlockTokenId);
    if (senha == null || senha.isBlank()) {
      throw new ApiClientErrorException("Digite a senha do certificado para buscar na Receita.", 422);
    }
    String cnpj = cnpjDoSalao(tenantId);
    RestClient cliente = adn.cliente(senha);

    NfseReceitaCursorEntity cursor = cursor(tenantId, ambiente);
    long nsu = cursor.getUltimoNsu();
    NfseReceitaDtos.BuscaResponse resposta = new NfseReceitaDtos.BuscaResponse();
    int tentativasNoLimite = 0;
    boolean chegouAoFim = false;

    for (int lote = 0; lote < MAX_LOTES_POR_BUSCA; lote++) {
      if (lote > 0) pausa(PAUSA_ENTRE_LOTES_MS);
      ClienteDaDistribuicaoAdn.Lote recebido;
      try {
        recebido = adn.buscar(cliente, ambiente, nsu, cnpj);
      } catch (RuntimeException ex) {
        LOG.warn("nfse_receita.busca.falha tenant_id={} ambiente={} nsu={}", tenantId, ambiente, nsu, ex);
        if (resposta.novos == 0 && lote == 0) {
          throw new ApiClientErrorException("Sem resposta da Receita. Tente de novo em alguns minutos.", 502);
        }
        resposta.parcial = true;
        break;
      }
      if ("LIMITE".equals(recebido.rejeicao())) {
        if (++tentativasNoLimite > 3) {
          resposta.parcial = true;
          break;
        }
        pausa(3000L * tentativasNoLimite);
        lote--;
        continue;
      }
      if (recebido.rejeicao() != null) {
        if (lote == 0) {
          throw new ApiClientErrorException("A Receita recusou a consulta: " + recebido.rejeicao(), 422);
        }
        resposta.parcial = true;
        resposta.mensagem = recebido.rejeicao();
        break;
      }
      if (recebido.fim() && recebido.documentos().isEmpty()) {
        chegouAoFim = true;
        break;
      }
      long maior = nsu;
      for (ClienteDaDistribuicaoAdn.Documento doc : recebido.documentos()) {
        maior = Math.max(maior, doc.nsu());
        if (gravar(tenantId, ambiente, cnpj, doc)) {
          resposta.novos++;
          if ("EVENTO".equals(doc.tipoDocumento())) resposta.eventos++;
          else resposta.notas++;
        }
      }
      if (maior <= nsu) {
        chegouAoFim = true;
        break;
      }
      nsu = maior;
      salvarCursor(tenantId, ambiente, nsu);
    }
    if (!chegouAoFim && resposta.mensagem == null) resposta.parcial = true;
    salvarCursor(tenantId, ambiente, nsu);
    resposta.ultimoNsu = nsu;
    if (resposta.mensagem == null) {
      resposta.mensagem = resposta.novos == 0
          ? "Nenhum documento novo na Receita."
          : resposta.notas + " nota(s) e " + resposta.eventos + " evento(s) novos.";
      if (resposta.parcial) resposta.mensagem += " Ainda há mais: busque de novo para continuar.";
    }
    LOG.info(
        "nfse_receita.busca tenant_id={} ambiente={} novos={} ultimo_nsu={} parcial={}",
        tenantId, ambiente, resposta.novos, nsu, resposta.parcial);
    return resposta;
  }

  /** Grava um documento novo (o NSU e unico por salao e ambiente). Devolve falso se ja existia. */
  private boolean gravar(UUID tenantId, String ambiente, String cnpj, ClienteDaDistribuicaoAdn.Documento doc) {
    Boolean gravado = transacao.execute(status -> {
      if (documentos.existsByTenantIdAndAmbienteAndNsu(tenantId, ambiente, doc.nsu())) return false;
      NfseDocumentoDaReceitaEntity entidade = new NfseDocumentoDaReceitaEntity();
      entidade.setTenantId(tenantId);
      entidade.setAmbiente(ambiente);
      entidade.setNsu(doc.nsu());
      entidade.setChaveAcesso(doc.chave());
      entidade.setTipoDocumento(doc.tipoDocumento() == null || doc.tipoDocumento().isBlank() ? "OUTRO" : doc.tipoDocumento());
      entidade.setTipoEvento(doc.tipoEvento());
      try {
        LeitorDoDocumentoDaReceita.preencher(entidade, doc.xml(), cnpj);
      } catch (RuntimeException ex) {
        // XML que nao deu para ler: o documento fica guardado assim mesmo, com o XML.
        LOG.warn("nfse_receita.xml_ilegivel tenant_id={} nsu={}", tenantId, doc.nsu(), ex);
      }
      if (doc.xml() != null) {
        String cifrado = encryptionService.encrypt(doc.xml());
        entidade.setXmlEnc(cifrado == null ? null : cifrado.getBytes(StandardCharsets.UTF_8));
      }
      if ("NFSE".equals(entidade.getTipoDocumento()) && entidade.getChaveAcesso() != null
          && documentos.existeCancelamentoPara(tenantId, ambiente, entidade.getChaveAcesso())) {
        entidade.setCancelada(true);
      }
      documentos.save(entidade);
      if ("EVENTO".equals(entidade.getTipoDocumento()) && entidade.isCancelada() && entidade.getChaveDoEvento() != null) {
        documentos.marcarCancelada(tenantId, ambiente, entidade.getChaveDoEvento());
      }
      return true;
    });
    return Boolean.TRUE.equals(gravado);
  }

  public NfseReceitaDtos.ListaResponse listar(String ambiente, String papel, String de, String ate) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String amb = ambiente(ambiente);
    String filtroPapel = papel == null || papel.isBlank() ? null : papel.trim().toUpperCase(Locale.ROOT);
    List<NfseDocumentoDaReceitaEntity> notas =
        documentos.listarNotas(tenantId, amb, filtroPapel, dataOuNula(de), dataOuNula(ate));
    Set<String> chaves = new HashSet<>();
    for (NfseDocumentoDaReceitaEntity nota : notas) if (nota.getChaveAcesso() != null) chaves.add(nota.getChaveAcesso());
    Set<String> doAzzo = chaves.isEmpty()
        ? Set.of()
        : new HashSet<>(documentos.chavesEmitidasPeloAzzo(tenantId, chaves));

    // O motivo sai do XML do evento de cancelamento (so das canceladas: poucas, e o XML e pequeno).
    java.util.Map<String, LeitorDoDocumentoDaReceita.Cancelamento> cancelamentos = new java.util.HashMap<>();
    Set<String> chavesCanceladas = new HashSet<>();
    for (NfseDocumentoDaReceitaEntity nota : notas) {
      if (nota.isCancelada() && nota.getChaveAcesso() != null) chavesCanceladas.add(nota.getChaveAcesso());
    }
    if (!chavesCanceladas.isEmpty()) {
      for (NfseDocumentoDaReceitaEntity evento : documentos.eventosDeCancelamento(tenantId, amb, chavesCanceladas)) {
        try {
          LeitorDoDocumentoDaReceita.Cancelamento dados = LeitorDoDocumentoDaReceita.cancelamento(decifrar(evento));
          if (dados != null) cancelamentos.putIfAbsent(evento.getChaveDoEvento(), dados);
        } catch (RuntimeException ex) {
          LOG.warn("nfse_receita.evento_ilegivel tenant_id={} evento={}", tenantId, evento.getId(), ex);
        }
      }
    }

    NfseReceitaDtos.ListaResponse resposta = new NfseReceitaDtos.ListaResponse();
    for (NfseDocumentoDaReceitaEntity nota : notas) {
      NfseReceitaDtos.Nota dto = paraDto(nota, doAzzo);
      LeitorDoDocumentoDaReceita.Cancelamento dados = cancelamentos.get(nota.getChaveAcesso());
      if (dados != null) {
        dto.canceladaEm = dados.quando();
        dto.motivoCancelamento = dados.motivo();
        dto.justificativaCancelamento = dados.justificativa();
        dto.codigoMotivoCancelamento = dados.codigoMotivo();
        dto.tipoCancelamento = dados.tipo();
        dto.cancelamentoProcessadoEm = dados.processadoEm();
        dto.cancelamentoAutorDocumento = dados.autor();
        dto.numeroDoEventoCancelamento = dados.numeroDoEvento();
        dto.chaveSubstituta = dados.chaveSubstituta();
      }
      resposta.itens.add(dto);
    }
    cursores.findById(new NfseReceitaCursorEntity.Chave(tenantId, amb)).ifPresent(cursor -> {
      resposta.ultimoNsu = cursor.getUltimoNsu();
      resposta.ultimaBuscaEm = cursor.getUltimaBuscaEm() == null ? null : cursor.getUltimaBuscaEm().toString();
    });
    return resposta;
  }

  public String xml(String id) {
    NfseDocumentoDaReceitaEntity doc = obter(id);
    String xml = decifrar(doc);
    if (xml == null) throw new ApiClientErrorException("O XML desta nota não está disponível.", 404);
    return xml;
  }

  public byte[] danfse(String id) {
    NfseDocumentoDaReceitaEntity doc = obter(id);
    if (!"NFSE".equals(doc.getTipoDocumento())) {
      throw new ApiClientErrorException("Só NFS-e tem DANFSe.", 422);
    }
    String xml = decifrar(doc);
    if (xml == null) throw new ApiClientErrorException("O XML desta nota não está disponível.", 404);
    return danfseRenderer.render(xml, doc.isCancelada());
  }

  public NfseDocumentoDaReceitaEntity obter(String id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (Exception ex) {
      throw new ApiClientErrorException("Nota não encontrada.", 404);
    }
    return documentos.findByTenantIdAndId(tenantId, uuid)
        .orElseThrow(() -> new ApiClientErrorException("Nota não encontrada.", 404));
  }

  // ─── apoio ────────────────────────────────────────────────────────────────

  private NfseReceitaDtos.Nota paraDto(NfseDocumentoDaReceitaEntity nota, Set<String> doAzzo) {
    NfseReceitaDtos.Nota dto = new NfseReceitaDtos.Nota();
    dto.id = nota.getId() == null ? null : nota.getId().toString();
    dto.ambiente = nota.getAmbiente();
    dto.chaveAcesso = nota.getChaveAcesso();
    dto.numeroNfse = nota.getNumeroNfse();
    dto.competencia = nota.getCompetencia() == null ? null : nota.getCompetencia().toString();
    dto.emitidaEm = nota.getEmitidaEm() == null ? null : nota.getEmitidaEm().toString();
    dto.papel = nota.getPapel();
    dto.origem = nota.getChaveAcesso() != null && doAzzo.contains(nota.getChaveAcesso()) ? "AZZO" : "EXTERNA";
    dto.valorServicos = nota.getValorServicos();
    dto.valorIss = nota.getValorIss();
    dto.issRetido = nota.isIssRetido();
    dto.prestadorDocumento = nota.getPrestadorDocumento();
    dto.prestadorNome = nota.getPrestadorNome();
    dto.tomadorDocumento = nota.getTomadorDocumento();
    dto.tomadorNome = nota.getTomadorNome();
    dto.descricao = nota.getDescricao();
    dto.cancelada = nota.isCancelada();
    return dto;
  }

  private String decifrar(NfseDocumentoDaReceitaEntity doc) {
    byte[] cifrado = doc.getXmlEnc();
    if (cifrado == null || cifrado.length == 0) return null;
    return encryptionService.decrypt(new String(cifrado, StandardCharsets.UTF_8));
  }

  private NfseReceitaCursorEntity cursor(UUID tenantId, String ambiente) {
    return cursores.findById(new NfseReceitaCursorEntity.Chave(tenantId, ambiente)).orElseGet(() -> {
      NfseReceitaCursorEntity novo = new NfseReceitaCursorEntity();
      novo.setTenantId(tenantId);
      novo.setAmbiente(ambiente);
      novo.setUltimoNsu(0);
      return novo;
    });
  }

  private void salvarCursor(UUID tenantId, String ambiente, long nsu) {
    transacao.executeWithoutResult(status -> {
      NfseReceitaCursorEntity cursor = cursor(tenantId, ambiente);
      cursor.setUltimoNsu(Math.max(cursor.getUltimoNsu(), nsu));
      cursor.setUltimaBuscaEm(Instant.now());
      cursores.save(cursor);
    });
  }

  private String cnpjDoSalao(UUID tenantId) {
    String cnpj = fiscalTaxConfigRepository.findByTenantId(tenantId)
        .map(config -> config.getIssuerCnpj())
        .filter(Objects::nonNull)
        .map(valor -> valor.replaceAll("\\D", ""))
        .orElse("");
    if (cnpj.length() != 14) {
      throw new ApiClientErrorException("Informe o CNPJ do salão nas configurações fiscais antes de buscar na Receita.", 422);
    }
    return cnpj;
  }

  static String ambiente(String valor) {
    String amb = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    if (!"HOMOLOGACAO".equals(amb) && !"PRODUCAO".equals(amb)) {
      throw new ApiClientErrorException("Ambiente inválido: use HOMOLOGACAO ou PRODUCAO.", 400);
    }
    return amb;
  }

  private static LocalDate dataOuNula(String valor) {
    try {
      return valor == null || valor.isBlank() ? null : LocalDate.parse(valor.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private static void pausa(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
