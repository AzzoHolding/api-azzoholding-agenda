package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantAddress;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantAddressRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
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
  private final AuditService auditService;
  private final AuthenticatedUser authenticatedUser;

  public ServicoSalonProfile(
      ContextoTenant contextoTenant,
      TenantRepository tenantRepository,
      TenantAddressRepository tenantAddressRepository,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      PublicBookingUrlService publicBookingUrlService,
      MinioStorageService minioStorageService,
      AuditService auditService,
      AuthenticatedUser authenticatedUser) {
    this.auditService = auditService;
    this.authenticatedUser = authenticatedUser;
    this.contextoTenant = contextoTenant;
    this.tenantRepository = tenantRepository;
    this.tenantAddressRepository = tenantAddressRepository;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.publicBookingUrlService = publicBookingUrlService;
    this.minioStorageService = minioStorageService;
  }

  /**
   * Quantas vezes o salao pode trocar o CPF/CNPJ depois do cadastro.
   *
   * <p>O documento identifica o periodo de avaliacao (um por CPF/CNPJ, para sempre) e o cliente da
   * cobranca. Sem limite, trocar de documento a cada troca de avaliacao seria so questao de
   * digitar. Acima do limite a troca so pelo suporte.
   */
  public static final int LIMITE_DE_TROCAS_DO_DOCUMENTO = 2;

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
    if (!br.com.phdigitalcode.azzo.agenda.pro.util.DocumentoValidator.cpfOuCnpjValido(document)) {
      throw new IllegalArgumentException("CPF/CNPJ invalido. Confira os numeros.");
    }

    registrarTrocaDeDocumentoOuFalhar(tenant, document);

    String nomeAntes = tenant.getName();
    String slugAntes = tenant.getSlug();
    boolean documentoMudou = !document.equals(onlyDigitsOrNull(tenant.getDocument()));
    aplicarSlugOuFalhar(tenant, request.salonSlug);

    tenant.setName(request.salonName);
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
    auditarAtualizacaoDoPerfil(tenant, nomeAntes, slugAntes, documentoMudou);
    return toPrivateProfile(tenant, tenantAddress);
  }

  /**
   * Formato do endereco de agendamento: letras minusculas e numeros separados por hifen unico, sem
   * hifen no comeco nem no fim. E o que o gerador do cadastro produz ({@code SlugUtil}).
   */
  private static final java.util.regex.Pattern FORMATO_DO_SLUG =
      java.util.regex.Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  static final int SLUG_MIN = 3;
  static final int SLUG_MAX = 80;

  /**
   * Troca o endereco de agendamento (slug) do salao com validacao (achado SEG-006): antes era
   * gravado cru, sem formato nem tamanho, e a unicidade so aparecia como erro generico do banco.
   * Vazio nao altera. Slug IGUAL ao atual nao e revalidado, para nao travar quem tem um endereco
   * antigo fora do formato novo. Trocar o slug muda o link publico: quem ja divulgou o antigo deixa
   * de abrir a pagina, por isso a tela deve avisar antes.
   */
  private void aplicarSlugOuFalhar(Tenant tenant, String pedido) {
    if (pedido == null || pedido.isBlank()) return;
    String enviado = pedido.trim();
    if (enviado.equals(tenant.getSlug())) return; // antes de normalizar: o legado volta igual
    String novo = enviado.toLowerCase(java.util.Locale.ROOT);
    if (novo.equals(tenant.getSlug())) return;
    if (novo.length() < SLUG_MIN || novo.length() > SLUG_MAX || !FORMATO_DO_SLUG.matcher(novo).matches()) {
      throw new ApiClientErrorException(
          "Endereco de agendamento invalido: use de " + SLUG_MIN + " a " + SLUG_MAX
              + " caracteres, so letras minusculas, numeros e hifen (sem hifen no comeco, no fim ou repetido).",
          400);
    }
    boolean emUsoPorOutro =
        tenantRepository.findBySlug(novo).map(outro -> !outro.getId().equals(tenant.getId())).orElse(false);
    if (emUsoPorOutro) {
      throw new ApiClientErrorException(
          "Este endereco de agendamento ja esta em uso por outro salao. Escolha outro.", 409);
    }
    tenant.setSlug(novo);
  }

  /** Trilha da alteracao do perfil. O documento nao vai para o log: so se ele mudou. */
  private void auditarAtualizacaoDoPerfil(
      Tenant tenant, String nomeAntes, String slugAntes, boolean documentoMudou) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenant.getId();
      command.actorUserId = authenticatedUser.idOuNulo();
      command.actorRole = authenticatedUser.roleOuNulo();
      command.module = AuditConstants.Module.TENANT;
      command.action = "SALON_PROFILE_UPDATED";
      command.entityType = "TENANT";
      command.entityId = tenant.getId() != null ? tenant.getId().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = java.util.Map.of("name", String.valueOf(nomeAntes), "slug", String.valueOf(slugAntes));
      command.after =
          java.util.Map.of(
              "name", String.valueOf(tenant.getName()), "slug", String.valueOf(tenant.getSlug()));
      command.metadata = java.util.Map.of("documentChanged", documentoMudou);
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar a gravacao do perfil.
    }
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
    dto.documentChangesRemaining = trocasRestantes(tenant);
    dto.documentChangeLimit = LIMITE_DE_TROCAS_DO_DOCUMENTO;
    return dto;
  }

  /**
   * Conta a troca de CPF/CNPJ e recusa a que passa do limite.
   *
   * <p>So conta quando os DIGITOS mudam: a tela reenvia o perfil inteiro a cada gravacao, inclusive
   * o documento igual, e isso nao pode gastar troca. Salao sem documento anterior (legado) define o
   * primeiro sem contar — nao ha o que "trocar". Roda ANTES de qualquer campo ser alterado.
   */
  private void registrarTrocaDeDocumentoOuFalhar(Tenant tenant, String novoDocumento) {
    String atual = onlyDigitsOrNull(tenant.getDocument());
    if (atual == null || atual.equals(novoDocumento)) return;

    int trocas = tenant.getDocumentChangeCount() == null ? 0 : tenant.getDocumentChangeCount();
    if (trocas >= LIMITE_DE_TROCAS_DO_DOCUMENTO) {
      throw new ApiClientErrorException(
          "O CPF/CNPJ do salao ja foi alterado "
              + LIMITE_DE_TROCAS_DO_DOCUMENTO
              + " vezes e nao pode mais ser trocado por aqui. Para corrigir, fale com o suporte.",
          409);
    }
    tenant.setDocumentChangeCount(trocas + 1);
    LOG.info(
        "CPF/CNPJ do salao alterado (tenantId={}, trocas={}/{})",
        tenant.getId(),
        trocas + 1,
        LIMITE_DE_TROCAS_DO_DOCUMENTO);
  }

  private int trocasRestantes(Tenant tenant) {
    int trocas = tenant.getDocumentChangeCount() == null ? 0 : tenant.getDocumentChangeCount();
    return Math.max(0, LIMITE_DE_TROCAS_DO_DOCUMENTO - trocas);
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
