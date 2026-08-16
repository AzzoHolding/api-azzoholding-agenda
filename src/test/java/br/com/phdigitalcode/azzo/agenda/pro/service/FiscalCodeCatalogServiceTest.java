package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCodeCatalogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCodeCatalogRepository;

/**
 * Cobre {@code modules/fiscal/application/FiscalCodeCatalogService.java}.
 *
 * <p>⚠️ {@code hasAnyActiveByType} e metodo {@code default} da interface do repositorio, e o mock do
 * Mockito <b>nao executa o corpo do {@code default}</b> (armadilha 7): estubar
 * {@code contarAtivosPorTipo} nao teria efeito nenhum, porque a chamada interna nunca acontece. Por
 * isso o {@code default} e estubado <b>diretamente</b>, e a normalizacao que ele faz por dentro fica
 * coberta pelo teste do proprio repositorio, nao aqui.
 */
@ExtendWith(MockitoExtension.class)
class FiscalCodeCatalogServiceTest {

  @Mock private FiscalCodeCatalogRepository fiscalCodeCatalogRepository;

  private FiscalCodeCatalogService service;

  @BeforeEach
  void setUp() {
    service = new FiscalCodeCatalogService(fiscalCodeCatalogRepository);
  }

  @Test
  void normalizaOTipoParaMaiusculaAntesDeConsultar() {
    when(fiscalCodeCatalogRepository.findActiveByTypeAndValueAtDate(
            any(), any(), any(LocalDate.class)))
        .thenReturn(Optional.of(new FiscalCodeCatalogEntity()));

    assertThat(service.existsActive("  cfop  ", "5102", LocalDate.of(2026, 3, 10))).isTrue();

    ArgumentCaptor<String> tipo = ArgumentCaptor.forClass(String.class);
    verify(fiscalCodeCatalogRepository)
        .findActiveByTypeAndValueAtDate(
            tipo.capture(), eq("5102"), eq(LocalDate.of(2026, 3, 10)));
    assertThat(tipo.getValue()).isEqualTo("CFOP");
  }

  /**
   * ⚠️ Assimetria do original: o <b>valor</b> so recebe {@code trim}, sem mudanca de caixa —
   * diferente do tipo. Travado para que ninguem "conserte" sem decidir a mudanca de contrato.
   */
  @Test
  void oValorDoCodigoNaoEConvertidoParaMaiuscula() {
    when(fiscalCodeCatalogRepository.findActiveByTypeAndValueAtDate(any(), any(), any()))
        .thenReturn(Optional.empty());

    service.existsActive("NCM", "  ab12  ", LocalDate.of(2026, 3, 10));

    verify(fiscalCodeCatalogRepository)
        .findActiveByTypeAndValueAtDate(eq("NCM"), eq("ab12"), any(LocalDate.class));
  }

  @Test
  void dataNulaCaiParaHoje() {
    when(fiscalCodeCatalogRepository.findActiveByTypeAndValueAtDate(any(), any(), any()))
        .thenReturn(Optional.empty());

    // Janela de tolerancia em vez de igualdade com LocalDate.now(): sem isso o teste falha se a
    // execucao atravessar a virada do dia (armadilha 12).
    LocalDate antes = LocalDate.now();
    service.existsActive("NCM", "12345678", null);
    LocalDate depois = LocalDate.now();

    ArgumentCaptor<LocalDate> data = ArgumentCaptor.forClass(LocalDate.class);
    verify(fiscalCodeCatalogRepository)
        .findActiveByTypeAndValueAtDate(eq("NCM"), eq("12345678"), data.capture());
    assertThat(data.getValue()).isBetween(antes, depois);
  }

  @Test
  void argumentoEmBrancoDevolveFalseSemTocarNoRepositorio() {
    assertThat(service.existsActive(null, "5102", null)).isFalse();
    assertThat(service.existsActive("  ", "5102", null)).isFalse();
    assertThat(service.existsActive("CFOP", null, null)).isFalse();
    assertThat(service.existsActive("CFOP", "  ", null)).isFalse();
    assertThat(service.hasCatalogForType(null)).isFalse();
    assertThat(service.hasCatalogForType("  ")).isFalse();

    verify(fiscalCodeCatalogRepository, never())
        .findActiveByTypeAndValueAtDate(any(), any(), any());
    verify(fiscalCodeCatalogRepository, never()).hasAnyActiveByType(any());
  }

  @Test
  void hasCatalogForTypeNormalizaOTipoERepassaARespostaDoRepositorio() {
    when(fiscalCodeCatalogRepository.hasAnyActiveByType("CST")).thenReturn(true);

    assertThat(service.hasCatalogForType(" cst ")).isTrue();

    verify(fiscalCodeCatalogRepository).hasAnyActiveByType("CST");
  }

  @Test
  void hasCatalogForTypeEFalseQuandoNaoHaLinhaAtivaDoTipo() {
    when(fiscalCodeCatalogRepository.hasAnyActiveByType("NCM")).thenReturn(false);

    assertThat(service.hasCatalogForType("NCM")).isFalse();
  }
}
