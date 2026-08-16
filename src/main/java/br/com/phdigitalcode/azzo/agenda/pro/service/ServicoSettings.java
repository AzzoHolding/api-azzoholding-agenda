package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SettingsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantCacheInvalidationService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/settings/application/ServicoSettings.java} — camada fina que resolve o
 * tenant do contexto e delega ao {@link TenantOperationalSettingsService}.
 *
 * <p>Sem {@code @Transactional} aqui, como no original: cada metodo do service de dominio abre a
 * sua propria transacao.
 *
 * <p>A invalidacao do cache do assistente e best-effort nos dois pontos em que aparece (horario
 * legado e tabela relacional) — falha nela nunca derruba a atualizacao. Enquanto
 * {@code assistantintegration} nao for migrado, quem responde e o placeholder
 * {@link AssistantCacheInvalidationService}.
 */
@Service
public class ServicoSettings {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoSettings.class);

  private final ContextoTenant contextoTenant;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;
  private final AssistantCacheInvalidationService assistantCacheInvalidationService;

  public ServicoSettings(
      ContextoTenant contextoTenant,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      AssistantCacheInvalidationService assistantCacheInvalidationService) {
    this.contextoTenant = contextoTenant;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.assistantCacheInvalidationService = assistantCacheInvalidationService;
  }

  public SettingsDtos.SettingsResponse obter() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getOrCreateSettings(tenantId);
  }

  public SettingsDtos.NotificationSettings atualizarNotifications(
      SettingsDtos.NotificationSettings request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateNotifications(tenantId, request);
  }

  public SettingsDtos.ReactivationSettings atualizarReactivation(
      SettingsDtos.ReactivationSettings request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateReactivation(tenantId, request);
  }

  public Map<String, SettingsDtos.BusinessHoursDay> atualizarBusinessHours(
      Map<String, SettingsDtos.BusinessHoursDay> request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Map<String, SettingsDtos.BusinessHoursDay> updated =
        tenantOperationalSettingsService.updateBusinessHours(tenantId, request);

    // Invalida o cache do system prompt do assistente para que o LLM receba os horarios
    // atualizados imediatamente, sem esperar o TTL.
    try {
      assistantCacheInvalidationService.invalidarCachePrompt(tenantId.toString());
      LOG.info("[Settings] Cache do assistente invalidado para tenant={}", tenantId);
    } catch (Exception e) {
      // Nao falha a operacao principal — o cache expira naturalmente em 3 min
      LOG.warn(
          "[Settings] Falha ao invalidar cache do assistente (tenant={}): {}",
          tenantId,
          e.getMessage());
    }

    return updated;
  }

  // Business Hours — tabela relacional

  public List<SettingsDtos.BusinessHoursItemResponse> obterBusinessHoursTabela() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getBusinessHoursFromTable(tenantId);
  }

  public List<SettingsDtos.BusinessHoursItemResponse> atualizarBusinessHoursTabela(
      List<SettingsDtos.BusinessHoursItemRequest> request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<SettingsDtos.BusinessHoursItemResponse> updated =
        tenantOperationalSettingsService.updateBusinessHoursInTable(tenantId, request);
    try {
      assistantCacheInvalidationService.invalidarCachePrompt(tenantId.toString());
    } catch (Exception e) {
      LOG.warn(
          "[Settings] Falha ao invalidar cache do assistente (tenant={}): {}",
          tenantId,
          e.getMessage());
    }
    return updated;
  }

  // LGPD

  public SettingsDtos.LgpdContactResponse obterContatoLgpd() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getLgpdContact(tenantId);
  }

  public SettingsDtos.LgpdContactResponse atualizarContatoLgpd(
      SettingsDtos.LgpdContactRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateLgpdContact(tenantId, request);
  }

  // Feature Flags

  public SettingsDtos.TenantFeatureFlagsResponse obterFeatureFlags() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getFeatureFlags(tenantId);
  }

  public SettingsDtos.TenantFeatureFlagsResponse atualizarFeatureFlags(
      SettingsDtos.TenantFeatureFlagsRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateFeatureFlags(tenantId, request);
  }

  // Email Templates

  public SettingsDtos.EmailTemplateListResponse listarEmailTemplates() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.listEmailTemplates(tenantId);
  }

  public SettingsDtos.EmailTemplateResponse obterEmailTemplate(String type) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getEmailTemplate(tenantId, type);
  }

  public SettingsDtos.EmailTemplateResponse atualizarEmailTemplate(
      String type, SettingsDtos.EmailTemplateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateEmailTemplate(tenantId, type, request);
  }

  // Politica de Cancelamento

  public SettingsDtos.CancellationPolicyResponse obterCancellationPolicy() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getCancellationPolicy(tenantId);
  }

  public SettingsDtos.CancellationPolicyResponse atualizarCancellationPolicy(
      SettingsDtos.CancellationPolicyRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateCancellationPolicy(tenantId, request);
  }

  // Regua de lembretes — F03

  public SettingsDtos.ReminderSettingsResponse obterReminderSettings() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.getReminderSettings(tenantId);
  }

  public SettingsDtos.ReminderSettingsResponse atualizarReminderSettings(
      SettingsDtos.ReminderSettingsRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return tenantOperationalSettingsService.updateReminderSettings(tenantId, request);
  }
}
