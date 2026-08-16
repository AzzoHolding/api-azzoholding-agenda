package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantPaymentDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Espelha {@code modules/settings/application/ServicoTenantPayments.java} — conta de recebimento do
 * salao (Asaas do proprio tenant, F00).
 *
 * <p>A chave de API vai criptografada em repouso ({@link EncryptionService}) e nunca sai em claro:
 * a resposta devolve apenas os quatro ultimos caracteres.
 *
 * <p><b>Assimetria do original, preservada</b>: {@code validarChaveNoAsaas} testa a chave contra o
 * {@link AsaasClient} da <b>plataforma</b>, cuja base URL e fixa ({@code app.asaas.base-url}), e nao
 * contra a base URL correspondente ao {@code ambiente} que o tenant esta salvando. Salvar uma chave
 * de producao com a plataforma apontando para o sandbox (ou vice-versa) falha na validacao. Nao foi
 * "corrigido" aqui — mudaria quando um tenant consegue salvar a configuracao.
 *
 * <p>O {@code WebApplicationException} capturado no original e o erro de <i>resposta HTTP</i> do
 * REST client; o equivalente do {@link org.springframework.web.client.RestClient} e
 * {@link RestClientResponseException}. Falha de conexao/timeout continua propagando sem virar
 * {@code IllegalArgumentException}, exatamente como no Quarkus.
 */
@Service
public class ServicoTenantPayments {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final TenantPaymentSettingsRepository tenantPaymentSettingsRepository;
  private final EncryptionService encryptionService;
  private final AsaasClient asaasClient;
  private final String publicBaseUrl;

  public ServicoTenantPayments(
      ContextoTenant contextoTenant,
      AuditService auditService,
      TenantPaymentSettingsRepository tenantPaymentSettingsRepository,
      EncryptionService encryptionService,
      AsaasClient asaasClient,
      @Value("${app.public-base-url:https://localhost:8443}") String publicBaseUrl) {
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.tenantPaymentSettingsRepository = tenantPaymentSettingsRepository;
    this.encryptionService = encryptionService;
    this.asaasClient = asaasClient;
    this.publicBaseUrl = publicBaseUrl;
  }

  @Transactional
  public TenantPaymentDtos.ConfigResponse obterConfiguracaoAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toConfigResponse(findByTenantIdOrCreate(tenantId));
  }

  @Transactional
  public TenantPaymentDtos.ConfigResponse atualizar(TenantPaymentDtos.UpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantPaymentSettings config = findByTenantIdOrCreate(tenantId);

    String apiKey = trimToNull(request.apiKey);
    if (apiKey == null) {
      throw new IllegalArgumentException("Chave de API do Asaas e obrigatoria.");
    }
    validarChaveNoAsaas(apiKey);

    config.setApiKeyEnc(encryptionService.encrypt(apiKey));
    config.setAmbiente(request.ambiente);
    config.setAtivo(true);
    ensureWebhookToken(config);

    TenantPaymentDtos.ConfigResponse response = toConfigResponse(config);
    registrarAuditoria(
        tenantId,
        "PAYMENT_CONFIG_UPDATE",
        Map.of("ambiente", config.getAmbiente(), "ativo", config.isAtivo()),
        true);
    return response;
  }

  private void validarChaveNoAsaas(String apiKey) {
    try {
      asaasClient.listPayments(apiKey, null, 1, 0);
    } catch (RestClientResponseException e) {
      registrarAuditoria(
          contextoTenant.obterTenantIdOuFalhar(),
          "PAYMENT_CONFIG_VALIDATION",
          Map.of("success", false, "status", e.getStatusCode().value()),
          false);
      throw new IllegalArgumentException(
          "Nao foi possivel validar a chave de API informada junto ao Asaas. Revise a chave e o"
              + " ambiente selecionado.",
          e);
    }
  }

  /**
   * Equivalente ao {@code findByTenantIdOrCreate} do repositorio Panache. {@code saveAndFlush}
   * porque o {@code persist()} do Panache emitia o INSERT na hora (armadilha 2).
   */
  private TenantPaymentSettings findByTenantIdOrCreate(UUID tenantId) {
    return tenantPaymentSettingsRepository
        .findByTenantId(tenantId)
        .orElseGet(
            () -> {
              TenantPaymentSettings created = new TenantPaymentSettings();
              created.setTenantId(tenantId);
              return tenantPaymentSettingsRepository.saveAndFlush(created);
            });
  }

  private TenantPaymentDtos.ConfigResponse toConfigResponse(TenantPaymentSettings config) {
    TenantPaymentDtos.ConfigResponse response = new TenantPaymentDtos.ConfigResponse();
    response.provider = config.getProvider();
    response.ambiente = config.getAmbiente();
    response.ativo = config.isAtivo();
    response.apiKeyMascarada = maskApiKey(decrypt(config.getApiKeyEnc()));
    response.webhookPath = buildWebhookPath(config.getWebhookToken());
    return response;
  }

  private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) return null;
    int visible = Math.min(4, apiKey.length());
    return "****" + apiKey.substring(apiKey.length() - visible);
  }

  private void ensureWebhookToken(TenantPaymentSettings config) {
    if (trimToNull(config.getWebhookToken()) != null) return;
    config.setWebhookToken(generateWebhookToken());
  }

  private String generateWebhookToken() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private String buildWebhookPath(String webhookToken) {
    if (trimToNull(webhookToken) == null) return null;
    String base = trimToNull(publicBaseUrl);
    if (base == null) return null;
    return stripTrailingSlash(base) + "/webhook/asaas/tenant/" + webhookToken;
  }

  private void registrarAuditoria(UUID tenantId, String acao, Object depois, boolean sucesso) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "TENANT";
    command.entityId = tenantId != null ? tenantId.toString() : null;
    command.after = depois;
    command.sourceChannel = AuditConstants.SourceChannel.API;
    if (sucesso) {
      auditService.recordSuccess(command);
    } else {
      auditService.recordError(command);
    }
  }

  private String decrypt(String encrypted) {
    String value = trimToNull(encrypted);
    if (value == null) return null;
    return trimToNull(encryptionService.decrypt(value));
  }

  private String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }
}
