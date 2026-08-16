package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantAddress;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantAddressRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre {@code modules/salon/application/ServicoSalonProfile.java} — espelha
 * {@code ServicoSalonProfileUnitTest} do original (atualizar/remover logo, exposicao de datas
 * especiais no perfil privado) e acrescenta os demais ramos (perfil publico, validacao de
 * documento, criacao de endereco novo, "salao nao encontrado").
 */
class ServicoSalonProfileTest {

  private ContextoTenant contextoTenant;
  private TenantRepository tenantRepository;
  private TenantAddressRepository tenantAddressRepository;
  private TenantOperationalSettingsService tenantOperationalSettingsService;
  private PublicBookingUrlService publicBookingUrlService;
  private MinioStorageService minioStorageService;
  private ServicoSalonProfile service;

  @BeforeEach
  void setUp() {
    contextoTenant = new ContextoTenant();
    tenantRepository = mock(TenantRepository.class);
    tenantAddressRepository = mock(TenantAddressRepository.class);
    tenantOperationalSettingsService = mock(TenantOperationalSettingsService.class);
    publicBookingUrlService = mock(PublicBookingUrlService.class);
    minioStorageService = mock(MinioStorageService.class);
    service =
        new ServicoSalonProfile(
            contextoTenant,
            tenantRepository,
            tenantAddressRepository,
            tenantOperationalSettingsService,
            publicBookingUrlService,
            minioStorageService);
  }

