package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ToggleRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ToggleResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Cobre {@code ServicoTenantToggle}: chaves suportadas, coercao de tipo e perfis validos. */
class TenantToggleServiceTest {

  private TenantWhatsAppConfigRepository repository;
  private TenantToggleService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(TenantWhatsAppConfigRepository.class);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findById(tenantId)).thenReturn(Optional.empty());
    when(repository.save(any(TenantWhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    service = new TenantToggleService(contextoTenant, repository);
  }

  private ToggleRequest req(String key, Object value) {
    ToggleRequest request = new ToggleRequest();
    request.key = key;
    request.value = value;
    return request;
  }

  @Test
  void habilitaWhatsappComBooleanoNativo() {
    ToggleResponse response = service.aplicar(req("WHATSAPP_ENABLED", true));

    assertThat(response.key).isEqualTo("WHATSAPP_ENABLED");
    assertThat(response.value).isEqualTo(true);
    assertThat(response.updatedAt).isNotNull();
  }

  @Test
  void aceitaStringComoBooleano() {
    assertThat(service.aplicar(req("WHATSAPP_CAN_CANCEL", "true")).value).isEqualTo(true);
    assertThat(service.aplicar(req("WHATSAPP_CAN_CANCEL", "false")).value).isEqualTo(false);
  }

  @Test
  void chaveEhNormalizadaParaMaiusculaEComTrim() {
    ToggleResponse response = service.aplicar(req("  whatsapp_can_schedule  ", true));
    assertThat(response.key).isEqualTo("WHATSAPP_CAN_SCHEDULE");
  }

  @Test
  void perfilDeUsoValidoEhNormalizadoParaMaiuscula() {
    ToggleResponse response = service.aplicar(req("WHATSAPP_USAGE_PROFILE", "notifications"));
    assertThat(response.value).isEqualTo("NOTIFICATIONS");
  }

  @Test
  void perfilDeUsoInvalidoRetorna400ComOsValoresAceitos() {
    assertThatThrownBy(() -> service.aplicar(req("WHATSAPP_USAGE_PROFILE", "QUALQUER")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("REACTIVE_ONLY, NOTIFICATIONS, COMPLETE")
        .extracting(ex -> ((ApiClientErrorException) ex).getStatus())
        .isEqualTo(400);
  }

  @Test
  void toggleDesconhecidoRetorna400() {
    assertThatThrownBy(() -> service.aplicar(req("TELEGRAM_ENABLED", true)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Toggle desconhecido")
        .extracting(ex -> ((ApiClientErrorException) ex).getStatus())
        .isEqualTo(400);
  }

  @Test
  void valorDeTipoErradoParaBooleanoRetorna400() {
    assertThatThrownBy(() -> service.aplicar(req("WHATSAPP_ENABLED", 123)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("esperado boolean");
  }

  @Test
  void valorDeTipoErradoParaPerfilRetorna400() {
    assertThatThrownBy(() -> service.aplicar(req("WHATSAPP_USAGE_PROFILE", true)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("esperado string");
  }

  @Test
  void configCriadaAoAplicarToggleEmTenantSemConfigTemDefaultsDoOriginal() {
    service.aplicar(req("WHATSAPP_ENABLED", true));

    var captor = org.mockito.ArgumentCaptor.forClass(TenantWhatsAppConfig.class);
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    TenantWhatsAppConfig criada = captor.getAllValues().get(0);

    assertThat(criada.getTenantId()).isEqualTo(tenantId);
    assertThat(criada.getWhatsappAccessTokenEnc()).isEmpty();
    assertThat(criada.isCanSchedule()).isTrue();
    assertThat(criada.isCanCancel()).isTrue();
    assertThat(criada.isCanReschedule()).isTrue();
  }
}
