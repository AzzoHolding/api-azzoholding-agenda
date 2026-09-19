package br.com.phdigitalcode.azzo.agenda.pro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * A API NAO subia com o endpoint OTLP vazio ("Invalid endpoint, must start with http://") — e a
 * suite nao pegou, porque nenhum teste sobe o contexto inteiro (2026-09-19). O envio de traces
 * fica desligado por padrao e so e ligado, com endereco, no perfil prod.
 */
class TracingPropertiesTest {

  private Object valor(String arquivo, String chave) throws Exception {
    List<PropertySource<?>> fontes =
        new YamlPropertySourceLoader().load(arquivo, new ClassPathResource(arquivo));
    return fontes.stream().map(f -> f.getProperty(chave)).filter(v -> v != null).findFirst().orElse(null);
  }

  @Test
  @DisplayName("fora de producao o envio de traces fica desligado e sem endereco vazio")
  void envioDesligadoPorPadrao() throws Exception {
    assertThat(String.valueOf(valor("application.yml", "management.tracing.export.enabled")))
        .isEqualTo("${TRACING_EXPORT_ENABLED:false}");
    assertThat(valor("application.yml", "management.opentelemetry.tracing.export.otlp.endpoint")).isNull();
  }

  @Test
  @DisplayName("em producao o envio e ligado e aponta para o Tempo")
  void producaoEnviaParaOTempo() throws Exception {
    assertThat(String.valueOf(valor("application-prod.yml", "management.tracing.export.enabled")))
        .isEqualTo("${TRACING_EXPORT_ENABLED:true}");
    assertThat(String.valueOf(valor("application-prod.yml", "management.opentelemetry.tracing.export.otlp.endpoint")))
        .contains("http://tempo:4318/v1/traces");
  }
}
