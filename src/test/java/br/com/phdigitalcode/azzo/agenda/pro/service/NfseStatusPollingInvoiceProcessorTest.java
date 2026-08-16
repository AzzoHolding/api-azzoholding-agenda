package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEventEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Cobre {@code modules/nfse/application/NfseStatusPollingInvoiceProcessor.java} (Fronteira 7).
 * Original sem teste proprio no Quarkus.
 *
 * <p>Usa a {@link NfseFiscalStateMachine} real (nao mockada) — mesma escolha ja feita em outras
 * fronteiras quando a maquina de estados e simples o bastante para nao precisar de stub, e assim
 * as transicoes reais (BFS de alcancabilidade) sao exercitadas de verdade.
 */
@ExtendWith(MockitoExtension.class)
class NfseStatusPollingInvoiceProcessorTest {

  @Mock private NfseInvoiceRepository nfseInvoiceRepository;
  @Mock private NfseInvoiceEventRepository nfseInvoiceEventRepository;
  @Mock private NfseProviderRouterService nfseProviderRouterService;
  @Mock private NfseProviderAdapter provider;
  @Mock private EncryptionService encryptionService;
  @Mock private NfseXmlBuilderService nfseXmlBuilderService;

  private NfseStatusPollingInvoiceProcessor processor;
  private final UUID tenantId = UUID.randomUUID();
  private final UUID invoiceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processor =
        new NfseStatusPollingInvoiceProcessor(
            nfseInvoiceRepository,
            nfseInvoiceEventRepository,
            nfseProviderRouterService,
            new NfseFiscalStateMachine(),
            nfseXmlBuilderService,
            encryptionService);
  }

  @Test
  void processarInvoiceDevolveFalseQuandoInvoiceNaoExiste() {
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.empty());

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isFalse();
    verify(nfseProviderRouterService, never()).resolve(any());
  }

  @Test
  void processarInvoiceDevolveFalseQuandoInvoiceNaoEstaPending() {
    NfseInvoiceEntity invoice = invoicePending();
    invoice.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isFalse();
    verify(nfseProviderRouterService, never()).resolve(any());
  }

  @Test
  void processarInvoiceDevolveFalseQuandoProviderNaoDevolveStatus() {
    NfseInvoiceEntity invoice = invoicePending();
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(provider);
    when(provider.queryStatus(invoice)).thenReturn(Optional.empty());

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isFalse();
    verify(nfseInvoiceEventRepository, never()).save(any());
  }

  @Test
  void processarInvoiceAutorizadaTransicionaParaAuthorizedECriptografaOXml() {
    NfseInvoiceEntity invoice = invoicePending();
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(provider);
    NfseProviderAdapter.StatusQueryResult status =
        new NfseProviderAdapter.StatusQueryResult(
            "100", "Autorizado", "12345", "proto-1", "cod-verif-1", "chave-1", true, false, false);
    when(provider.queryStatus(invoice)).thenReturn(Optional.of(status));
    when(nfseXmlBuilderService.buildAuthorizationReturnXml(invoice, "100", "Autorizado"))
        .thenReturn("<xml/>");
    when(encryptionService.encrypt("<xml/>")).thenReturn("encrypted-xml");

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isTrue();
    assertThat(invoice.getFiscalStatus()).isEqualTo(NfseFiscalStatus.AUTHORIZED);
    assertThat(invoice.getOperationalStatus()).isNull();
    assertThat(invoice.getNumeroNfse()).isEqualTo("12345");
    assertThat(invoice.getProtocolo()).isEqualTo("proto-1");
    assertThat(invoice.getCodigoVerificacao()).isEqualTo("cod-verif-1");
    assertThat(invoice.getChaveAcessoNfse()).isEqualTo("chave-1");
    assertThat(invoice.getDataEmissao()).isNotNull();
    assertThat(new String(invoice.getXmlRetornoEnc())).isEqualTo("encrypted-xml");

    ArgumentCaptor<NfseInvoiceEventEntity> captor = ArgumentCaptor.forClass(NfseInvoiceEventEntity.class);
    verify(nfseInvoiceEventRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(NfseInvoiceEventEntity::getEventType)
        .containsExactly("AUTHORIZED", "POLLING_AUTHORIZED");
  }

  @Test
  void processarInvoiceRejeitadaTransicionaParaRejectedERegistraEvento() {
    NfseInvoiceEntity invoice = invoicePending();
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(provider);
    NfseProviderAdapter.StatusQueryResult status =
        new NfseProviderAdapter.StatusQueryResult(
            "200", "Rejeitado", null, null, null, null, false, true, false);
    when(provider.queryStatus(invoice)).thenReturn(Optional.of(status));

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isTrue();
    assertThat(invoice.getFiscalStatus()).isEqualTo(NfseFiscalStatus.REJECTED);
    assertThat(invoice.getOperationalStatus()).isNull();

    ArgumentCaptor<NfseInvoiceEventEntity> captor = ArgumentCaptor.forClass(NfseInvoiceEventEntity.class);
    verify(nfseInvoiceEventRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(NfseInvoiceEventEntity::getEventType)
        .containsExactly("REJECTED", "POLLING_REJECTED");
  }

  @Test
  void processarInvoiceAindaPendenteMarcaWaitingProviderEAtualizaProtocolo() {
    NfseInvoiceEntity invoice = invoicePending();
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(provider);
    NfseProviderAdapter.StatusQueryResult status =
        new NfseProviderAdapter.StatusQueryResult(
            "100", "Em processamento", null, "proto-novo", null, null, false, false, true);
    when(provider.queryStatus(invoice)).thenReturn(Optional.of(status));

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isFalse();
    assertThat(invoice.getFiscalStatus()).isEqualTo(NfseFiscalStatus.PENDING);
    assertThat(invoice.getOperationalStatus()).isEqualTo(NfseOperationalStatus.WAITING_PROVIDER);
    assertThat(invoice.getProtocolo()).isEqualTo("proto-novo");
    verify(nfseInvoiceEventRepository, never()).save(any());
  }

  @Test
  void processarInvoiceComExcecaoMarcaRetryScheduledERegistraEventoDeErro() {
    NfseInvoiceEntity invoice = invoicePending();
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(provider);
    when(provider.queryStatus(invoice)).thenThrow(new RuntimeException("provedor indisponivel"));

    boolean resultado = processor.processarInvoice(tenantId, invoiceId);

    assertThat(resultado).isFalse();
    assertThat(invoice.getOperationalStatus()).isEqualTo(NfseOperationalStatus.RETRY_SCHEDULED);

    ArgumentCaptor<NfseInvoiceEventEntity> captor = ArgumentCaptor.forClass(NfseInvoiceEventEntity.class);
    verify(nfseInvoiceEventRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("POLLING_ERROR");
    assertThat(captor.getValue().getEventStatus()).isEqualTo("PENDING");
    assertThat(captor.getValue().getProviderMessage()).isEqualTo("provedor indisponivel");
  }

  private NfseInvoiceEntity invoicePending() {
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(invoiceId);
    invoice.setTenantId(tenantId);
    invoice.setProvedor("ABRASF");
    invoice.setFiscalStatus(NfseFiscalStatus.PENDING);
    return invoice;
  }
}
