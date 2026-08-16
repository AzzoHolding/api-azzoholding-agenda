package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.net.URI;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Espelha {@code modules/publicbooking/application/PublicBookingUrlService.java}: utilitario
 * autonomo (sem dependencias de outros beans do modulo {@code publicbooking}, ainda nao migrado)
 * usado por {@code ServicoSalonProfile} para montar a URL publica de agendamento do salao.
 *
 * <p>Porte antecipado — o restante de {@code publicbooking} (fluxo de agendamento publico em si)
 * segue nao migrado; trazer apenas este helper agora nao introduz codigo morto porque
 * {@code ServicoSalonProfile} o consome de verdade.
 */
@Service
public class PublicBookingUrlService {

  private static final String UNSET = "__unset__";

  private final String configuredBaseUrl;
  private final String activeProfile;

  public PublicBookingUrlService(
      @Value("${app.public.booking.base-url:http://localhost:5173}") String configuredBaseUrl,
      @Value("${spring.profiles.active:dev}") String activeProfile) {
    this.configuredBaseUrl = configuredBaseUrl;
    this.activeProfile = activeProfile;
  }

  public String buildPublicBookingUrl(String salonSlug) {
    String slug = normalizeSlug(salonSlug);
    String baseUrl = resolveAndValidateBaseUrl();
    return baseUrl + "/agendar/" + slug;
  }

  private String resolveAndValidateBaseUrl() {
    String baseUrl = configuredBaseUrl != null ? configuredBaseUrl.trim() : "";
    if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

    if (baseUrl.isBlank() || UNSET.equalsIgnoreCase(baseUrl)) {
      throw new IllegalStateException("app.public.booking.base-url nao configurada");
    }

    URI uri;
    try {
      uri = URI.create(baseUrl);
    } catch (Exception e) {
      throw new IllegalStateException("app.public.booking.base-url invalida", e);
    }

    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalStateException("app.public.booking.base-url deve usar http/https");
    }
    if (host.isBlank()) {
      throw new IllegalStateException("app.public.booking.base-url deve conter host");
    }

    if (isProductionProfile() && isLocalHost(host)) {
      throw new IllegalStateException("app.public.booking.base-url nao pode apontar para localhost em producao");
    }
    return baseUrl;
  }

  private boolean isProductionProfile() {
    return activeProfile != null && activeProfile.contains("prod");
  }

  private boolean isLocalHost(String host) {
    return "localhost".equals(host)
        || "127.0.0.1".equals(host)
        || "0.0.0.0".equals(host)
        || "::1".equals(host);
  }

  private String normalizeSlug(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("slug do salao obrigatorio");
    }
    return slug.trim();
  }
}
