package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Cobre o {@code tryParse} tolerante de {@code FiscalDocumentStatus}. */
class FiscalDocumentStatusTest {

  @Test
  void todoNomeDoEnumEAceito() {
    for (FiscalDocumentStatus status : FiscalDocumentStatus.values()) {
      assertThat(FiscalDocumentStatus.tryParse(status.name()))
          .as("tryParse(%s)", status.name())
          .contains(status);
    }
  }

  @Test
  void oParsingIgnoraCaixaEEspacoAoRedor() {
    assertThat(FiscalDocumentStatus.tryParse("authorized"))
        .contains(FiscalDocumentStatus.AUTHORIZED);
    assertThat(FiscalDocumentStatus.tryParse("  Cancel_Pending  "))
        .contains(FiscalDocumentStatus.CANCEL_PENDING);
  }

  /** Alias do contrato antigo do provider — precisa continuar virando {@code AUTHORIZED}. */
  @Test
  void oAliasLegadoIssuedViraAuthorized() {
    assertThat(FiscalDocumentStatus.tryParse("ISSUED")).contains(FiscalDocumentStatus.AUTHORIZED);
    assertThat(FiscalDocumentStatus.tryParse(" issued ")).contains(FiscalDocumentStatus.AUTHORIZED);
  }

  @Test
  void issuedNaoEUmValorDoEnum() {
    assertThat(FiscalDocumentStatus.values())
        .extracting(Enum::name)
        .doesNotContain("ISSUED");
  }

  @Test
  void nuloBrancoEDesconhecidoNaoEstouramEViramVazio() {
    assertThat(FiscalDocumentStatus.tryParse(null)).isEmpty();
    assertThat(FiscalDocumentStatus.tryParse("")).isEmpty();
    assertThat(FiscalDocumentStatus.tryParse("   ")).isEmpty();
    assertThat(FiscalDocumentStatus.tryParse("QUALQUER_COISA")).isEmpty();
    assertThat(FiscalDocumentStatus.tryParse("AUTHORIZED_X")).isEmpty();
  }

  @Test
  void oRetornoNuncaENulo() {
    Optional<FiscalDocumentStatus> resultado = FiscalDocumentStatus.tryParse("lixo");

    assertThat(resultado).isNotNull().isEmpty();
  }
}
