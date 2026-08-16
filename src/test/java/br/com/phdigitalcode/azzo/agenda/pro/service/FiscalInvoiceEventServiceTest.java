package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEventEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceEventRepository;

/** Cobre {@code modules/fiscal/application/FiscalInvoiceEventService.java}. */
@ExtendWith(MockitoExtension.class)
class FiscalInvoiceEventServiceTest {

  @Mock private FiscalInvoiceEventRepository fiscalInvoiceEventRepository;

  private FiscalInvoiceEventService service;
  private final UUID tenantId = UUID.randomUUID();
  private final UUID invoiceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FiscalInvoiceEventService(fiscalInvoiceEventRepository);
  }

  @Test
  void gravaOEventoComOsCamposDaSefaz() {
    service.registrarEvento(
        tenantId, invoiceId, "AUTORIZACAO", "OK", "100", "Autorizado o uso da NF-e");

    ArgumentCaptor<FiscalInvoiceEventEntity> gravado =
        ArgumentCaptor.forClass(FiscalInvoiceEventEntity.class);
    verify(fiscalInvoiceEventRepository).save(gravado.capture());

    FiscalInvoiceEventEntity evento = gravado.getValue();
    assertThat(evento.getTenantId()).isEqualTo(tenantId);
    assertThat(evento.getInvoiceId()).isEqualTo(invoiceId);
    assertThat(evento.getEventType()).isEqualTo("AUTORIZACAO");
    assertThat(evento.getEventStatus()).isEqualTo("OK");
    assertThat(evento.getSefazStatusCode()).isEqualTo("100");
    assertThat(evento.getSefazStatusMessage()).isEqualTo("Autorizado o uso da NF-e");
  }

  @Test
  void osCamposDaSefazSaoOpcionais() {
    service.registrarEvento(tenantId, invoiceId, "EMISSAO", "PENDENTE", null, null);

    ArgumentCaptor<FiscalInvoiceEventEntity> gravado =
        ArgumentCaptor.forClass(FiscalInvoiceEventEntity.class);
    verify(fiscalInvoiceEventRepository).save(gravado.capture());
    assertThat(gravado.getValue().getSefazStatusCode()).isNull();
    assertThat(gravado.getValue().getSefazStatusMessage()).isNull();
  }

  /**
   * ⚠️ Comportamento do original: campo obrigatorio faltando faz o metodo <b>sair sem gravar e sem
   * erro</b>. O evento e observabilidade — perder o registro e preferivel a derrubar a emissao da
   * nota. Trocar por excecao mudaria o comportamento do {@code ServicoFiscal}.
   */
  @Test
  void campoObrigatorioAusenteNaoGravaENaoEstoura() {
    service.registrarEvento(null, invoiceId, "AUTORIZACAO", "OK", null, null);
    service.registrarEvento(tenantId, null, "AUTORIZACAO", "OK", null, null);
    service.registrarEvento(tenantId, invoiceId, null, "OK", null, null);
    service.registrarEvento(tenantId, invoiceId, "  ", "OK", null, null);
    service.registrarEvento(tenantId, invoiceId, "AUTORIZACAO", null, null, null);
    service.registrarEvento(tenantId, invoiceId, "AUTORIZACAO", "  ", null, null);

    verifyNoInteractions(fiscalInvoiceEventRepository);
  }

  @Test
  void oTipoEOStatusSaoGravadosCrusSemNormalizacao() {
    service.registrarEvento(tenantId, invoiceId, " cancelamento ", " ok ", null, null);

    ArgumentCaptor<FiscalInvoiceEventEntity> gravado =
        ArgumentCaptor.forClass(FiscalInvoiceEventEntity.class);
    verify(fiscalInvoiceEventRepository).save(gravado.capture());
    assertThat(gravado.getValue().getEventType()).isEqualTo(" cancelamento ");
    assertThat(gravado.getValue().getEventStatus()).isEqualTo(" ok ");
  }
}
