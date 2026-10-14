package br.com.phdigitalcode.azzo.agenda.pro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Pentest 14/10/2026: SEG-019 (validacao dependia do nome "prod") e SEG-017 (SameSite=None). */
class StartupSecurityValidatorPerfilTest {

  @Test
  void soPerfisDeDesenvolvimentoToleramPadraoInseguro() {
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("dev")).isTrue();
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("test")).isTrue();
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("local,debug")).isTrue();
    // Antes: so "contem prod" era producao — estes passavam com segredo padrao.
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("producao")).isFalse();
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("staging")).isFalse();
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("")).isFalse();
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento(null)).isFalse();
    assertThat(StartupSecurityValidator.ehAmbienteDeDesenvolvimento("prod")).isFalse();
  }

  private static StartupSecurityValidator valido(String perfil) {
    StartupSecurityValidator v = new StartupSecurityValidator();
    ReflectionTestUtils.setField(v, "encryptionKey", "chave-propria-de-32-caracteres!!");
    ReflectionTestUtils.setField(v, "internalApiKey", "chave-interna-propria");
    ReflectionTestUtils.setField(v, "lgpdContactEmail", "lgpd@azzo.app");
    ReflectionTestUtils.setField(v, "metaAppSecret", "segredo");
    ReflectionTestUtils.setField(v, "agendaDebugEnabled", false);
    ReflectionTestUtils.setField(v, "activeProfile", perfil);
    ReflectionTestUtils.setField(v, "cookieSameSite", "STRICT");
    ReflectionTestUtils.setField(v, "minioEnabled", true);
    ReflectionTestUtils.setField(v, "minioAccessKey", "minioadmin");
    ReflectionTestUtils.setField(v, "minioSecretKey", "minioadmin123");
    return v;
  }

  @Test
  void perfilStagingComChavePadraoNaoSobe() {
    StartupSecurityValidator v = valido("staging");
    ReflectionTestUtils.setField(v, "internalApiKey", "changeme-dev");
    assertThatThrownBy(v::onReady).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sameSiteNoneEmProducaoNaoSobe() {
    StartupSecurityValidator v = valido("prod");
    ReflectionTestUtils.setField(v, "cookieSameSite", "None");
    assertThatThrownBy(v::onReady).isInstanceOf(IllegalStateException.class).hasMessageContaining("CSRF");
  }

  @Test
  void minioPadraoEmProducaoSoAlertaPorEnquanto() {
    // Producao ainda usava minioadmin em 14/10/2026: recusar subir derrubaria a API.
    valido("prod").onReady();
  }
}
