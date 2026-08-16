package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/membership/application/ClientMembershipMaintenanceService.java}: efetiva o
 * cancelamento de assinaturas que ja tiveram a renovacao suspensa ({@code cancelAtPeriodEnd=true}) e
 * cujo periodo pago ja terminou.
 */
@Service
public class ClientMembershipMaintenanceService {

  private static final Logger LOG =
      LoggerFactory.getLogger(ClientMembershipMaintenanceService.class);

  private final ClientMembershipRepository clientMembershipRepository;

  public ClientMembershipMaintenanceService(ClientMembershipRepository clientMembershipRepository) {
    this.clientMembershipRepository = clientMembershipRepository;
  }

  @Transactional
  public int efetivarCancelamentosPendentes() {
    List<ClientMembership> pendentes =
        clientMembershipRepository.findPendentesDeCancelamento(
            ClientMembership.STATUS_CANCELADA, Instant.now());
    for (ClientMembership membership : pendentes) {
      membership.setStatus(ClientMembership.STATUS_CANCELADA);
      LOG.info(
          CorrelatedLogging.context(
              "Assinatura cancelada ao final do periodo",
              "tenantId", membership.getTenantId(),
              "membershipId", membership.getId()));
    }
    return pendentes.size();
  }
}
