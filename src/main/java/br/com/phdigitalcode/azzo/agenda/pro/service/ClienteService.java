package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ClienteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteAddressDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteDetailResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteStatsDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteTopServiceDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.PagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository.ClienteStats;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;

/**
 * Espelha {@code modules/customers/application/ServicoClientes.java}.
 *
 * <p>{@code atualizarAvatar}/{@code removerAvatar} foram portados junto com o fechamento do gap de
 * {@code customers}, usando {@code MinioStorageService.salvarArquivoClienteAvatar}/
 * {@code removerArquivoClienteAvatar}. {@code topServices} vem de views materializadas do modulo
 * {@code reports} (ainda nao migrado) e por ora e sempre lista vazia, exatamente como o original ja
 * fazia em {@code listar()}.
 */
@Service
public class ClienteService {

  private static final Logger LOG = LoggerFactory.getLogger(ClienteService.class);
  private static final int MAX_LIST_SIZE = 500;

  private final ClienteRepository clienteRepository;
  private final ClienteStatsRepository clienteStatsRepository;
  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final MinioStorageService minioStorageService;

  public ClienteService(
      ClienteRepository clienteRepository,
      ClienteStatsRepository clienteStatsRepository,
      ContextoTenant contextoTenant,
      AuditService auditService,
      MinioStorageService minioStorageService) {
    this.clienteRepository = clienteRepository;
    this.clienteStatsRepository = clienteStatsRepository;
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.minioStorageService = minioStorageService;
  }

