package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Tudo o que o salao guarda sobre um cliente, num arquivo so — LGPD art. 18, II (acesso) e V
 * (portabilidade), art. 19 (declaracao completa em ate 15 dias).
 *
 * <p>Ate 2026-10-13 o modulo LGPD so registrava o pedido e anonimizava: um pedido de acesso nao
 * tinha como ser atendido pelo sistema. Cada exportacao vira uma solicitacao "ACESSO" ja encerrada
 * (com protocolo) e um evento de auditoria — a prova de que o pedido foi atendido.
 *
 * <p>JSON de proposito: legivel por pessoa e por maquina (portabilidade). Mensagens do chat saem
 * decifradas; o que ja passou do prazo de retencao nao existe mais e nao aparece.
 */
@Service
public class ServicoExportacaoTitular {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoExportacaoTitular.class);

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final ClienteRepository clienteRepository;
  private final TenantRepository tenantRepository;
  private final LgpdDataSubjectRequestRepository requestRepository;
  private final EncryptionService encryptionService;
  private final AuditService auditService;

  @PersistenceContext private EntityManager entityManager;

  public ServicoExportacaoTitular(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      ClienteRepository clienteRepository,
      TenantRepository tenantRepository,
      LgpdDataSubjectRequestRepository requestRepository,
      EncryptionService encryptionService,
      AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.clienteRepository = clienteRepository;
    this.tenantRepository = tenantRepository;
    this.requestRepository = requestRepository;
    this.encryptionService = encryptionService;
    this.auditService = auditService;
  }

  @Transactional
  public Map<String, Object> exportar(UUID clientId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente cliente = clienteRepository.findByIdAndTenantId(clientId, tenantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado"));
    Instant agora = Instant.now();

    Map<String, Object> exportacao = new LinkedHashMap<>();
    exportacao.put("geradoEm", agora.toString());
    exportacao.put("controlador", controlador(tenantId));
    exportacao.put("titular", titular(cliente));
    exportacao.put("consentimentos", consentimentos(tenantId, cliente));
    exportacao.put("agendamentos", linhas(
        """
        SELECT a.code, a.date, a.start_time, a.end_time, a.status, a.total_price, a.notes,
               p.name,
               (SELECT string_agg(s.name, ', ' ORDER BY s.name) FROM appointment_items ai
                  JOIN services s ON s.id = ai.service_id
                 WHERE ai.appointment_id = a.id AND ai.tenant_id = a.tenant_id)
          FROM appointments a
          LEFT JOIN professionals p ON p.id = a.professional_id
         WHERE a.tenant_id = :tenantId AND a.client_id = :clientId
         ORDER BY a.date, a.start_time
        """,
        tenantId, clientId,
        "codigo", "data", "inicio", "fim", "situacao", "valor", "observacoes", "profissional", "servicos"));
    exportacao.put("comandas", linhas(
        """
        SELECT opened_at, closed_at, status, subtotal, desconto, gorjeta, total
          FROM comandas WHERE tenant_id = :tenantId AND client_id = :clientId ORDER BY opened_at
        """,
        tenantId, clientId,
        "abertaEm", "fechadaEm", "situacao", "subtotal", "desconto", "gorjeta", "total"));
    exportacao.put("notasDeAtendimento", linhas(
        """
        SELECT created_at, service_execution_notes, client_feedback_notes, internal_followup_notes
          FROM appointment_customer_notes WHERE tenant_id = :tenantId AND client_id = :clientId
         ORDER BY created_at
        """,
        tenantId, clientId,
        "registradaEm", "execucaoDoServico", "retornoDoCliente", "acompanhamentoInterno"));
    exportacao.put("pacotes", linhas(
        """
        SELECT package_nome, preco_pago, created_at FROM client_package_purchases
         WHERE tenant_id = :tenantId AND client_id = :clientId ORDER BY created_at
        """,
        tenantId, clientId, "pacote", "valorPago", "compradoEm"));
    exportacao.put("assinaturas", linhas(
        """
        SELECT plan_nome, preco_mensal, status, period_start, period_end, created_at
          FROM client_memberships WHERE tenant_id = :tenantId AND client_id = :clientId
         ORDER BY created_at
        """,
        tenantId, clientId, "plano", "valorMensal", "situacao", "inicioDoPeriodo", "fimDoPeriodo", "criadaEm"));
    exportacao.put("notasFiscais", linhas(
        """
        SELECT n.numero_nfse, n.data_emissao, n.valor_servicos, n.fiscal_status
          FROM nfse_invoices n
         WHERE n.tenant_id = :tenantId AND n.appointment_id IN (
               SELECT a.id FROM appointments a WHERE a.tenant_id = :tenantId AND a.client_id = :clientId)
         ORDER BY n.data_emissao
        """,
        tenantId, clientId, "numero", "emitidaEm", "valor", "situacao"));
    exportacao.put("mensagens", mensagens(tenantId, clientId));
    exportacao.put("notificacoes", linhas(
        """
        SELECT n.created_at, n.channel, n.status, n.message
          FROM notifications n
         WHERE n.tenant_id = :tenantId AND n.appointment_id IN (
               SELECT a.id FROM appointments a WHERE a.tenant_id = :tenantId AND a.client_id = :clientId)
         ORDER BY n.created_at
        """,
        tenantId, clientId, "enviadaEm", "canal", "situacao", "mensagem"));
    exportacao.put("sobreOTratamento", sobreOTratamento());

    String protocolo = registrarAtendimento(tenantId, cliente, agora);
    exportacao.put("protocolo", protocolo);
    auditar(tenantId, clientId, protocolo);
    LOG.info("lgpd_export_completed clientId={} tenantId={} protocol={}", clientId, tenantId, protocolo);
    return exportacao;
  }

  private Map<String, Object> controlador(UUID tenantId) {
    Map<String, Object> salao = new LinkedHashMap<>();
    tenantRepository.findById(tenantId).ifPresent(t -> {
      salao.put("nome", t.getName());
      salao.put("documento", t.getDocument());
      salao.put("email", t.getEmail());
      salao.put("telefone", t.getPhone());
    });
    salao.put("operador", "Azzo Agenda Pro (plataforma usada pelo salao)");
    return salao;
  }

  private Map<String, Object> titular(Cliente c) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("nome", c.getName());
    t.put("email", c.getEmail());
    t.put("telefone", c.getPhone());
    t.put("documento", c.getCpfCnpj());
    t.put("nascimento", texto(c.getBirthDate()));
    Map<String, Object> endereco = new LinkedHashMap<>();
    endereco.put("cep", c.getZipCode());
    endereco.put("logradouro", c.getStreet());
    endereco.put("numero", c.getNumber());
    endereco.put("complemento", c.getComplement());
    endereco.put("bairro", c.getNeighborhood());
    endereco.put("cidade", c.getCity());
    endereco.put("uf", c.getState());
    t.put("endereco", endereco);
    t.put("observacoes", c.getNotes());
    t.put("pontosDeFidelidade", c.getLoyaltyPoints());
    t.put("clienteDesde", texto(c.getCreatedAt()));
    t.put("anonimizadoEm", texto(c.getAnonymizedAt()));
    return t;
  }

  private Map<String, Object> consentimentos(UUID tenantId, Cliente c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("aceitaMensagensAutomaticas", !Boolean.TRUE.equals(c.getWhatsappOptOut()));
    m.put("optInEm", texto(c.getWhatsappOptInAt()));
    m.put("optOutEm", texto(c.getWhatsappOptOutAt()));
    m.put("historico", linhas(
        """
        SELECT action, source, created_at FROM reactivation_consent_history
         WHERE tenant_id = :tenantId AND client_id = :clientId ORDER BY created_at
        """,
        tenantId, c.getId(), "acao", "origem", "registradoEm"));
    return m;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> mensagens(UUID tenantId, UUID clientId) {
    List<Object[]> rows = entityManager.createNativeQuery(
            """
            SELECT created_at, direction, content FROM chat_messages
             WHERE tenant_id = :tenantId AND client_id = :clientId AND content IS NOT NULL
             ORDER BY created_at
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("clientId", clientId)
        .getResultList();
    List<Map<String, Object>> mensagens = new ArrayList<>();
    for (Object[] row : rows) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("em", texto(row[0]));
      m.put("sentido", "INBOUND".equals(String.valueOf(row[1])) ? "do cliente" : "do salao");
      m.put("texto", decifrar(row[2] == null ? null : row[2].toString()));
      mensagens.add(m);
    }
    return mensagens;
  }

  private String decifrar(String conteudo) {
    if (conteudo == null || conteudo.isBlank()) return conteudo;
    try {
      return encryptionService.decrypt(conteudo);
    } catch (RuntimeException e) {
      // Conteudo antigo, de antes da cifra: ja e o texto.
      return conteudo;
    }
  }

  private static Map<String, Object> sobreOTratamento() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("finalidades", List.of(
        "Agendar, confirmar e lembrar atendimentos (execucao de contrato, art. 7, V)",
        "Registrar o atendimento, a venda e emitir nota fiscal (obrigacao legal, art. 7, II)",
        "Mensagens de retorno ao salao, com opcao de parar a qualquer momento (legitimo interesse, art. 7, IX)"));
    m.put("compartilhamento", List.of(
        "Meta (WhatsApp) e Telegram: entrega das mensagens",
        "Provedor de inteligencia artificial do assistente, quando o salao o usa",
        "Asaas: cobrancas, quando o salao as usa",
        "Prefeitura: nota fiscal de servico"));
    m.put("direitos",
        "Corrigir, anonimizar, eliminar, saber com quem os dados sao compartilhados e revogar o "
            + "consentimento: peca ao salao (controlador). Mensagens automaticas: responda PARE.");
    return m;
  }

  private String registrarAtendimento(UUID tenantId, Cliente cliente, Instant agora) {
    LgpdDataSubjectRequest pedido = new LgpdDataSubjectRequest();
    pedido.setTenantId(tenantId);
    pedido.setProtocolCode(protocolo(tenantId, agora));
    pedido.setRequestType("ACESSO");
    pedido.setStatus("ENCERRADO");
    pedido.setRequesterName(cliente.getName());
    pedido.setRequesterEmail(cliente.getEmail() != null ? cliente.getEmail() : "nao-informado@sistema");
    pedido.setRequesterDocument(cliente.getCpfCnpj());
    pedido.setDescription("Exportacao dos dados do cliente " + cliente.getId() + " (acesso e portabilidade)");
    pedido.setResponseSummary("Arquivo com os dados do titular gerado pelo operador.");
    pedido.setCreatedByUserId(ator());
    pedido.setClosedAt(agora);
    requestRepository.save(pedido);
    return pedido.getProtocolCode();
  }

  private String protocolo(UUID tenantId, Instant agora) {
    String dia = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(agora);
    for (int i = 0; i < 5; i++) {
      String codigo = "LGPD-" + dia + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
      if (requestRepository.findByTenantAndProtocol(tenantId, codigo).isEmpty()) return codigo;
    }
    return "LGPD-" + dia + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  private void auditar(UUID tenantId, UUID clientId, String protocolo) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = ator();
      command.module = AuditConstants.Module.LGPD;
      command.action = "LGPD_CLIENT_DATA_EXPORT";
      command.entityType = "CLIENT";
      command.entityId = clientId.toString();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.after = Map.of("clientId", clientId.toString(), "protocolo", protocolo, "compliance", "LGPD art. 18 II e V");
      auditService.recordSuccess(command);
    } catch (RuntimeException e) {
      LOG.warn("lgpd_export_audit_failed clientId={} tenantId={}", clientId, tenantId);
    }
  }

  private UUID ator() {
    try {
      return authenticatedUser.idOuNulo();
    } catch (RuntimeException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> linhas(String sql, UUID tenantId, UUID clientId, String... nomes) {
    List<Object> rows = entityManager.createNativeQuery(sql)
        .setParameter("tenantId", tenantId)
        .setParameter("clientId", clientId)
        .getResultList();
    List<Map<String, Object>> resultado = new ArrayList<>();
    for (Object linha : rows) {
      Object[] valores = linha instanceof Object[] arr ? arr : new Object[] {linha};
      Map<String, Object> m = new LinkedHashMap<>();
      for (int i = 0; i < nomes.length && i < valores.length; i++) m.put(nomes[i], valor(valores[i]));
      resultado.add(m);
    }
    return resultado;
  }

  /** Datas e ids como texto; numero continua numero. */
  private static Object valor(Object v) {
    if (v == null || v instanceof Number || v instanceof Boolean || v instanceof String) return v;
    return texto(v);
  }

  private static String texto(Object v) {
    return v == null ? null : v.toString();
  }
}
