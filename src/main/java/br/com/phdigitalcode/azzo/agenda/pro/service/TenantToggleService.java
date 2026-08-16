package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ToggleRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ToggleResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/tenant/application/ServicoTenantToggle.java}. */
@Service
public class TenantToggleService {

  private static final Logger LOG = LoggerFactory.getLogger(TenantToggleService.class);

  private static final Set<String> WHATSAPP_KEYS = Set.of(
      "WHATSAPP_ENABLED",
      "WHATSAPP_USAGE_PROFILE",
      "WHATSAPP_CAN_SCHEDULE",
      "WHATSAPP_CAN_CANCEL",
      "WHATSAPP_CAN_RESCHEDULE");

  private static final Set<String> VALID_USAGE_PROFILES = Set.of("REACTIVE_ONLY", "NOTIFICATIONS", "COMPLETE");

  private final ContextoTenant contextoTenant;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;

  public TenantToggleService(ContextoTenant contextoTenant, TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository) {
    this.contextoTenant = contextoTenant;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
  }

  @Transactional
  public ToggleResponse aplicar(ToggleRequest request) {
    String key = request.key.toUpperCase().trim();
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    if (WHATSAPP_KEYS.contains(key)) {
      return aplicarWhatsApp(tenantId, key, request.value);
    }

    throw new ApiClientErrorException("Toggle desconhecido: " + key, 400);
  }

  private ToggleResponse aplicarWhatsApp(UUID tenantId, String key, Object value) {
    TenantWhatsAppConfig config = findOrCreate(tenantId);

    switch (key) {
      case "WHATSAPP_ENABLED" -> {
        boolean val = asBoolean(key, value);
        config.setWhatsappEnabled(val);
        tenantWhatsAppConfigRepository.save(config);
        LOG.info("toggle whatsapp_enabled={} tenant={}", val, tenantId);
        return new ToggleResponse(key, val);
      }
      case "WHATSAPP_USAGE_PROFILE" -> {
        String val = asString(key, value).toUpperCase();
        if (!VALID_USAGE_PROFILES.contains(val)) {
          throw new ApiClientErrorException(
              "Perfil invalido: " + val + ". Valores aceitos: REACTIVE_ONLY, NOTIFICATIONS, COMPLETE", 400);
        }
        config.setWhatsappUsageProfile(val);
        tenantWhatsAppConfigRepository.save(config);
        LOG.info("toggle whatsapp_usage_profile={} tenant={}", val, tenantId);
        return new ToggleResponse(key, val);
      }
      case "WHATSAPP_CAN_SCHEDULE" -> {
        boolean val = asBoolean(key, value);
        config.setCanSchedule(val);
        tenantWhatsAppConfigRepository.save(config);
        return new ToggleResponse(key, val);
      }
      case "WHATSAPP_CAN_CANCEL" -> {
        boolean val = asBoolean(key, value);
        config.setCanCancel(val);
        tenantWhatsAppConfigRepository.save(config);
        return new ToggleResponse(key, val);
      }
      case "WHATSAPP_CAN_RESCHEDULE" -> {
        boolean val = asBoolean(key, value);
        config.setCanReschedule(val);
        tenantWhatsAppConfigRepository.save(config);
        return new ToggleResponse(key, val);
      }
      default -> throw new ApiClientErrorException("Toggle nao suportado: " + key, 400);
    }
  }

  private TenantWhatsAppConfig findOrCreate(UUID tenantId) {
    return tenantWhatsAppConfigRepository.findById(tenantId).orElseGet(() -> {
      TenantWhatsAppConfig created = new TenantWhatsAppConfig();
      created.setTenantId(tenantId);
      created.setWhatsappAccessTokenEnc("");
      created.setWhatsappEnabled(false);
      created.setCanSchedule(true);
      created.setCanCancel(true);
      created.setCanReschedule(true);
      return tenantWhatsAppConfigRepository.save(created);
    });
  }

  private boolean asBoolean(String key, Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof String s) return Boolean.parseBoolean(s.trim());
    throw new ApiClientErrorException("Valor invalido para " + key + ": esperado boolean", 400);
  }

  private String asString(String key, Object value) {
    if (value instanceof String s && !s.isBlank()) return s.trim();
    throw new ApiClientErrorException("Valor invalido para " + key + ": esperado string", 400);
  }
}
