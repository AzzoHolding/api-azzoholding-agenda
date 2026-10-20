package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationConsentHistoryEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationConsentHistoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;

/**
 * "Nao me mande mais mensagens" vindo de um canal sem o fluxo proprio do WhatsApp (hoje, o
 * Telegram).
 *
 * <p>Ate 2026-10-13 o "PARE" so era entendido no WhatsApp, e o lembrete e a reativacao so
 * olhavam o opt-out quando o canal era WhatsApp: quem falava pelo Telegram nao tinha como parar
 * as mensagens automaticas. O campo continua se chamando {@code whatsappOptOut}, mas vale para
 * mensagem automatica em qualquer canal.
 *
 * <p>Sem registro em {@code reactivation_consent_history}: a coluna {@code source} tem CHECK com
 * as origens antigas, e mudar CHECK sem conferir o banco de producao ja derrubou deploy (V141).
 * Fica pendente; o horario do opt-out vai em {@code whatsappOptOutAt}.
 */
@Service
public class ServicoOptOutDoTitular {

  public static final String CONFIRMACAO =
      "Entendido! Voce nao recebera mais mensagens automaticas de agendamento. "
          + "Para reativar, entre em contato com o salao.";

  private static final List<String> PALAVRAS = List.of("PARE", "STOP", "NAO QUERO", "SAIR", "REMOVER");

  /** Origem gravada no historico de consentimento para o "PARE" vindo do Telegram (V148). */
  public static final String ORIGEM_TELEGRAM = "TELEGRAM_REPLY";

  private final ClienteRepository clienteRepository;
  private final WhatsAppBookingReactivationCycleRepository reactivationCycleRepository;
  private final WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private final ReactivationConsentHistoryRepository consentHistoryRepository;

  public ServicoOptOutDoTitular(
      ClienteRepository clienteRepository,
      WhatsAppBookingReactivationCycleRepository reactivationCycleRepository,
      WhatsAppBookingReactivationService whatsAppBookingReactivationService,
      ReactivationConsentHistoryRepository consentHistoryRepository) {
    this.clienteRepository = clienteRepository;
    this.reactivationCycleRepository = reactivationCycleRepository;
    this.whatsAppBookingReactivationService = whatsAppBookingReactivationService;
    this.consentHistoryRepository = consentHistoryRepository;
  }

  /**
   * Mesmas palavras do WhatsApp. "CANCELAR" fica de fora de proposito (achado INT-006): e a
   * resposta que o lembrete pede para cancelar o HORARIO, nao as mensagens.
   */
  public static boolean ehPedidoDeOptOut(String texto) {
    if (texto == null) return false;
    String normalizado = java.text.Normalizer.normalize(texto.strip(), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toUpperCase(Locale.ROOT);
    return PALAVRAS.contains(normalizado);
  }

  /**
   * Registra o opt-out e deixa a linha no historico de consentimento com a origem do pedido.
   * Antes so mudava a ficha: o "PARE" do Telegram nao aparecia no historico (o CHECK de origem nao
   * aceitava Telegram; ampliado na V148).
   */
  @Transactional
  public void registrar(Cliente cliente, String origem) {
    registrar(cliente);
    ReactivationConsentHistoryEntity consent = new ReactivationConsentHistoryEntity();
    consent.setTenantId(cliente.getTenantId());
    consent.setClientId(cliente.getId());
    consent.setAction("OPT_OUT");
    consent.setSource(origem);
    consentHistoryRepository.save(consent);
  }

  @Transactional
  public void registrar(Cliente cliente) {
    cliente.setWhatsappOptOut(true);
    cliente.setWhatsappOptOutAt(Instant.now());
    cliente.setWhatsappOptIn(false);
    clienteRepository.save(cliente);
    List<WhatsAppBookingReactivationCycleEntity> ativos =
        reactivationCycleRepository.listActiveByTenantAndClient(cliente.getTenantId(), cliente.getId());
    for (WhatsAppBookingReactivationCycleEntity ciclo : ativos) {
      whatsAppBookingReactivationService.cancelCycle(ciclo, "OPT_OUT");
    }
  }
}
