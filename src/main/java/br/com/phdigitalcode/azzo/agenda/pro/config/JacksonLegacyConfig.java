package br.com.phdigitalcode.azzo.agenda.pro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Bean de {@code com.fasterxml.jackson.databind.ObjectMapper} (Jackson 2 classico), consumido
 * por construtor em ~23 classes portadas do Quarkus (auditoria, webhooks de WhatsApp/Telegram,
 * idempotencia fiscal/NFS-e, filtro de rate limit etc.).
 *
 * <p>Spring Boot 4 so autoconfigura {@code tools.jackson.databind.ObjectMapper} (Jackson 3) por
 * default — sem este bean explicito, a injecao dessas classes falha com
 * {@code NoSuchBeanDefinitionException} na subida real da aplicacao (nao aparece em testes
 * unitarios porque eles instanciam {@code new ObjectMapper()} direto, sem passar pelo container).
 * Os dois tipos de ObjectMapper (Jackson 2 e Jackson 3) coexistem sem conflito: sao classes
 * distintas, e este bean nao interfere na serializacao HTTP normal do Spring MVC, que usa Jackson
 * 3 via {@code spring-boot-jackson}.
 */
@Configuration
public class JacksonLegacyConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
