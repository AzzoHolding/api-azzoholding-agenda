package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;

@Repository
public interface ContaDePagamentoRepository extends JpaRepository<ContaDePagamento, UUID> {

  List<ContaDePagamento> findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(UUID tenantId);

  Optional<ContaDePagamento> findByIdAndTenantIdAndAtivoTrue(UUID id, UUID tenantId);

  Optional<ContaDePagamento> findByWebhookTokenAndAtivoTrue(String webhookToken);

  boolean existsByTenantIdAndProvedor(UUID tenantId, String provedor);
}
