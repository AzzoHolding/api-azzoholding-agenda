package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEventEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseStateTransitionException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Porte verbatim de {@code modules/nfse/application/NfseStatusPollingInvoiceProcessor.java}
 * (Fronteira 7).
 *
 * <p>{@code REQUIRES_NEW} + timeout de 120s por invoice (equivalente ao
 * {@code @TransactionConfiguration(timeout = 120)} do original) — chamado de {@link
 * NfseStatusPollingWorker}, bean diferente, entao o proxy do Spring intercepta normalmente.
 *
 * <p><b>Assimetria preservada, nao "consertada"</b> (achado 2 da Etapa 25 do inventario): esta
 * classe duplica por copy-paste a mesma sequencia de "finalizar autorizacao" que
 * {@code NfseService.autorizar}/{@code finalizarAutorizacao} (fluxo sincrono) faz — transicionar
 * estado, montar XML retorno, criptografar, registrar evento — sem helper compartilhado, tal como
 * no original.
 */
@Service
public class NfseStatusPollingInvoiceProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(NfseStatusPollingInvoiceProcessor.class);

  private final NfseInvoiceRepository nfseInvoiceRepository;
  private final NfseInvoiceEventRepository nfseInvoiceEventRepository;
  private final NfseProviderRouterService nfseProviderRouterService;
  private final NfseFiscalStateMachine nfseFiscalStateMachine;
  private final NfseXmlBuilderService nfseXmlBuilderService;
  private final EncryptionService encryptionService;

  public NfseStatusPollingInvoiceProcessor(
      NfseInvoiceRepository nfseInvoiceRepository,
      NfseInvoiceEventRepository nfseInvoiceEventRepository,
      NfseProviderRouterService nfseProviderRouterService,
      NfseFiscalStateMachine nfseFiscalStateMachine,
      NfseXmlBuilderService nfseXmlBuilderService,
      EncryptionService encryptionService) {
    this.nfseInvoiceRepository = nfseInvoiceRepository;
    this.nfseInvoiceEventRepository = nfseInvoiceEventRepository;
    this.nfseProviderRouterService = nfseProviderRouterService;
    this.nfseFiscalStateMachine = nfseFiscalStateMachine;
    this.nfseXmlBuilderService = nfseXmlBuilderService;
    this.encryptionService = encryptionService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 120)
  public boolean processarInvoice(UUID tenantId, UUID invoiceId) {
    NfseInvoiceEntity invoice = nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId).orElse(null);
    if (invoice == null || invoice.getFiscalStatus() != NfseFiscalStatus.PENDING) return false;

    try {
      NfseProviderAdapter provider = nfseProviderRouterService.resolve(invoice.getProvedor());
      var statusOpt = provider.queryStatus(invoice);
      if (statusOpt.isEmpty()) return false;
      NfseProviderAdapter.StatusQueryResult status = statusOpt.get();

      if (status.authorized()) {
        transicionarFiscalStatus(invoice, NfseFiscalStatus.AUTHORIZED, "AUTHORIZED");
        invoice.setOperationalStatus(null);
        invoice.setNumeroNfse(status.numeroNfse());
        invoice.setProtocolo(firstNonBlank(status.protocolo(), invoice.getProtocolo()));
        invoice.setCodigoVerificacao(firstNonBlank(status.codigoVerificacao(), invoice.getCodigoVerificacao()));
        invoice.setChaveAcessoNfse(firstNonBlank(status.chaveAcessoNfse(), invoice.getChaveAcessoNfse()));
        invoice.setDataEmissao(Instant.now());
        String retornoXml =
            nfseXmlBuilderService.buildAuthorizationReturnXml(
                invoice, status.providerStatusCode(), status.providerStatusMessage());
        invoice.setXmlRetornoEnc(asEncryptedBytes(retornoXml));
        registrarEvento(
            invoice.getTenantId(),
            invoice.getId(),
            "POLLING_AUTHORIZED",
            "AUTHORIZED",
            status.providerStatusCode(),
            status.providerStatusMessage(),
            retornoXml);
        return true;
      }

      if (status.rejected()) {
        transicionarFiscalStatus(invoice, NfseFiscalStatus.REJECTED, "REJECTED");
        invoice.setOperationalStatus(null);
        registrarEvento(
            invoice.getTenantId(),
            invoice.getId(),
            "POLLING_REJECTED",
            "REJECTED",
            status.providerStatusCode(),
            status.providerStatusMessage(),
            null);
        return true;
      }

      invoice.setOperationalStatus(NfseOperationalStatus.WAITING_PROVIDER);
      invoice.setProtocolo(firstNonBlank(status.protocolo(), invoice.getProtocolo()));
      return false;
    } catch (Exception ex) {
      invoice.setOperationalStatus(NfseOperationalStatus.RETRY_SCHEDULED);
      registrarEvento(
          invoice.getTenantId(),
          invoice.getId(),
          "POLLING_ERROR",
          "PENDING",
          "POLLING_ERROR",
          resumir(ex.getMessage(), 500),
          null);
      LOG.warn("Falha no polling de status NFS-e invoice={} tenant={}", invoice.getId(), invoice.getTenantId(), ex);
      return false;
    }
  }

  private void transicionarFiscalStatus(NfseInvoiceEntity invoice, NfseFiscalStatus target, String eventStatus) {
    NfseFiscalStatus current = invoice.getFiscalStatus();
    if (current == target) return;
    if (!nfseFiscalStateMachine.canReach(current, target)) {
      throw new NfseStateTransitionException("Transicao fiscal NFS-e invalida: " + current + " -> " + target);
    }
    invoice.setFiscalStatus(target);
    registrarEvento(invoice.getTenantId(), invoice.getId(), target.name(), eventStatus, null, null, null);
  }

  private void registrarEvento(
      UUID tenantId,
      UUID invoiceId,
      String eventType,
      String eventStatus,
      String providerCode,
      String providerMessage,
      String payloadSource) {
    NfseInvoiceEventEntity event = new NfseInvoiceEventEntity();
    event.setTenantId(tenantId);
    event.setInvoiceId(invoiceId);
    event.setEventType(eventType);
    event.setEventStatus(eventStatus);
    event.setProviderCode(providerCode);
    event.setProviderMessage(providerMessage);
    event.setPayloadHash(payloadSource == null ? null : sha256(payloadSource));
    event.setRequestedBy(null);
    nfseInvoiceEventRepository.save(event);
  }

  private byte[] asEncryptedBytes(String xml) {
    String encrypted = encryptionService.encrypt(xml);
    return encrypted == null ? null : encrypted.getBytes(StandardCharsets.UTF_8);
  }

  private String firstNonBlank(String first, String fallback) {
    if (first != null && !first.isBlank()) return first;
    return fallback;
  }

  private String resumir(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      return null;
    }
  }
}
