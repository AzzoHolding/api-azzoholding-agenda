package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackage;

/** Espelha {@code modules/packages/domain/repository/ServicePackageRepository.java}. */
@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {

  List<ServicePackage> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<ServicePackage> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Ha outro pacote ATIVO do salao com este nome (sem diferenca de maiusculas)? */
  @org.springframework.data.jpa.repository.Query(
      "select count(p) > 0 from ServicePackage p where p.tenantId = :tenantId and p.ativo = true"
          + " and lower(trim(p.nome)) = lower(trim(:nome)) and (:id is null or p.id <> :id)")
  boolean existeAtivoComNome(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
      @org.springframework.data.repository.query.Param("nome") String nome,
      @org.springframework.data.repository.query.Param("id") UUID id);
}
