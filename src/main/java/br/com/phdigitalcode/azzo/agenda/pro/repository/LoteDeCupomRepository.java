package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.LoteDeCupom;

@Repository
public interface LoteDeCupomRepository extends JpaRepository<LoteDeCupom, UUID> {

  List<LoteDeCupom> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<LoteDeCupom> findByIdAndTenantId(UUID id, UUID tenantId);
}
