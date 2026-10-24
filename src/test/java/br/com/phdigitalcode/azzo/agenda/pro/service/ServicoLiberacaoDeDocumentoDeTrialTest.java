package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/** SEG-009: o documento do periodo gratuito volta a ficar disponivel para cadastros nunca confirmados. */
class ServicoLiberacaoDeDocumentoDeTrialTest {

  private TenantRepository tenants;

  @BeforeEach
  void preparar() {
    tenants = mock(TenantRepository.class);
  }

  private void verificaLimiteDe(int dias) {
    ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
    verify(tenants).liberarDocumentoDeTrialDeCadastrosNaoConfirmados(limite.capture());
    Instant esperado = Instant.now().minus(Duration.ofDays(dias));
    assertThat(Duration.between(limite.getValue(), esperado).abs()).isLessThan(Duration.ofSeconds(10));
  }

  @Test
  void liberaOQueEstaSemConfirmarHaMaisDeSeteDias() {
    when(tenants.liberarDocumentoDeTrialDeCadastrosNaoConfirmados(any())).thenReturn(3);

    int liberados = new ServicoLiberacaoDeDocumentoDeTrial(tenants, 7).liberar();

    assertThat(liberados).isEqualTo(3);
    verificaLimiteDe(7);
  }

  @Test
  void oPrazoEConfiguravel() {
    new ServicoLiberacaoDeDocumentoDeTrial(tenants, 3).liberar();

    verificaLimiteDe(3);
  }

  /** Zero (ou negativo) desliga: nao pode virar "liberar tudo agora". */
  @Test
  void prazoZeroOuNegativoDesligaALiberacao() {
    assertThat(new ServicoLiberacaoDeDocumentoDeTrial(tenants, 0).liberar()).isZero();
    assertThat(new ServicoLiberacaoDeDocumentoDeTrial(tenants, -5).liberar()).isZero();

    verify(tenants, never()).liberarDocumentoDeTrialDeCadastrosNaoConfirmados(any());
  }

  @Test
  void semNadaParaLiberarDevolveZero() {
    when(tenants.liberarDocumentoDeTrialDeCadastrosNaoConfirmados(any())).thenReturn(0);

    assertThat(new ServicoLiberacaoDeDocumentoDeTrial(tenants, 7).liberar()).isZero();
  }
}