  private Tenant tenant(UUID tenantId) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setName("Salao QA");
    tenant.setSlug("salao-qa");
    tenant.setCreatedAt(Instant.now());
    return tenant;
  }

  @Test
  void deveAtualizarERemoverLogoDoSalao() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    Tenant tenant = tenant(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantAddressRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    when(tenantOperationalSettingsService.getSpecialClosureDates(tenantId)).thenReturn(List.of());
    when(publicBookingUrlService.buildPublicBookingUrl("salao-qa")).thenReturn("https://qa.local/agendar/salao-qa");
    String storageKey = "tenant/" + tenantId + "/salao/logo/logo.webp";
    when(minioStorageService.salvarArquivoSalaoLogo(any(), eq("logo.webp"), eq("image/webp"), eq(tenantId)))
        .thenReturn(storageKey);
    when(minioStorageService.gerarUrlAssinadaLeitura(storageKey, tenantId))
        .thenReturn("https://cdn.qa.local/" + storageKey);

    SalonDtos.SalonProfile atualizado = service.atualizarLogo("fake-image".getBytes(), "logo.webp", "image/webp");

    assertThat(tenant.getLogo()).isEqualTo(storageKey);
    assertThat(atualizado.logo).isEqualTo(storageKey);
    assertThat(atualizado.logoUrl).isEqualTo("https://cdn.qa.local/" + storageKey);
    verify(minioStorageService, never()).removerArquivoSalaoLogo(any(), any());

    SalonDtos.SalonProfile removido = service.removerLogo();

    assertThat(tenant.getLogo()).isNull();
    assertThat(removido.logo).isNull();
    assertThat(removido.logoUrl).isNull();
    verify(minioStorageService).removerArquivoSalaoLogo(storageKey, tenantId);
  }

  @Test
  void naoRemoveDoStorageQuandoLogoEhUrlExterna() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    Tenant tenant = tenant(tenantId);
    tenant.setLogo("https://cdn.externo/logo.png");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantAddressRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    when(tenantOperationalSettingsService.getSpecialClosureDates(tenantId)).thenReturn(List.of());
    when(publicBookingUrlService.buildPublicBookingUrl("salao-qa")).thenReturn("https://qa.local/agendar/salao-qa");

    SalonDtos.SalonProfile removido = service.removerLogo();

    assertThat(removido.logo).isNull();
    verify(minioStorageService, never()).removerArquivoSalaoLogo(any(), any());
  }

  @Test
  void deveExporDatasEspeciaisNoPerfilPrivado() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    Tenant tenant = tenant(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantAddressRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    SalonDtos.SpecialClosureDate item = new SalonDtos.SpecialClosureDate();
    item.date = "2026-12-25";
    item.reason = "Natal";
    when(tenantOperationalSettingsService.getSpecialClosureDates(tenantId)).thenReturn(List.of(item));
    when(publicBookingUrlService.buildPublicBookingUrl("salao-qa")).thenReturn("https://qa.local/agendar/salao-qa");

    SalonDtos.SalonProfile profile = service.obterPrivado();

    assertThat(profile.specialClosureDates).hasSize(1);
    assertThat(profile.specialClosureDates.get(0).date).isEqualTo("2026-12-25");
  }

  @Test
  void obterPrivadoFalhaQuandoSalaoNaoExiste() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obterPrivado()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void atualizarPrivadoExigeDocumentoValido() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant(tenantId)));

    SalonDtos.SalonProfile request = new SalonDtos.SalonProfile();
    request.salonCpfCnpj = null;

    assertThatThrownBy(() -> service.atualizarPrivado(request)).isInstanceOf(IllegalArgumentException.class);

    request.salonCpfCnpj = "123"; // nem 11 nem 14 digitos
    assertThatThrownBy(() -> service.atualizarPrivado(request)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void atualizarPrivadoNormalizaDocumentoECriaEnderecoNovo() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    Tenant tenant = tenant(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantAddressRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(tenantAddressRepository.save(any(TenantAddress.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    when(tenantOperationalSettingsService.getSpecialClosureDates(tenantId)).thenReturn(List.of());
    when(publicBookingUrlService.buildPublicBookingUrl("novo-slug")).thenReturn("https://qa.local/agendar/novo-slug");

    SalonDtos.SalonProfile request = new SalonDtos.SalonProfile();
    request.salonName = "Novo Nome";
    request.salonSlug = "novo-slug";
    request.salonCpfCnpj = "123.456.789-01";
    request.city = "Sao Paulo";
    request.state = "SP";
    request.businessHours = List.of();
    request.specialClosureDates = List.of();

    SalonDtos.SalonProfile result = service.atualizarPrivado(request);

    assertThat(tenant.getDocument()).isEqualTo("12345678901");
    assertThat(tenant.getName()).isEqualTo("Novo Nome");
    assertThat(result.city).isEqualTo("Sao Paulo");
    verify(tenantAddressRepository).save(any(TenantAddress.class));
    verify(tenantOperationalSettingsService).updateBusinessHoursList(tenantId, request.businessHours);
    verify(tenantOperationalSettingsService).updateSpecialClosureDates(tenantId, request.specialClosureDates);
  }

  @Test
  void atualizarPrivadoReusaEnderecoExistente() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    Tenant tenant = tenant(tenantId);
    TenantAddress existingAddress = new TenantAddress();
    existingAddress.setTenantId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantAddressRepository.findById(tenantId)).thenReturn(Optional.of(existingAddress));
    when(tenantAddressRepository.save(any(TenantAddress.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    when(tenantOperationalSettingsService.getSpecialClosureDates(tenantId)).thenReturn(List.of());
    when(publicBookingUrlService.buildPublicBookingUrl(any())).thenReturn("https://qa.local/agendar/slug");

    SalonDtos.SalonProfile request = new SalonDtos.SalonProfile();
    request.salonCpfCnpj = "12345678901234"; // 14 digitos (CNPJ)
    request.street = "Rua Nova";

    service.atualizarPrivado(request);

    assertThat(existingAddress.getStreet()).isEqualTo("Rua Nova");
    verify(tenantAddressRepository).save(existingAddress);
  }

  @Test
  void obterPublicoMontaPerfilPublicoSemDadosPrivados() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = tenant(tenantId);
    tenant.setLogo("logokey.webp");
    when(tenantRepository.findBySlug("salao-qa")).thenReturn(Optional.of(tenant));
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    when(publicBookingUrlService.buildPublicBookingUrl("salao-qa")).thenReturn("https://qa.local/agendar/salao-qa");
    when(minioStorageService.gerarUrlAssinadaLeitura("logokey.webp", tenantId))
        .thenReturn("https://cdn.qa.local/logokey.webp");

    SalonDtos.PublicSalonProfile publico = service.obterPublico("salao-qa");

    assertThat(publico.salonName).isEqualTo("Salao QA");
    assertThat(publico.publicBookingUrl).isEqualTo("https://qa.local/agendar/salao-qa");
    assertThat(publico.logoUrl).isEqualTo("https://cdn.qa.local/logokey.webp");
    assertThat(publico.depositRequired).isFalse();
    assertThat(publico.depositAmount).isNull();
  }

  @Test
  void obterPublicoFalhaQuandoSlugNaoExiste() {
    when(tenantRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obterPublico("inexistente")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveLogoUrlDevolveNuloQuandoStorageFalha() {
    UUID tenantId = UUID.randomUUID();
    contextoTenant.definirTenantId(tenantId);
    Tenant tenant = tenant(tenantId);
    tenant.setLogo("logokey.webp");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantAddressRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(tenantOperationalSettingsService.getBusinessHours(tenantId)).thenReturn(List.of());
    when(tenantOperationalSettingsService.getSpecialClosureDates(tenantId)).thenReturn(List.of());
    when(publicBookingUrlService.buildPublicBookingUrl("salao-qa")).thenReturn("https://qa.local/agendar/salao-qa");
    when(minioStorageService.gerarUrlAssinadaLeitura("logokey.webp", tenantId))
        .thenThrow(new IllegalStateException("storage indisponivel"));

    SalonDtos.SalonProfile profile = service.obterPrivado();

    assertThat(profile.logoUrl).isNull();
  }
}