  @Transactional(readOnly = true)
  public List<ClienteListResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Map<UUID, ClienteStats> statsByClient = clienteStatsRepository.findStatsByTenant(tenantId);
    List<ClienteListResponse> response = clienteRepository.findByTenantIdOrderByName(tenantId).stream()
        .limit(MAX_LIST_SIZE)
        .map(cliente -> toListResponse(cliente, statsByClient.getOrDefault(cliente.getId(), ClienteStats.EMPTY), List.of()))
        .toList();
    LOG.info("customers.list.completed {}", CorrelatedLogging.context("tenantId", tenantId, "count", response.size()));
    return response;
  }

  @Transactional(readOnly = true)
  public PagedResponse<ClienteListResponse> listarPaginado(int page, int limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    var pageable = PageRequest.of(page, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("name")));
    var pageResult = clienteRepository.findByTenantId(tenantId, pageable);

    List<UUID> clientIds = pageResult.getContent().stream().map(Cliente::getId).toList();
    Map<UUID, ClienteStats> statsByClient = clienteStatsRepository.findStatsByTenantAndClientIds(tenantId, clientIds);

    PagedResponse<ClienteListResponse> response = new PagedResponse<>();
    response.items = pageResult.getContent().stream()
        .map(cliente -> toListResponse(cliente, statsByClient.getOrDefault(cliente.getId(), ClienteStats.EMPTY), List.of()))
        .toList();
    response.totalCount = pageResult.getTotalElements();
    response.currentPage = page;
    response.totalPages = limit > 0 ? (int) Math.ceil((double) pageResult.getTotalElements() / limit) : 0;
    LOG.info(
        "customers.listPaged.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId, "page", page, "limit", limit,
            "count", response.items.size(), "totalCount", response.totalCount));
    return response;
  }

  @Transactional(readOnly = true)
  public ClienteDetailResponse obterPorId(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> {
      LOG.warn("customers.getById.notFound {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
      return new IllegalArgumentException("Cliente nao encontrado");
    });
    LOG.info("customers.getById.completed {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
    return toDetailResponse(cliente, clienteStatsRepository.findStatsByTenantAndClient(tenantId, cliente.getId()), List.of());
  }

  @Transactional
  public ClienteListResponse criar(ClienteRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente c = new Cliente();
    c.setTenantId(tenantId);
    aplicar(req, c);
    clienteRepository.save(c);

    ClienteStats stats = clienteStatsRepository.findStatsByTenantAndClient(tenantId, c.getId());
    auditar(tenantId, "CLIENT_CREATE", null, resumoCliente(c, stats), c.getId());
    LOG.info(
        "customers.create.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId, "clientId", c.getId(), "email", c.getEmail(),
            "phone", c.getPhone(), "clientType", c.getClientType()));
    return toListResponse(c, stats, List.of());
  }

  @Transactional
  public ClienteListResponse atualizar(UUID id, ClienteRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente c = clienteRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> {
      LOG.warn("customers.update.notFound {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
      return new IllegalArgumentException("Cliente nao encontrado");
    });

    ClienteStats beforeStats = clienteStatsRepository.findStatsByTenantAndClient(tenantId, c.getId());
    Map<String, Object> before = resumoCliente(c, beforeStats);
    aplicar(req, c);
    clienteRepository.save(c);

    ClienteStats afterStats = clienteStatsRepository.findStatsByTenantAndClient(tenantId, c.getId());
    auditar(tenantId, "CLIENT_UPDATE", before, resumoCliente(c, afterStats), c.getId());
    LOG.info(
        "customers.update.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId, "clientId", c.getId(), "email", c.getEmail(),
            "phone", c.getPhone(), "clientType", c.getClientType()));
    return toListResponse(c, afterStats, List.of());
  }

  @Transactional
  public void deletar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente before = clienteRepository.findByIdAndTenantId(id, tenantId).orElse(null);
    if (before == null) {
      LOG.warn("customers.delete.notFound {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
      throw new IllegalArgumentException("Cliente nao encontrado");
    }
    ClienteStats stats = clienteStatsRepository.findStatsByTenantAndClient(tenantId, before.getId());
    Map<String, Object> snapshot = resumoCliente(before, stats);
    clienteRepository.delete(before);

    auditar(tenantId, "CLIENT_DELETE", snapshot, null, id);
    LOG.info("customers.delete.completed {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
  }

  @Transactional
  public ClienteDetailResponse atualizarAvatar(UUID id, byte[] arquivo, String nomeArquivo, String contentType) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> {
      LOG.warn("customers.avatar.update.notFound {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
      return new IllegalArgumentException("Cliente nao encontrado");
    });

    String avatarAnterior = cliente.getAvatar();
    String novoAvatar =
        minioStorageService.salvarArquivoClienteAvatar(arquivo, nomeArquivo, contentType, tenantId, cliente.getId());
    cliente.setAvatar(novoAvatar);
    if (deveRemoverAvatarAnterior(avatarAnterior, novoAvatar)) {
      minioStorageService.removerArquivoClienteAvatar(avatarAnterior, tenantId);
    }
    clienteRepository.save(cliente);
    LOG.info(
        "customers.avatar.update.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId, "clientId", cliente.getId(), "contentType", contentType));
    return toDetailResponse(
        cliente, clienteStatsRepository.findStatsByTenantAndClient(tenantId, cliente.getId()), List.of());
  }

  @Transactional
  public ClienteDetailResponse removerAvatar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> {
      LOG.warn("customers.avatar.remove.notFound {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", id));
      return new IllegalArgumentException("Cliente nao encontrado");
    });

    String avatarAnterior = cliente.getAvatar();
    cliente.setAvatar(null);
    if (deveRemoverDoStorage(avatarAnterior)) {
      minioStorageService.removerArquivoClienteAvatar(avatarAnterior, tenantId);
    }
    clienteRepository.save(cliente);
    LOG.info("customers.avatar.remove.completed {}", CorrelatedLogging.context("tenantId", tenantId, "clientId", cliente.getId()));
    return toDetailResponse(
        cliente, clienteStatsRepository.findStatsByTenantAndClient(tenantId, cliente.getId()), List.of());
  }

  private boolean deveRemoverAvatarAnterior(String avatarAnterior, String novoAvatar) {
    return deveRemoverDoStorage(avatarAnterior) && !avatarAnterior.equals(novoAvatar);
  }

  private boolean deveRemoverDoStorage(String avatar) {
    return avatar != null
        && !avatar.isBlank()
        && !avatar.startsWith("http://")
        && !avatar.startsWith("https://");
  }

  private void aplicar(ClienteRequest req, Cliente c) {
    c.setName(normalizeText(req.name));
    c.setEmail(normalizeEmail(req.email));
    c.setPhone(normalizeDigits(req.phone));
    c.setBirthDate(DataUtil.parseDataISO(req.birthDate));
    c.setNotes(normalizeText(req.notes));
    c.setCpfCnpj(normalizeDigits(req.cpfCnpj));
    c.setClientType((req.clientType != null && req.clientType.equals("PJ")) ? "PJ" : "PF");
    aplicarEndereco(req.address, c);
    if (req.whatsappOptOut != null) {
      boolean novoOptOut = Boolean.TRUE.equals(req.whatsappOptOut);
      if (novoOptOut && !Boolean.TRUE.equals(c.getWhatsappOptOut())) {
        c.setWhatsappOptOut(true);
        c.setWhatsappOptOutAt(java.time.Instant.now());
      } else if (!novoOptOut) {
        c.setWhatsappOptOut(false);
      }
    }
  }

  private void aplicarEndereco(ClienteAddressDto address, Cliente c) {
    if (address == null) {
      c.setZipCode(null);
      c.setStreet(null);
      c.setNumber(null);
      c.setComplement(null);
      c.setNeighborhood(null);
      c.setCity(null);
      c.setState(null);
      return;
    }
    c.setZipCode(normalizeDigits(address.zipCode));
    c.setStreet(normalizeText(address.street));
    c.setNumber(normalizeText(address.number));
    c.setComplement(normalizeText(address.complement));
    c.setNeighborhood(normalizeText(address.neighborhood));
    c.setCity(normalizeText(address.city));
    c.setState(normalizeState(address.state));
  }

  private void auditar(UUID tenantId, String action, Object before, Object after, UUID entityId) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = obterUsuarioId();
      command.actorRole = obterActorRole();
      command.module = AuditConstants.Module.CUSTOMER;
      command.action = action;
      command.entityType = "CLIENT";
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve bloquear fluxo principal.
    }
  }

  private Map<String, Object> resumoCliente(Cliente c, ClienteStats stats) {
    if (c == null) return null;
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("id", c.getId() != null ? c.getId().toString() : null);
    snapshot.put("name", c.getName());
    snapshot.put("email", c.getEmail());
    snapshot.put("phone", c.getPhone());
    snapshot.put("avatar", c.getAvatar());
    snapshot.put("birthDate", c.getBirthDate() != null ? c.getBirthDate().toString() : null);
    snapshot.put("notes", c.getNotes());
    snapshot.put("zipCode", c.getZipCode());
    snapshot.put("street", c.getStreet());
    snapshot.put("number", c.getNumber());
    snapshot.put("complement", c.getComplement());
    snapshot.put("neighborhood", c.getNeighborhood());
    snapshot.put("city", c.getCity());
    snapshot.put("state", c.getState());
    snapshot.put("totalVisits", stats.totalVisits());
    snapshot.put("totalSpent", stats.totalSpent());
    snapshot.put("lastVisit", stats.lastVisit() != null ? stats.lastVisit().toString() : null);
    return snapshot;
  }

  private UUID obterUsuarioId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
      return jwtPrincipal.userId();
    }
    return null;
  }

  private String obterActorRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getAuthorities() == null) return null;
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
        .findFirst()
        .orElse(null);
  }

  private ClienteListResponse toListResponse(Cliente c, ClienteStats stats, List<ClienteTopServiceDto> topServices) {
    ClienteListResponse r = new ClienteListResponse();
    r.id = c.getId().toString();
    r.tenantId = c.getTenantId().toString();
    r.name = c.getName();
    r.email = c.getEmail();
    r.phone = c.getPhone();
    r.avatar = c.getAvatar();
    r.avatarUrl = resolveAvatarUrl(c);
    r.birthDate = c.getBirthDate() != null ? c.getBirthDate().toString() : null;
    r.notes = c.getNotes();
    r.cpfCnpj = c.getCpfCnpj();
    r.clientType = c.getClientType();
    r.address = toAddress(c);
    r.topServices = topServices;
    r.totalVisits = stats.totalVisits();
    r.totalSpent = stats.totalSpent();
    r.lastVisit = stats.lastVisit() != null ? stats.lastVisit().toString() : null;
    r.createdAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : null;
    return r;
  }

  private ClienteDetailResponse toDetailResponse(Cliente c, ClienteStats stats, List<ClienteTopServiceDto> topServices) {
    ClienteDetailResponse response = new ClienteDetailResponse();
    ClienteListResponse base = toListResponse(c, stats, topServices);
    response.id = base.id;
    response.tenantId = base.tenantId;
    response.name = base.name;
    response.email = base.email;
    response.phone = base.phone;
    response.avatar = base.avatar;
    response.avatarUrl = base.avatarUrl;
    response.birthDate = base.birthDate;
    response.notes = base.notes;
    response.cpfCnpj = base.cpfCnpj;
    response.clientType = base.clientType;
    response.address = base.address;
    response.topServices = base.topServices;
    response.totalVisits = base.totalVisits;
    response.totalSpent = base.totalSpent;
    response.lastVisit = base.lastVisit;
    response.createdAt = base.createdAt;

    response.stats = new ClienteStatsDto();
    response.stats.totalVisits = stats.totalVisits();
    response.stats.totalSpent = stats.totalSpent();
    response.stats.lastVisit = stats.lastVisit() != null ? stats.lastVisit().toString() : null;
    return response;
  }

  public String resolveAvatarUrl(Cliente cliente) {
    if (cliente == null || cliente.getAvatar() == null || cliente.getAvatar().isBlank()) return null;
    if (cliente.getAvatar().startsWith("http://") || cliente.getAvatar().startsWith("https://")) {
      return cliente.getAvatar();
    }
    return "/api/v1/clients/" + cliente.getId() + "/avatar";
  }

  private ClienteAddressDto toAddress(Cliente c) {
    if (c == null) return null;
    if (c.getZipCode() == null && c.getStreet() == null && c.getNumber() == null && c.getComplement() == null
        && c.getNeighborhood() == null && c.getCity() == null && c.getState() == null) {
      return null;
    }
    ClienteAddressDto address = new ClienteAddressDto();
    address.zipCode = c.getZipCode();
    address.street = c.getStreet();
    address.number = c.getNumber();
    address.complement = c.getComplement();
    address.neighborhood = c.getNeighborhood();
    address.city = c.getCity();
    address.state = c.getState();
    return address;
  }

  private String normalizeText(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeEmail(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizeDigits(String value) {
    String normalized = normalizeText(value);
    if (normalized == null) return null;
    String digits = normalized.replaceAll("\\D", "");
    return digits.isBlank() ? null : digits;
  }

  private String normalizeState(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }
}
