package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SettingsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantLoyaltyDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantPaymentDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoSettings;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantLoyalty;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantPayments;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/settings/api/SettingsResource.java} — {@code /api/v1/settings}, 21
 * endpoints, verbo por verbo.
 *
 * <p>Papeis conferidos contra o original: leitura geral e {@code business-hours/table} aceitam
 * {@code OWNER} e {@code PROFESSIONAL}; {@code cancellation-policy} (GET e PUT) aceita
 * {@code OWNER} e {@code ADMIN}; todo o resto e {@code OWNER}. Nao ha nenhum {@code PATCH} aqui —
 * todas as escritas sao {@code PUT}, inclusive as parciais.
 */
@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('OWNER')")
public class SettingsController {

  private final ServicoSettings servicoSettings;
  private final ServicoTenantPayments servicoTenantPayments;
  private final ServicoTenantLoyalty servicoTenantLoyalty;

  public SettingsController(
      ServicoSettings servicoSettings,
      ServicoTenantPayments servicoTenantPayments,
      ServicoTenantLoyalty servicoTenantLoyalty) {
    this.servicoSettings = servicoSettings;
    this.servicoTenantPayments = servicoTenantPayments;
    this.servicoTenantLoyalty = servicoTenantLoyalty;
  }

  // ---- Leitura geral (OWNER e PROFESSIONAL podem ler) ----

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
  public SettingsDtos.SettingsResponse obter() {
    return servicoSettings.obter();
  }

  // ---- Configuracoes do tenant (somente OWNER pode alterar) ----

  @PutMapping("/notifications")
  public SettingsDtos.NotificationSettings atualizarNotifications(
      @Valid @RequestBody SettingsDtos.NotificationSettings request) {
    return servicoSettings.atualizarNotifications(request);
  }

  @PutMapping("/reactivation")
  public SettingsDtos.ReactivationSettings atualizarReactivation(
      @Valid @RequestBody SettingsDtos.ReactivationSettings request) {
    return servicoSettings.atualizarReactivation(request);
  }

  /** Sem {@code @Valid} no original — mantido assim. */
  @PutMapping("/business-hours")
  public Map<String, SettingsDtos.BusinessHoursDay> atualizarBusinessHours(
      @RequestBody Map<String, SettingsDtos.BusinessHoursDay> request) {
    return servicoSettings.atualizarBusinessHours(request);
  }

  // ---- Horarios de funcionamento — tabela relacional ----

  @GetMapping("/business-hours/table")
  @PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
  public List<SettingsDtos.BusinessHoursItemResponse> obterBusinessHoursTabela() {
    return servicoSettings.obterBusinessHoursTabela();
  }

  /**
   * Sem {@code @Valid} no original, apesar de {@code BusinessHoursItemRequest} ter
   * {@code @NotBlank}/{@code @Pattern} — as anotacoes so tem efeito quando este payload chega pelo
   * {@code PUT /business-hours} (via mapa). Assimetria preservada: validar aqui passaria a rejeitar
   * requisicoes que hoje o backend aceita e normaliza sozinho.
   */
  @PutMapping("/business-hours/table")
  public List<SettingsDtos.BusinessHoursItemResponse> atualizarBusinessHoursTabela(
      @RequestBody List<SettingsDtos.BusinessHoursItemRequest> request) {
    return servicoSettings.atualizarBusinessHoursTabela(request);
  }

  // ---- LGPD ----

  @GetMapping("/lgpd")
  public SettingsDtos.LgpdContactResponse obterContatoLgpd() {
    return servicoSettings.obterContatoLgpd();
  }

  @PutMapping("/lgpd")
  public SettingsDtos.LgpdContactResponse atualizarContatoLgpd(
      @Valid @RequestBody SettingsDtos.LgpdContactRequest request) {
    return servicoSettings.atualizarContatoLgpd(request);
  }

  // ---- Feature Flags ----

  @GetMapping("/features")
  public SettingsDtos.TenantFeatureFlagsResponse obterFeatureFlags() {
    return servicoSettings.obterFeatureFlags();
  }

  @PutMapping("/features")
  public SettingsDtos.TenantFeatureFlagsResponse atualizarFeatureFlags(
      @Valid @RequestBody SettingsDtos.TenantFeatureFlagsRequest request) {
    return servicoSettings.atualizarFeatureFlags(request);
  }

  // ---- Templates de E-mail ----

  @GetMapping("/email-templates")
  public SettingsDtos.EmailTemplateListResponse listarTemplates() {
    return servicoSettings.listarEmailTemplates();
  }

  @GetMapping("/email-templates/{type}")
  public SettingsDtos.EmailTemplateResponse obterTemplate(@PathVariable String type) {
    return servicoSettings.obterEmailTemplate(type);
  }

  @PutMapping("/email-templates/{type}")
  public SettingsDtos.EmailTemplateResponse atualizarTemplate(
      @PathVariable String type, @Valid @RequestBody SettingsDtos.EmailTemplateRequest request) {
    return servicoSettings.atualizarEmailTemplate(type, request);
  }

  // ---- Politica de Cancelamento ----

  @GetMapping("/cancellation-policy")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public SettingsDtos.CancellationPolicyResponse obterCancellationPolicy() {
    return servicoSettings.obterCancellationPolicy();
  }

  @PutMapping("/cancellation-policy")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public SettingsDtos.CancellationPolicyResponse atualizarCancellationPolicy(
      @Valid @RequestBody SettingsDtos.CancellationPolicyRequest request) {
    return servicoSettings.atualizarCancellationPolicy(request);
  }

  // ---- Regua de lembretes de agendamento — F03 ----

  @GetMapping("/reminders")
  public SettingsDtos.ReminderSettingsResponse obterReminderSettings() {
    return servicoSettings.obterReminderSettings();
  }

  @PutMapping("/reminders")
  public SettingsDtos.ReminderSettingsResponse atualizarReminderSettings(
      @Valid @RequestBody SettingsDtos.ReminderSettingsRequest request) {
    return servicoSettings.atualizarReminderSettings(request);
  }

  // ---- Conta de recebimento do salao (Asaas do tenant) — F00 ----

  @GetMapping("/payments")
  public TenantPaymentDtos.ConfigResponse obterPaymentConfig() {
    return servicoTenantPayments.obterConfiguracaoAtual();
  }

  @PutMapping("/payments")
  public TenantPaymentDtos.ConfigResponse atualizarPaymentConfig(
      @Valid @RequestBody TenantPaymentDtos.UpdateRequest request) {
    return servicoTenantPayments.atualizar(request);
  }

  // ---- Programa de fidelidade — F08 ----

  @GetMapping("/loyalty")
  public TenantLoyaltyDtos.ConfigResponse obterLoyaltyConfig() {
    return servicoTenantLoyalty.obterConfiguracaoAtual();
  }

  @PutMapping("/loyalty")
  public TenantLoyaltyDtos.ConfigResponse atualizarLoyaltyConfig(
      @Valid @RequestBody TenantLoyaltyDtos.UpdateRequest request) {
    return servicoTenantLoyalty.atualizar(request);
  }
}
