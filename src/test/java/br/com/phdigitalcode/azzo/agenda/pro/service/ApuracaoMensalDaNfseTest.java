package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;

/** A apuracao do salao sai das NFS-e validas de producao, e o imposto e o ISS que o salao paga. */
class ApuracaoMensalDaNfseTest {

  private final UUID tenantId = UUID.randomUUID();
  private final NfseInvoiceRepository repositorio = mock(NfseInvoiceRepository.class);
  private final ApuracaoMensalDaNfse apuracao = new ApuracaoMensalDaNfse(repositorio);

  @Test
  void somaServicosEIssDasNotasDeProducaoDoMesPelaCompetencia() {
    when(repositorio.listForAccountingExport(
            eq(tenantId), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)), eq(ApuracaoMensalDaNfse.VALIDAS)))
        .thenReturn(List.of(
            nota(NfseAmbiente.PRODUCAO, "100.00", "5.00", false),
            nota(NfseAmbiente.PRODUCAO, "50.50", "2.53", false),
            nota(NfseAmbiente.HOMOLOGACAO, "999.00", "49.95", false)));

    FiscalDtos.ApuracaoMensal mes = apuracao.doMes(tenantId, 2026, 11);

    assertThat(mes.totalServicos).isEqualTo(15050);
    assertThat(mes.totalImpostos).isEqualTo(753);
    // A nota de homologacao e teste: fica fora.
    assertThat(mes.totalDocumentos).isEqualTo(2);
    assertThat(mes.documentos).allSatisfy(doc -> assertThat(doc.type).isEqualTo("NFSE"));
    assertThat(mes.documentos.get(0).numeroNf).isEqualTo("77");
    assertThat(mes.documentos.get(0).totalAmount).isEqualTo(10000);
  }

  /** ISS retido e recolhido pelo tomador: entra nos servicos, nao nos impostos do salao. */
  @Test
  void issRetidoNaoContaComoImpostoDoSalao() {
    when(repositorio.listForAccountingExport(eq(tenantId), any(), any(), any()))
        .thenReturn(List.of(nota(NfseAmbiente.PRODUCAO, "200.00", "10.00", true)));

    FiscalDtos.ApuracaoMensal mes = apuracao.doMes(tenantId, 2026, 10);

    assertThat(mes.totalServicos).isEqualTo(20000);
    assertThat(mes.totalImpostos).isZero();
  }

  @Test
  void soNotasValidasSaoPedidasAoBanco() {
    when(repositorio.listForAccountingExport(eq(tenantId), any(), any(), any())).thenReturn(List.of());

    apuracao.doMes(tenantId, 2026, 2);

    verify(repositorio).listForAccountingExport(
        tenantId,
        LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 2, 28),
        List.of(NfseFiscalStatus.AUTHORIZED, NfseFiscalStatus.CANCEL_PENDING, NfseFiscalStatus.CANCEL_REJECTED));
  }

  @Test
  void resumoAnualSomaOsDozeMeses() {
    when(repositorio.listForAccountingExport(eq(tenantId), any(), any(), any()))
        .thenReturn(List.of(nota(NfseAmbiente.PRODUCAO, "10.00", "0.50", false)));

    FiscalDtos.ResumoAnual ano = apuracao.resumoAnual(tenantId, 2026);

    assertThat(ano.meses).hasSize(12);
    assertThat(ano.totalServicos).isEqualTo(12000);
    assertThat(ano.totalImpostos).isEqualTo(600);
    assertThat(ano.totalDocumentos).isEqualTo(12);
  }

  @Test
  void mesInvalidoERecusado() {
    assertThatThrownBy(() -> apuracao.doMes(tenantId, 2026, 13)).isInstanceOf(IllegalArgumentException.class);
  }

  private NfseInvoiceEntity nota(NfseAmbiente ambiente, String servicos, String iss, boolean retido) {
    NfseInvoiceEntity nota = new NfseInvoiceEntity();
    nota.setId(UUID.randomUUID());
    nota.setTenantId(tenantId);
    nota.setAmbiente(ambiente);
    nota.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    nota.setNumeroNfse("77");
    nota.setSerieRps("1");
    nota.setDataCompetencia(LocalDate.of(2026, 11, 6));
    nota.setDataEmissao(Instant.parse("2026-11-06T15:00:00Z"));
    nota.setValorServicos(new BigDecimal(servicos));
    nota.setValorIss(new BigDecimal(iss));
    nota.setIssRetido(retido);
    nota.setCustomerName("Cliente");
    return nota;
  }
}
