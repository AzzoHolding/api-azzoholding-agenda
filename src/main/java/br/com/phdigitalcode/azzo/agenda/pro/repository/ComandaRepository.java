package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import jakarta.persistence.LockModeType;

/** Espelha {@code modules/pos/domain/repository/ComandaRepository.java}. */
@Repository
public interface ComandaRepository extends JpaRepository<Comanda, UUID> {

  Optional<Comanda> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Mesma busca de {@link #findByIdAndTenantId}, mas com lock pessimista de escrita: usada em
   * operacoes com efeito colateral irreversivel (fechar a comanda lanca receita, baixa estoque e
   * gera comissao) para impedir que duas requisicoes concorrentes na mesma comanda processem o
   * fechamento duas vezes.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Comanda c where c.id = :id and c.tenantId = :tenantId")
  Optional<Comanda> findByIdAndTenantParaAtualizacao(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId);

  Page<Comanda> findByTenantIdOrderByOpenedAtDesc(UUID tenantId, Pageable pageable);

  Page<Comanda> findByTenantIdAndStatusOrderByOpenedAtDesc(
      UUID tenantId, String status, Pageable pageable);

  /**
   * Usado para checar se um agendamento ja teve comanda aberta (idempotencia da abertura
   * automatica) e para evitar duplicar receita entre Agendamento e Comanda.
   */
  boolean existsByAppointmentIdAndTenantId(UUID appointmentId, UUID tenantId);

  List<Comanda> findByAppointmentIdAndTenantId(UUID appointmentId, UUID tenantId);

  default Optional<Comanda> findFirstByAppointmentAndTenant(UUID appointmentId, UUID tenantId) {
    if (appointmentId == null || tenantId == null) return Optional.empty();
    return findByAppointmentIdAndTenantId(appointmentId, tenantId).stream().findFirst();
  }
}
