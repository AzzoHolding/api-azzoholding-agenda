package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;

/** Espelha {@code modules/membership/domain/repository/ClientMembershipRepository.java}. */
@Repository
public interface ClientMembershipRepository extends JpaRepository<ClientMembership, UUID> {

  List<ClientMembership> findByTenantIdAndClientIdOrderByCreatedAtDesc(UUID tenantId, UUID clientId);

  Optional<ClientMembership> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<ClientMembership> findByAsaasSubscriptionId(String asaasSubscriptionId);

  /**
   * Assinaturas com renovacao ja suspensa cujo periodo pago venceu — o cancelamento so vira
   * definitivo aqui, nunca no momento do pedido do cliente.
   */
  @Query("""
      select m from ClientMembership m
      where m.cancelAtPeriodEnd = true
        and m.status <> :statusCancelada
        and m.periodEnd < :now
      """)
  List<ClientMembership> findPendentesDeCancelamento(
      @Param("statusCancelada") String statusCancelada, @Param("now") Instant now);

  List<ClientMembership> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, org.springframework.data.domain.Limit limit);

  Optional<ClientMembership> findByProvedorRecorrenciaAndAssinaturaNaOperadora(String provedor, String assinatura);

  /**
   * Cartao recorrente que pede consulta: esperando o cartao, ou com o periodo vencendo/vencido (a
   * cobranca do mes ja deveria ter vindo). A reserva para quando o aviso da operadora se perde.
   */
  @Query("""
      select m from ClientMembership m
      where m.assinaturaNaOperadora is not null
        and m.status in ('AGUARDANDO_CARTAO', 'ATIVA', 'INADIMPLENTE')
        and (m.status = 'AGUARDANDO_CARTAO' or m.periodEnd < :limite)
      order by m.updatedAt asc
      """)
  List<ClientMembership> cartaoRecorrenteParaConsultar(@Param("limite") Instant limite, org.springframework.data.domain.Limit limit);
}
