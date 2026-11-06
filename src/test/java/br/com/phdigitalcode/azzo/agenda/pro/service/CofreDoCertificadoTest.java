package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/** O certificado so abre com a senha do dono e a pimenta do servidor juntas. */
class CofreDoCertificadoTest {

  private static final String PIMENTA = Base64.getEncoder().encodeToString("p".repeat(32).getBytes(StandardCharsets.UTF_8));
  private static final String OUTRA_PIMENTA = Base64.getEncoder().encodeToString("q".repeat(32).getBytes(StandardCharsets.UTF_8));
  private static final byte[] PFX = "conteudo-do-pfx".getBytes(StandardCharsets.UTF_8);

  private final CofreDoCertificado cofre = new CofreDoCertificado(PIMENTA);

  @Test
  void abreComASenhaCerta() {
    CofreDoCertificado.Trancado trancado = cofre.trancar(PFX, "senha-do-dono");

    assertThat(cofre.abrir(trancado.conteudo(), trancado.sal(), "senha-do-dono")).isEqualTo(PFX);
  }

  @Test
  void naoGuardaOPfxEmClaro() {
    CofreDoCertificado.Trancado trancado = cofre.trancar(PFX, "senha-do-dono");

    assertThat(new String(Base64.getDecoder().decode(trancado.conteudo()), StandardCharsets.ISO_8859_1))
        .doesNotContain("conteudo-do-pfx");
  }

  @Test
  void senhaErradaNaoAbre() {
    CofreDoCertificado.Trancado trancado = cofre.trancar(PFX, "senha-do-dono");

    assertThatThrownBy(() -> cofre.abrir(trancado.conteudo(), trancado.sal(), "outra-senha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID);
  }

  /** Banco e senha roubados nao bastam: sem a pimenta do servidor, nao abre. */
  @Test
  void semAMesmaPimentaNaoAbre() {
    CofreDoCertificado.Trancado trancado = cofre.trancar(PFX, "senha-do-dono");
    CofreDoCertificado outroServidor = new CofreDoCertificado(OUTRA_PIMENTA);

    assertThatThrownBy(() -> outroServidor.abrir(trancado.conteudo(), trancado.sal(), "senha-do-dono"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID);
  }

  @Test
  void cadaEnvioTemSalProprio() {
    CofreDoCertificado.Trancado primeiro = cofre.trancar(PFX, "senha-do-dono");
    CofreDoCertificado.Trancado segundo = cofre.trancar(PFX, "senha-do-dono");

    assertThat(primeiro.sal()).isNotEqualTo(segundo.sal());
    assertThat(primeiro.conteudo()).isNotEqualTo(segundo.conteudo());
  }

  @Test
  void semPimentaConfiguradaRecusaComMensagemPropria() {
    CofreDoCertificado semPimenta = new CofreDoCertificado("");

    assertThatThrownBy(() -> semPimenta.trancar(PFX, "senha-do-dono"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(CofreDoCertificado.ERR_PIMENTA_AUSENTE);
  }

  @Test
  void pimentaCurtaOuInvalidaContaComoAusente() {
    assertThat(CofreDoCertificado.lerPimenta(Base64.getEncoder().encodeToString(new byte[16]))).isNull();
    assertThat(CofreDoCertificado.lerPimenta("nao-e-base64!!")).isNull();
    assertThat(CofreDoCertificado.lerPimenta(PIMENTA)).hasSize(32);
  }
}
