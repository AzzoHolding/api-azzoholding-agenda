package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.SpecialtyCreateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.SpecialtyUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.SpecialtyResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Specialty;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SpecialtyRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/services/application/ServicoSpecialties.java}. */
@Service
public class SpecialtyService {

  private final SpecialtyRepository specialtyRepository;
  private final ContextoTenant contextoTenant;

  public SpecialtyService(SpecialtyRepository specialtyRepository, ContextoTenant contextoTenant) {
    this.specialtyRepository = specialtyRepository;
    this.contextoTenant = contextoTenant;
  }

  public List<SpecialtyResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return specialtyRepository.findByTenantIdOrderByName(tenantId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public SpecialtyResponse criar(SpecialtyCreateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String name = normalizeName(request.name);
    String description = normalizeDescription(request.description);

    Specialty specialty = specialtyRepository.findByTenantAndName(tenantId, name).orElse(null);
    if (specialty == null) {
      specialty = new Specialty();
      specialty.setTenantId(tenantId);
      specialty.setName(name);
      specialty.setDescription(description);
      specialty = specialtyRepository.save(specialty);
    } else if (description != null && !description.equals(specialty.getDescription())) {
      specialty.setDescription(description);
      specialty = specialtyRepository.save(specialty);
    }
    return toResponse(specialty);
  }

  @Transactional
  public SpecialtyResponse atualizar(UUID id, SpecialtyUpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Specialty specialty = specialtyRepository.findById(id)
        .filter(item -> tenantId.equals(item.getTenantId()))
        .orElseThrow(() -> new IllegalArgumentException("Especialidade nao encontrada"));

    String name = normalizeName(request.name);
    String description = normalizeDescription(request.description);

    Specialty sameName = specialtyRepository.findByTenantAndName(tenantId, name).orElse(null);
    if (sameName != null && !sameName.getId().equals(specialty.getId())) {
      throw new IllegalArgumentException("Ja existe especialidade com este nome");
    }

    specialty.setName(name);
    specialty.setDescription(description);
    return toResponse(specialtyRepository.save(specialty));
  }

  @Transactional
  public void deletar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Specialty specialty = specialtyRepository.findById(id)
        .filter(item -> tenantId.equals(item.getTenantId()))
        .orElseThrow(() -> new IllegalArgumentException("Especialidade nao encontrada"));
    specialtyRepository.delete(specialty);
  }

  @Transactional
  public int deletarSelecionadas(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("Nenhuma especialidade selecionada para remocao");
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
  public int deletarTodas() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    List<UUID> ids = specialtyRepository.findByTenantIdOrderByName(tenantId).stream().map(Specialty::getId).toList();
    if (ids.isEmpty()) return 0;
    int removedCount = 0;
    for (UUID id : ids) {
      deletar(id);
      removedCount++;
    }
    return removedCount;
  }

  private SpecialtyResponse toResponse(Specialty specialty) {
    SpecialtyResponse response = new SpecialtyResponse();
    response.id = specialty.getId().toString();
    response.tenantId = specialty.getTenantId().toString();
    response.name = specialty.getName();
    response.description = specialty.getDescription();
    response.createdAt = specialty.getCreatedAt() != null ? specialty.getCreatedAt().toString() : null;
    return response;
  }

  private String normalizeName(String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isBlank()) throw new IllegalArgumentException("Nome da especialidade e obrigatorio");
    return trimmed;
  }

  private String normalizeDescription(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if (trimmed.isBlank()) return null;
    if (trimmed.length() > 500) {
      throw new IllegalArgumentException("Descricao da especialidade deve ter no maximo 500 caracteres");
    }
    return trimmed;
  }
}
