package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantPaymentDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AsaasClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/** Cobre {@link ServicoTenantPayments} — conta de recebimento do salao (F00). */
@ExtendWith(MockitoExtension.class)
class ServicoTenantPaymentsTest {

  private final UUID tenantId = UUID.randomUUID();

  @Mock private ContextoTenant contextoTenant;
  @Mock private AuditService auditService;
  @Mock private TenantPaymentSettingsRepository repository;
  @Mock private EncryptionService encryptionService;
  @Mock private AsaasClient asaasClient;

  private ServicoTenantPayments service;

  @BeforeEach
  void setUp() {
    service =
        new ServicoTenantPayments(
            contextoTenant,
            auditService,
            repository,
            encryptionService,
            asaasClient,
            "https://app.azzoholding.com.br/");
  }

  @Test
  @DisplayName("chave em branco e recusada antes de qualquer chamada ao Asaas")
  void chaveEmBrancoERecusada() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(config()));
    TenantPaymentDtos.UpdateRequest request = new TenantPaymentDtos.UpdateRequest();
    request.apiKey = "   ";
    request.ambiente = "SANDBOX";

    assertThatThrownBy(() -> service.atualizar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Chave de API do Asaas e obrigatoria.");

    verify(asaasClient, never()).listPayments(any(), any(), any(), any());
  }

  @Test
  @DisplayName("chave recusada pelo Asaas vira IllegalArgumentException e auditoria de erro")
  void chaveRecusadaPeloAsaas() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(config()));
    when(asaasClient.listPayments("chave-ruim", null, 1, 0))
        .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null));
    TenantPaymentDtos.UpdateRequest request = new TenantPaymentDtos.UpdateRequest();
    request.apiKey = "chave-ruim";
    request.ambiente = "PRODUCAO";

    assertThatThrownBy(() -> service.atualizar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nao foi possivel validar a chave de API informada junto ao Asaas");

    verify(auditService).recordError(any(AuditEventCommand.class));
    verify(encryptionService, never()).encrypt(any());
  }

  @Test
  @DisplayName("chave valida e criptografada, ativa a config e gera o token de webhook")
  void chaveValidaEArmazenadaCriptografada() {
    TenantPaymentSettings config = config();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
    when(encryptionService.encrypt("chave-boa-1234")).thenReturn("cifrada");
    when(encryptionService.decrypt("cifrada")).thenReturn("chave-boa-1234");
    TenantPaymentDtos.UpdateRequest request = new TenantPaymentDtos.UpdateRequest();
    request.apiKey = "chave-boa-1234";
    request.ambiente = "PRODUCAO";

    TenantPaymentDtos.ConfigResponse response = service.atualizar(request);

    assertThat(config.getApiKeyEnc()).isEqualTo("cifrada");
    assertThat(config.isAtivo()).isTrue();
    assertThat(config.getAmbiente()).isEqualTo("PRODUCAO");
    assertThat(config.getWebhookToken()).isNotBlank().hasSize(48); // 24 bytes em hex
    assertThat(response.apiKeyMascarada).isEqualTo("****1234");
    assertThat(response.webhookPath)
        .isEqualTo(
            "https://app.azzoholding.com.br/webhook/asaas/tenant/" + config.getWebhookToken());
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  @DisplayName("token de webhook ja existente nao e regerado")
  void tokenExistenteNaoERegerado() {
    TenantPaymentSettings config = config();
    config.setWebhookToken("token-antigo");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
    when(encryptionService.encrypt(any())).thenReturn("cifrada");
    when(encryptionService.decrypt("cifrada")).thenReturn("chave");
    TenantPaymentDtos.UpdateRequest request = new TenantPaymentDtos.UpdateRequest();
    request.apiKey = "chave";
    request.ambiente = "SANDBOX";

    service.atualizar(request);

    assertThat(config.getWebhookToken()).isEqualTo("token-antigo");
  }

  @Test
  @DisplayName("sem configuracao previa a linha e criada com flush")
  void configuracaoECriadaSobDemanda() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any(TenantPaymentSettings.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TenantPaymentDtos.ConfigResponse response = service.obterConfiguracaoAtual();

    verify(repository).saveAndFlush(any(TenantPaymentSettings.class));
    assertThat(response.provider).isEqualTo("ASAAS");
    assertThat(response.ativo).isFalse();
    // sem chave gravada nao ha o que mascarar, e sem token nao ha path de webhook
    assertThat(response.apiKeyMascarada).isNull();
    assertThat(response.webhookPath).isNull();
  }

  @Test
  @DisplayName("chave curta e mascarada sem estourar o substring")
  void chaveCurtaEMascarada() {
    TenantPaymentSettings config = config();
    config.setApiKeyEnc("cifrada");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
    when(encryptionService.decrypt("cifrada")).thenReturn("ab");

    assertThat(service.obterConfiguracaoAtual().apiKeyMascarada).isEqualTo("****ab");
    verify(encryptionService).decrypt(eq("cifrada"));
  }

  private TenantPaymentSettings config() {
    TenantPaymentSettings config = new TenantPaymentSettings();
    config.setTenantId(tenantId);
    return config;
  }
}
