package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppTemplateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;

/**
 * A confirmacao que o cliente recebe quando um horario e marcado.
 *
 * <p>Ate 2026-09-24 isto era um <b>placeholder que so logava</b>: o ponto de chamada existia em
 * {@code ServicoAgendamentos.criar}, e nada era enviado. Um cliente marcava e nao recebia nada.
 *
 * <p><b>Sai por TEMPLATE aprovado.</b> Confirmacao de cliente novo e sempre primeiro contato —
 * ninguem escreveu para o salao ainda —, e fora da janela de 24h a Cloud API aceita o texto livre,
 * devolve o identificador e descarta. Sem template, a mensagem nao chega, e o log diria "aceita".
 *
 * <p>Os portoes sao os mesmos do lembrete, e nenhum e novo: WhatsApp ligado no salao, envio
 * proativo permitido, e o opt-out de LGPD do cliente. Quem nao conectou o WhatsApp nao passa a
 * mandar nada por causa desta mudanca.
 */
@Service
public class ServicoConfirmacaoDeAgendamento {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoConfirmacaoDeAgendamento.class);
  /** O cliente le "24/09/2026", e nao "2026-09-24". */
  private static final DateTimeFormatter DIA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final ServicoTemplatesDoWhatsapp servicoTemplates;
  private final ClienteRepository clienteRepository;
  private final AgendamentoItemRepository agendamentoItemRepository;
  private final ServicoRepository servicoRepository;
  private final ProfissionalRepository profissionalRepository;
  private final TenantRepository tenantRepository;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final TenantOperationalSettingsRepository operationalSettingsRepository;
  private final CustomerCommunicationChannelResolver canalDoCliente;

  public ServicoConfirmacaoDeAgendamento(
      ServicoTemplatesDoWhatsapp servicoTemplates,
      ClienteRepository clienteRepository,
      AgendamentoItemRepository agendamentoItemRepository,
      ServicoRepository servicoRepository,
      ProfissionalRepository profissionalRepository,
      TenantRepository tenantRepository,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      TenantOperationalSettingsRepository operationalSettingsRepository,
      CustomerCommunicationChannelResolver canalDoCliente) {
    this.servicoTemplates = servicoTemplates;
    this.clienteRepository = clienteRepository;
    this.agendamentoItemRepository = agendamentoItemRepository;
    this.servicoRepository = servicoRepository;
    this.profissionalRepository = profissionalRepository;
    this.tenantRepository = tenantRepository;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.operationalSettingsRepository = operationalSettingsRepository;
    this.canalDoCliente = canalDoCliente;
  }

  /**
   * Envia a confirmacao. <b>Nunca lanca.</b>
   *
   * <p>O agendamento ja foi criado quando isto roda: derrubar a criacao por uma falha de envio
   * seria trocar um problema pequeno por um grande.
   */
  @Transactional(readOnly = true)
  public void enviar(UUID tenantId, Agendamento agendamento) {
    enviarAviso(
        tenantId, agendamento, WhatsAppTemplateEntity.CONFIRMACAO, "confirmacao",
        this::textoEquivalente);
  }

  /**
   * Avisa o cliente de que o horario dele foi cancelado pelo salao (cancelamento direto ou por
   * fechamento especial). <b>Nunca lanca</b>, pelos mesmos motivos da confirmacao.
   *
   * <p>Sai pelo template {@code azzo_cancelamento} e passa pelos mesmos portoes da confirmacao:
   * WhatsApp ligado, envio proativo permitido e opt-out de LGPD. LGPD: as variaveis sao as mesmas
   * da confirmacao e NENHUMA carrega o motivo do cancelamento ou do fechamento — o cliente recebe
   * so o que o texto do salao pedir entre cliente, data, hora, salao, profissional e servico.
   *
   * <p>Quem chama deve agendar isto para DEPOIS do commit
   * ({@code WhatsAppAppointmentNotificationService} faz): avisar de um cancelamento que depois
   * sofre rollback deixaria o cliente sem o horario que ainda existe.
   */
  @Transactional(readOnly = true)
  public void enviarCancelamento(UUID tenantId, Agendamento agendamento) {
    enviarAviso(
        tenantId, agendamento, WhatsAppTemplateEntity.CANCELAMENTO, "cancelamento",
        this::textoEquivalenteDoCancelamento);
  }

  private void enviarAviso(
      UUID tenantId,
      Agendamento agendamento,
      String finalidade,
      String rotulo,
      java.util.function.Function<Map<String, String>, String> textoEquivalente) {
    if (tenantId == null || agendamento == null || agendamento.getClientId() == null) return;

    try {
      if (!envioPermitido(tenantId)) return;

      Cliente cliente =
          clienteRepository.findByIdAndTenantId(agendamento.getClientId(), tenantId).orElse(null);
      if (cliente == null) return;

      // Gate LGPD, igual ao do lembrete: opt-out vale so para WhatsApp.
      if (Boolean.TRUE.equals(cliente.getWhatsappOptOut())) {
        LOG.debug("Aviso de {} ignorado: cliente={} optou por nao receber", rotulo, cliente.getId());
        return;
      }

      CustomerCommunicationChannelResolver.ResolvedChannel canal =
          canalDoCliente.resolve(tenantId, cliente, cliente.getPhone());
      String destino = canal.externalContactId();
      if (destino == null || destino.replaceAll("\\D", "").length() < 10) {
        LOG.debug("Aviso de {} ignorado: cliente={} sem telefone valido", rotulo, cliente.getId());
        return;
      }

      Map<String, String> valores = valoresDaConfirmacao(tenantId, agendamento, cliente);
      ChannelSendResult resultado =
          servicoTemplates.enviar(
              tenantId,
              finalidade,
              canal.channel() == ChatChannel.TELEGRAM ? ChatChannel.TELEGRAM : ChatChannel.WHATSAPP,
              destino,
              valores,
              textoEquivalente.apply(valores));

      if (resultado != null && !resultado.success()) {
        // O motivo da Meta no log: sem ele, uma mensagem que nao chega vira mistério.
        LOG.warn(
            "whatsapp.{}.recusada tenantId={} appointmentId={} motivo={}",
            rotulo, tenantId, agendamento.getId(), resultado.providerErrorMessage());
      } else {
        LOG.info(
            "whatsapp.{}.enviada tenantId={} appointmentId={}",
            rotulo, tenantId, agendamento.getId());
      }
    } catch (RuntimeException erro) {
      LOG.warn(
          "whatsapp.{}.falhou tenantId={} appointmentId={} motivo={}",
          rotulo, tenantId, agendamento.getId(), erro.getMessage());
    }
  }

  /**
   * O salao quer e pode mandar?
   *
   * <p>Conectar o WhatsApp e o consentimento: quem nao conectou nao passa a mandar nada por causa
   * desta mudanca. {@code whatsappNotifications} continua sendo o interruptor de quem conectou e
   * nao quer.
   */
  private boolean envioPermitido(UUID tenantId) {
    if (!operationalSettingsRepository.findByTenantIdOrCreate(tenantId).isWhatsappNotifications()) {
      return false;
    }
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return config != null && config.isWhatsappEnabled() && config.isProactiveAllowed();
  }

  /**
   * Os valores das variaveis do template.
   *
   * <p>Preenche as seis que o vocabulario conhece. Faltando qualquer uma que o template use, o
   * envio cai no texto livre em vez de mandar a mensagem com um buraco — a regra vive em
   * {@code ServicoTemplatesDoWhatsapp.enviar}.
   */
  private Map<String, String> valoresDaConfirmacao(
      UUID tenantId, Agendamento agendamento, Cliente cliente) {
    Map<String, String> valores = new LinkedHashMap<>();
    valores.put("cliente", cliente.getName());
    valores.put(
        "data", agendamento.getDate() == null ? "" : DIA_BR.format(agendamento.getDate()));
    valores.put("hora", agendamento.getStartTime() == null ? "" : agendamento.getStartTime());

    tenantRepository.findById(tenantId).ifPresent(tenant -> valores.put("salao", tenant.getName()));

    if (agendamento.getProfessionalId() != null) {
      profissionalRepository
          .findByIdAndTenantId(agendamento.getProfessionalId(), tenantId)
          .ifPresent(profissional -> valores.put("profissional", profissional.getName()));
    }

    // O primeiro servico do agendamento. Com varios, o nome do primeiro e o que o cliente
    // reconhece como "o horario dele" — listar todos estouraria o tamanho do template.
    agendamentoItemRepository
        .findByTenantIdAndAppointmentId(tenantId, agendamento.getId())
        .stream()
        .findFirst()
        .flatMap(item -> servicoRepository.findByIdAndTenantId(item.getServiceId(), tenantId))
        .ifPresent(servico -> valores.put("servico", servico.getName()));

    return valores;
  }

  /** O que vai pelo Telegram, onde template nao existe, e quando nao ha template aprovado. */
  private String textoEquivalente(Map<String, String> valores) {
    StringBuilder texto = new StringBuilder("Olá ");
    texto.append(valores.getOrDefault("cliente", "cliente")).append("! Seu agendamento");
    if (valores.containsKey("servico")) texto.append(" de ").append(valores.get("servico"));
    texto.append(" foi confirmado para ").append(valores.getOrDefault("data", ""));
    if (valores.containsKey("hora")) texto.append(" às ").append(valores.get("hora"));
    return texto.append(".").toString();
  }

  /** Sem o motivo do cancelamento, de proposito (LGPD): so o que mudou e como remarcar. */
  private String textoEquivalenteDoCancelamento(Map<String, String> valores) {
    StringBuilder texto = new StringBuilder("Olá ");
    texto.append(valores.getOrDefault("cliente", "cliente")).append("! Seu agendamento");
    if (valores.containsKey("servico")) texto.append(" de ").append(valores.get("servico"));
    texto.append(" de ").append(valores.getOrDefault("data", ""));
    if (valores.containsKey("hora")) texto.append(" às ").append(valores.get("hora"));
    return texto.append(" foi cancelado. Entre em contato para remarcar.").toString();
  }
}
