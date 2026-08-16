package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ServicoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServiceCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/services/application/ServicoServicos.java}. */
@Service
public class ServicoService {

  private final ServicoRepository servicoRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ServiceCategoryRepository serviceCategoryRepository;
  private final ContextoTenant contextoTenant;

  public ServicoService(
      ServicoRepository servicoRepository,
      ProfissionalRepository profissionalRepository,
      ServiceCategoryRepository serviceCategoryRepository,
      ContextoTenant contextoTenant) {
    this.servicoRepository = servicoRepository;
    this.profissionalRepository = profissionalRepository;
    this.serviceCategoryRepository = serviceCategoryRepository;
    this.contextoTenant = contextoTenant;
  }

  @Transactional(readOnly = true)
  public List<ServicoResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return servicoRepository.findByTenantId(tenantId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public ServicoResponse criar(ServicoRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Servico s = new Servico();
    s.setTenantId(tenantId);
    aplicar(req, s, tenantId);
    return toResponse(servicoRepository.save(s));
  }

  @Transactional
  public ServicoResponse atualizar(UUID id, ServicoRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Servico s = servicoRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Servico nao encontrado"));
    aplicar(req, s, tenantId);
    return toResponse(servicoRepository.save(s));
  }

  @Transactional
  public void deletar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Servico servico = servicoRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Servico nao encontrado"));
    servicoRepository.delete(servico);
  }

  @Transactional
  public int deletarSelecionados(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("Nenhum servico selecionado para remocao");
    }
    int removedCount = 0;
    for (UUID id : ids) {
      if (id == null) continue;
      deletar(id);
      removedCount++;
    }
    return removedCount;
  }

  @Transactional
  public int deletarTodos() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<UUID> ids = servicoRepository.findByTenantId(tenantId).stream().map(Servico::getId).toList();
    if (ids.isEmpty()) return 0;
    int removedCount = 0;
    for (UUID id : ids) {
      deletar(id);
      removedCount++;
    }
    return removedCount;
  }

  private void aplicar(ServicoRequest req, Servico s, UUID tenantId) {
    s.setName(req.name);
    s.setDescription(req.description);
    s.setDuration(req.duration);
    s.setPrice(req.price);
    s.setCategoryId(resolveCategoryId(tenantId, req.category));
    s.setActive(req.isActive);
    atualizarProfissionais(req.professionalIds, s, tenantId);
    aplicarSinal(req, s);
  }

  private void aplicarSinal(ServicoRequest req, Servico s) {
    s.setRequiresDeposit(req.requiresDeposit);
    if (!req.requiresDeposit) {
      s.setDepositType(null);
      s.setDepositValue(null);
      return;
    }
    if (req.depositType == null || req.depositType.isBlank()) {
      throw new IllegalArgumentException("Tipo de sinal e obrigatorio quando o servico exige sinal.");
    }
    if (req.depositValue == null) {
      throw new IllegalArgumentException("Valor do sinal e obrigatorio quando o servico exige sinal.");
    }
    if ("PERCENTUAL".equals(req.depositType) && req.depositValue.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("Percentual do sinal nao pode ser maior que 100.");
    }
    s.setDepositType(req.depositType);
    s.setDepositValue(req.depositValue);
  }

  private void atualizarProfissionais(List<UUID> professionalIds, Servico servico, UUID tenantId) {
    servico.getProfissionais().clear();
    if (professionalIds == null || professionalIds.isEmpty()) {
      return; // relacionamento opcional
    }
    servico.getProfissionais().addAll(profissionalRepository.findByIdInAndTenantId(professionalIds, tenantId));
  }

  private ServicoResponse toResponse(Servico s) {
    ServicoResponse r = new ServicoResponse();
    r.id = s.getId().toString();
    r.tenantId = s.getTenantId().toString();
    r.name = s.getName();
    r.description = s.getDescription();
    r.duration = s.getDuration();
    r.price = s.getPrice();
    r.category = resolveCategoryName(s.getCategoryId());
    r.professionalIds = s.getProfissionais().stream().map(Profissional::getId).collect(Collectors.toList());
    r.isActive = s.isActive();
    r.createdAt = s.getCreatedAt() != null ? s.getCreatedAt().toString() : null;
    r.requiresDeposit = s.isRequiresDeposit();
    r.depositType = s.getDepositType();
    r.depositValue = s.getDepositValue();
    return r;
  }

  private String resolveCategoryName(UUID categoryId) {
    if (categoryId == null) return null;
    return serviceCategoryRepository.findById(categoryId).map(ServiceCategory::getName).orElse(null);
  }

  private UUID resolveCategoryId(UUID tenantId, String categoryName) {
    if (categoryName == null || categoryName.isBlank()) return null;
    String normalized = categoryName.trim();
    ServiceCategory category = serviceCategoryRepository.findByTenantAndName(tenantId, normalized).orElse(null);
    if (category == null) {
      category = new ServiceCategory();
      category.setTenantId(tenantId);
      category.setName(normalized);
      category = serviceCategoryRepository.save(category);
    }
    return category.getId();
  }
}
