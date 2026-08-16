package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantTelegramDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TelegramBotClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;

/** Cobre {@code ServicoTenantTelegram}: espelha {@code ServicoTenantTelegramUnitTest} do Quarkus original. */
class ServicoTenantTelegramTest {

  private final UUID tenantId = UUID.randomUUID();

  private ContextoTenant contextoTenant;
  private AuditService auditService;
  private TenantTelegramConfigRepository repository;
  private EncryptionService encryptionService;
  private WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private TelegramBotClient telegramBotClient;
  private ServicoTenantTelegram service;

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    auditService = mock(AuditService.class);
    repository = mock(TenantTelegramConfigRepository.class);
    encryptionService = mock(EncryptionService.class);
    webhookVerifyTokenHashService = mock(WebhookVerifyTokenHashService.class);
    telegramBotClient = mock(TelegramBotClient.class);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(webhookVerifyTokenHashService.hash(anyString())).thenReturn("hashed-secret");
    when(encryptionService.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
    when(repository.save(any(TenantTelegramConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    service = new ServicoTenantTelegram(
        contextoTenant, auditService, repository, encryptionService, webhookVerifyTokenHashService,
        telegramBotClient, "https://app.azzoholding.com.br");
  }

  private TenantTelegramConfig configVazia() {
    TenantTelegramConfig config = new TenantTelegramConfig();
    config.setTenantId(tenantId);
    config.setTelegramBotTokenEnc("");
    config.setTelegramEnabled(false);
    return config;
  }

  @Test
  void atualizarSemTokenNaPrimeiraConfiguracaoLancaExcecao() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());

    TenantTelegramDtos.UpdateRequest request = new TenantTelegramDtos.UpdateRequest();
    request.telegramEnabled = true;

    assertThatThrownBy(() -> service.atualizar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("obrigatorio na primeira configuracao");
  }

  @Test
  void atualizarComTokenValidoConfiguraWebhookERegistraAuditoria() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(encryptionService.decrypt(anyString()))
        .thenAnswer(inv -> ((String) inv.getArgument(0)).replaceFirst("^enc:", ""));
    TelegramBotClient.BotProfile profile = new TelegramBotClient.BotProfile();
    profile.username = "meu_bot";
    profile.displayName = "Meu Bot";
    when(telegramBotClient.getMe(any(TenantTelegramConfig.class))).thenReturn(profile);

    TenantTelegramDtos.UpdateRequest request = new TenantTelegramDtos.UpdateRequest();
    request.botToken = "123456:ABC-token";
    request.telegramEnabled = true;

    TenantTelegramDtos.ConfigResponse response = service.atualizar(request);

    assertThat(response.telegramEnabled).isTrue();
    assertThat(response.botUsername).isEqualTo("meu_bot");
    verify(telegramBotClient).setWebhook(
        any(TenantTelegramConfig.class),
        org.mockito.ArgumentMatchers.eq("https://app.azzoholding.com.br/webhook/telegram/" + tenantId),
        anyString());
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void atualizarComTelegramDesabilitadoRemoveWebhook() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(encryptionService.decrypt(anyString()))
        .thenAnswer(inv -> ((String) inv.getArgument(0)).replaceFirst("^enc:", ""));
    TelegramBotClient.BotProfile profile = new TelegramBotClient.BotProfile();
    profile.username = "meu_bot";
    when(telegramBotClient.getMe(any(TenantTelegramConfig.class))).thenReturn(profile);

    TenantTelegramDtos.UpdateRequest request = new TenantTelegramDtos.UpdateRequest();
    request.botToken = "123456:ABC-token";
    request.telegramEnabled = false;

    service.atualizar(request);

    verify(telegramBotClient).deleteWebhook(any(TenantTelegramConfig.class));
    verify(telegramBotClient, never()).setWebhook(any(TenantTelegramConfig.class), anyString(), any());
  }

  @Test
  void testarConexaoComFalhaRetornaSucessoFalseERegistraErro() {
    TenantTelegramConfig config = configVazia();
    config.setTelegramBotTokenEnc("enc-token");
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(encryptionService.decrypt("enc-token")).thenReturn("bad-token");
    when(telegramBotClient.getMe(any(TenantTelegramConfig.class)))
        .thenThrow(new IllegalArgumentException("Token do bot do Telegram nao configurado."));

    TenantTelegramDtos.TestResponse response = service.testarConexao();

    assertThat(response.success).isFalse();
    assertThat(response.message).contains("Revise o token do bot informado");
    verify(auditService).recordError(any(AuditEventCommand.class));
  }

  @Test
  void enviarMensagemTesteComChatIdInvalidoNaoChamaTelegram() {
    TenantTelegramConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    TenantTelegramDtos.TestMessageRequest request = new TenantTelegramDtos.TestMessageRequest();
    request.destinationChatId = "+5511999998888";

    TenantTelegramDtos.TestMessageResponse response = service.enviarMensagemTeste(request);

    assertThat(response.success).isFalse();
    assertThat(response.message).contains("nao envia por telefone");
    verify(telegramBotClient, never()).sendMessage(any(TenantTelegramConfig.class), anyString(), anyString());
  }

  @Test
  void enviarMensagemTesteComChatIdValidoEnviaERetornaProviderMessageId() {
    TenantTelegramConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(telegramBotClient.sendMessage(any(TenantTelegramConfig.class), anyString(), anyString()))
        .thenReturn("999");

    TenantTelegramDtos.TestMessageRequest request = new TenantTelegramDtos.TestMessageRequest();
    request.destinationChatId = "123456789";

    TenantTelegramDtos.TestMessageResponse response = service.enviarMensagemTeste(request);

    assertThat(response.success).isTrue();
    assertThat(response.providerMessageId).isEqualTo("999");
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void validarConfiguracaoDelegaParaTelegramBotClient() {
    TelegramBotClient.BotProfile profile = new TelegramBotClient.BotProfile();
    profile.username = "meu_bot";
    profile.displayName = "Meu Bot";
    when(telegramBotClient.getMe("123456:ABC-token")).thenReturn(profile);

    TenantTelegramDtos.ValidateRequest request = new TenantTelegramDtos.ValidateRequest();
    request.botToken = "123456:ABC-token";

    TenantTelegramDtos.ValidateResponse response = service.validarConfiguracao(request);

    assertThat(response.success).isTrue();
    assertThat(response.botUsername).isEqualTo("meu_bot");
  }

  @Test
  void obterConfiguracaoAtualSemTokenNaoConsultaWebhookInfo() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());

    TenantTelegramDtos.ConfigResponse response = service.obterConfiguracaoAtual();

    assertThat(response.botTokenConfigured).isFalse();
    assertThat(response.webhook).isNull();
    verify(telegramBotClient, times(0)).getWebhookInfo(any(TenantTelegramConfig.class));
  }
}
