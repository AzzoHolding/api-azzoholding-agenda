package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Limite por IP da consulta publica de CNPJ.
 *
 * <p>O endpoint nao exige login e chama APIs externas (CNPJ.ws/BrasilAPI): sem limite, vira um
 * proxy aberto e uma forma barata de varrer CNPJs. O {@link RateLimitFilter} so cobre POST em
 * caminhos fixos, por isso este e separado. Em memoria, como aquele — com mais de uma replica o
 * limite vale por instancia.
 */
@Component
public class PublicCnpjRateLimiter {

  /** Teto de IPs guardados: passou disso, recomeca do zero em vez de crescer sem limite. */
  private static final int MAXIMO_DE_IPS = 10_000;

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final int consultas;
  private final Duration janela;

  public PublicCnpjRateLimiter(
      @Value("${app.rate-limit.public-cnpj.max-attempts:10}") int consultas,
      @Value("${app.rate-limit.public-cnpj.window-minutes:1}") int minutos) {
    this.consultas = consultas;
    this.janela = Duration.ofMinutes(minutos);
  }

  /** Consome uma consulta do IP ou lanca 429. */
  public void consumirOuFalhar(HttpServletRequest request) {
    if (buckets.size() > MAXIMO_DE_IPS) buckets.clear();
    Bucket bucket = buckets.computeIfAbsent(ipDe(request), ip -> novoBucket());
    if (!bucket.tryConsume(1)) {
      throw new ApiClientErrorException(
          "Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente novamente.", 429);
    }
  }

  private Bucket novoBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.classic(consultas, Refill.intervally(consultas, janela)))
        .build();
  }

  private String ipDe(HttpServletRequest request) {
    String encaminhado = request.getHeader("X-Forwarded-For");
    if (encaminhado != null && !encaminhado.isBlank()) {
      return encaminhado.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
