package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantAddress;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantAddressRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/salon/application/ServicoSalonProfile.java}: perfil publico/privado do
 * estabelecimento (dados cadastrais, endereco, logo).
 */
@Service
public class ServicoSalonProfile {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoSalonProfile.class);

  private final ContextoTenant contextoTenant;
  private final TenantRepository tenantRepository;
  private final TenantAddressRepository tenantAddressRepository;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;
  private final PublicBookingUrlService publicBookingUrlService;
  private final MinioStorageService minioStorageService;

  public ServicoSalonProfile(
      ContextoTenant contextoTenant,
      TenantRepository tenantRepository,
      TenantAddressRepository tenantAddressRepository,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      PublicBookingUrlService publicBookingUrlService,
      MinioStorageService minioStorageService) {
    this.contextoTenant = contextoTenant;
    this.tenantRepository = tenantRepository;
    this.tenantAddressRepository = tenantAddressRepository;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.publicBookingUrlService = publicBookingUrlService;
    this.minioStorageService = minioStorageService;
  }

  @Transactional(readOnly = true)
  public SalonDtos.SalonProfile obterPrivado() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Salao nao encontrado");
    TenantAddress tenantAddress = tenantAddressRepository.findById(tenantId).orElse(null);
    return toPrivateProfile(tenant, tenantAddress);
  }

  @Transactional
  public SalonDtos.SalonProfile atualizarPrivado(SalonDtos.SalonProfile request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Salao nao encontrado");

    String document = onlyDigitsOrNull(request.salonCpfCnpj);
    if (document == null || document.isBlank()) {
      throw new IllegalArgumentException("CPF ou CNPJ do salao e obrigatorio.");
    }
    if (document.length() != 11 && document.length() != 14) {
      throw new IllegalArgumentException("CPF deve ter 11 digitos ou CNPJ deve ter 14 digitos.");
    }

    tenant.setName(request.salonName);
    tenant.setSlug(request.salonSlug);
    tenant.setDescription(request.salonDescription);
    tenant.setPhone(request.salonPhone);
    tenant.setWhatsapp(request.salonWhatsapp);
    tenant.setDocument(document);
    tenant.setEmail(request.salonEmail);
    tenant.setWebsite(request.salonWebsite);
    tenant.setInstagram(request.salonInstagram);
    tenant.setFacebook(request.salonFacebook);
    tenantRepository.save(tenant);

    TenantAddress tenantAddress = tenantAddressRepository.findById(tenantId).orElse(null);
    if (tenantAddress == null) {
      tenantAddress = new TenantAddress();
      tenantAddress.setTenantId(tenantId);
    }
    tenantAddress.setStreet(request.street);
    tenantAddress.setNumber(request.number);
    tenantAddress.setComplement(request.complement);
    tenantAddress.setNeighborhood(request.neighborhood);
    tenantAddress.setCity(request.city);
    tenantAddress.setState(request.state);
    tenantAddress.setZipCode(request.zipCode);
    tenantAddress = tenantAddressRepository.save(tenantAddress);

    tenantOperationalSettingsService.updateBusinessHoursList(tenantId, request.businessHours);
    tenantOperationalSettingsService.updateSpecialClosureDates(tenantId, request.specialClosureDates);
    return toPrivateProfile(tenant, tenantAddress);
  }

  @Transactional
  public SalonDtos.SalonProfile atualizarLogo(byte[] arquivo, String nomeArquivo, String contentType) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Salao nao encontrado");

    String logoAnterior = tenant.getLogo();
    String novaLogo = minioStorageService.salvarArquivoSalaoLogo(arquivo, nomeArquivo, contentType, tenantId);
    tenant.setLogo(novaLogo);
    tenantRepository.save(tenant);

    if (deveRemoverLogoAnterior(logoAnterior, novaLogo)) {
      minioStorageService.removerArquivoSalaoLogo(logoAnterior, tenantId);
    }

    TenantAddress tenantAddress = tenantAddressRepository.findById(tenantId).orElse(null);
    return toPrivateProfile(tenant, tenantAddress);
  }

  @Transactional
  public SalonDtos.SalonProfile removerLogo() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Salao nao encontrado");

    String logoAnterior = tenant.getLogo();
    tenant.setLogo(null);
    tenantRepository.save(tenant);

    if (deveRemoverDoStorage(logoAnterior)) {
      minioStorageService.removerArquivoSalaoLogo(logoAnterior, tenantId);
    }

    TenantAddress tenantAddress = tenantAddressRepository.findById(tenantId).orElse(null);
    return toPrivateProfile(tenant, tenantAddress);
  }

  /**
   * Espelha {@code tenantRepository.find("slug", slug).firstResult()} do original. Usado por
   * {@code PublicSalonsResource} (modulo {@code publicbooking}, ainda nao migrado) — mantido aqui
   * porque pertence ao service original e nao introduz codigo morto (sera consumido quando o
   * controller publico for portado).
   */
  @Transactional(readOnly = true)
  public SalonDtos.PublicSalonProfile obterPublico(String slug) {
    Tenant tenant = tenantRepository.findBySlug(slug).orElse(null);
    if (tenant == null) throw new IllegalArgumentException("Salao nao encontrado");

    SalonDtos.PublicSalonProfile dto = new SalonDtos.PublicSalonProfile();
    dto.salonName = tenant.getName();
    dto.salonSlug = tenant.getSlug();
    dto.salonDescription = tenant.getDescription();
    dto.salonPhone = tenant.getPhone();
    dto.salonWhatsapp = tenant.getWhatsapp();
    dto.publicBookingUrl = publicBookingUrlService.buildPublicBookingUrl(tenant.getSlug());
    dto.businessHours = tenantOperationalSettingsService.getBusinessHours(tenant.getId());
    dto.logo = tenant.getLogo();
    dto.logoUrl = resolveLogoUrl(tenant.getId(), tenant.getLogo());
    return dto;
  }

  private SalonDtos.SalonProfile toPrivateProfile(Tenant tenant, TenantAddress address) {
    SalonDtos.SalonProfile dto = new SalonDtos.SalonProfile();
    dto.salonName = tenant.getName();
    dto.salonSlug = tenant.getSlug();
    dto.logo = tenant.getLogo();
    dto.logoUrl = resolveLogoUrl(tenant.getId(), tenant.getLogo());
    dto.salonDescription = tenant.getDescription();
    dto.salonPhone = tenant.getPhone();
    dto.salonWhatsapp = tenant.getWhatsapp();
    dto.salonCpfCnpj = tenant.getDocument();
    dto.publicBookingUrl = publicBookingUrlService.buildPublicBookingUrl(tenant.getSlug());
    dto.salonEmail = tenant.getEmail();
    dto.salonWebsite = tenant.getWebsite();
    dto.salonInstagram = tenant.getInstagram();
    dto.salonFacebook = tenant.getFacebook();
    dto.street = address != null ? address.getStreet() : null;
    dto.number = address != null ? address.getNumber() : null;
    dto.complement = address != null ? address.getComplement() : null;
    dto.neighborhood = address != null ? address.getNeighborhood() : null;
    dto.city = address != null ? address.getCity() : null;
    dto.state = address != null ? address.getState() : null;
    dto.zipCode = address != null ? address.getZipCode() : null;
    dto.businessHours = tenantOperationalSettingsService.getBusinessHours(tenant.getId());
    dto.specialClosureDates = tenantOperationalSettingsService.getSpecialClosureDates(tenant.getId());
    return dto;
  }

  private String onlyDigitsOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    String digits = value.replaceAll("\\D", "");
    return digits.isBlank() ? null : digits;
  }

  private boolean deveRemoverLogoAnterior(String logoAnterior, String novaLogo) {
    return deveRemoverDoStorage(logoAnterior) && !logoAnterior.equals(novaLogo);
  }

  private boolean deveRemoverDoStorage(String logo) {
    return logo != null && !logo.isBlank() && !logo.startsWith("http://") && !logo.startsWith("https://");
  }

  private String resolveLogoUrl(UUID tenantId, String logo) {
    if (logo == null || logo.isBlank()) return null;
    if (logo.startsWith("http://") || logo.startsWith("https://")) return logo;
    try {
      return minioStorageService.gerarUrlAssinadaLeitura(logo, tenantId);
    } catch (RuntimeException exception) {
      LOG.warn("Falha ao resolver URL da logo do salao (tenantId={}).", tenantId, exception);
      return null;
    }
  }
}
