package br.com.phdigitalcode.azzo.agenda.pro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;

/**
 * Campo que a API nao conhece deixa rastro no log (jornada de usuario de 2026-09-17).
 *
 * <p>Um corpo com o nome do campo errado — {@code posMaxDiscountPercent} no lugar de
 * {@code maxDiscountPercent} — era ignorado em silencio: a chamada voltava 200 e nada mudava. O
 * campo continua IGNORADO, de proposito: recusar (FAIL_ON_UNKNOWN_PROPERTIES) quebraria qualquer
 * tela que manda um campo a mais, e as zonas do front nao foram auditadas para isso. O que muda e
 * que o engano aparece no log com o nome do campo e o DTO — sem o valor, que pode ser dado pessoal.
 * As edicoes em que um corpo sem campo reconhecido seria um erro recusam o pedido vazio no servico.
 */
@Configuration
public class CamposDesconhecidosConfig {

  private static final Logger LOG = LoggerFactory.getLogger(CamposDesconhecidosConfig.class);

  @Bean
  public JsonMapperBuilderCustomizer registrarCamposDesconhecidos() {
    return builder -> builder.addHandler(new RegistraCampoDesconhecido());
  }

  static final class RegistraCampoDesconhecido extends DeserializationProblemHandler {

    @Override
    public boolean handleUnknownProperty(
        DeserializationContext ctxt,
        JsonParser p,
        ValueDeserializer<?> deserializer,
        Object beanOrClass,
        String propertyName)
        throws JacksonException {
      String alvo =
          beanOrClass instanceof Class<?> classe
              ? classe.getSimpleName()
              : beanOrClass == null ? "?" : beanOrClass.getClass().getSimpleName();
      LOG.warn("Campo desconhecido ignorado no corpo da requisicao: campo={} dto={}", propertyName, alvo);
      p.skipChildren();
      return true;
    }
  }
}
