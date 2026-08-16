package br.com.phdigitalcode.azzo.agenda.pro.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PLACEHOLDER — cobre o unico ponto em que {@code settings} depende de
 * {@code infrastructure/client/AssistantApiClient.java}: {@code invalidarCachePrompt(tenantId)},
 * chamado por {@link br.com.phdigitalcode.azzo.agenda.pro.service.ServicoSettings} depois de
 * atualizar horario de funcionamento, para que o LLM do {@code azzo-assistant-api} receba o horario
 * novo sem esperar o TTL.
 *
 * <p><b>Perde apenas efeito colateral</b> — e exatamente o que o proprio original ja trata como
 * best-effort: a chamada vive dentro de um {@code try/catch} que so loga em {@code warn}, com o
 * comentario "nao falha a operacao principal — o cache expira naturalmente em 3 min". Enquanto este
 * placeholder existir, o assistente pode responder com o horario antigo por ate esse TTL.
 *
 * <p><b>Ao migrar {@code assistantintegration}</b>: apagar esta classe e injetar o REST client real
 * ({@code RestClient} do Spring, nunca {@code WebClient}), preservando o mesmo tratamento de
 * excecao no chamador.
 */
@Service
public class AssistantCacheInvalidationService {

  private static final Logger LOG =
      LoggerFactory.getLogger(AssistantCacheInvalidationService.class);

  public void invalidarCachePrompt(String tenantId) {
    LOG.warn(
        "Cache de prompt do assistente NAO invalidado (modulo assistantintegration pendente de"
            + " migracao) tenantId={}",
        tenantId);
  }
}
