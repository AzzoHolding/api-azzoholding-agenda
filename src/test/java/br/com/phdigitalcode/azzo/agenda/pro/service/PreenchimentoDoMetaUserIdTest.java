package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupGateway;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

class PreenchimentoDoMetaUserIdTest {

  private TenantWhatsAppConfigRepository repository;
  private MetaEmbeddedSignupGateway gateway;
  private EncryptionService encryptionService;
  private PreenchimentoDoMetaUserId preenchimento;

  @BeforeEach
  void setUp() {
    repository = mock(TenantWhatsAppConfigRepository.class);
    gateway = mock(MetaEmbeddedSignupGateway.class);
    encryptionService = mock(EncryptionService.class);
    preenchimento = new PreenchimentoDoMetaUserId(repository, gateway, encryptionService);
  }

  private TenantWhatsAppConfig embutido(String tokenEnc) {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(UUID.randomUUID());
    config.setWhatsappTokenSource("EMBEDDED_CODE_EXCHANGE");
    config.setWhatsappAccessTokenEnc(tokenEnc);
    return config;
  }

  @Test
  void preencheQuemSeConectouAntesDoCampoExistir() {
    TenantWhatsAppConfig config = embutido("cifrado");
    when(repository.findByWhatsappTokenSourceAndMetaUserIdIsNull("EMBEDDED_CODE_EXCHANGE")).thenReturn(List.of(config));
    when(encryptionService.decrypt("cifrado")).thenReturn("token");
    when(gateway.fetchTokenUserId("token")).thenReturn("10229");

    preenchimento.preencher();

    assertThat(config.getMetaUserId()).isEqualTo("10229");
    verify(repository).save(config);
  }

  @Test
  void falhaNaMetaNaoDerrubaASubidaENaoGrava() {
    TenantWhatsAppConfig config = embutido("cifrado");
    when(repository.findByWhatsappTokenSourceAndMetaUserIdIsNull("EMBEDDED_CODE_EXCHANGE")).thenReturn(List.of(config));
    when(encryptionService.decrypt("cifrado")).thenReturn("token");
    when(gateway.fetchTokenUserId("token")).thenThrow(new IllegalStateException("token expirado"));

    preenchimento.preencher();

    assertThat(config.getMetaUserId()).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  void ignoraQuemNaoTemTokenGuardado() {
    when(repository.findByWhatsappTokenSourceAndMetaUserIdIsNull("EMBEDDED_CODE_EXCHANGE"))
        .thenReturn(List.of(embutido("")));

    preenchimento.preencher();

    verify(gateway, never()).fetchTokenUserId(any());
  }
}
