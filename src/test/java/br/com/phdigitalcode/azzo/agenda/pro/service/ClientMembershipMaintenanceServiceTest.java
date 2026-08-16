package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientMembership;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientMembershipRepository;

/**
 * Cobre {@code modules/membership/application/ClientMembershipMaintenanceService.java}: a transicao
 * para {@code CANCELADA} so acontece aqui, quando o periodo pago ja venceu.
 */
class ClientMembershipMaintenanceServiceTest {

  private ClientMembershipRepository clientMembershipRepository;
  private ClientMembershipMaintenanceService service;

  @BeforeEach
  void setUp() {
    clientMembershipRepository = mock(ClientMembershipRepository.class);
    service = new ClientMembershipMaintenanceService(clientMembershipRepository);
  }

  private ClientMembership pendente() {
    ClientMembership membership = new ClientMembership();
    membership.setId(UUID.randomUUID());
    membership.setTenantId(UUID.randomUUID());
    membership.setStatus(ClientMembership.STATUS_ATIVA);
    membership.setCancelAtPeriodEnd(true);
    membership.setPeriodEnd(Instant.now().minusSeconds(60));
    return membership;
  }

  @Test
  void efetivaCancelamentoDePendentesEDevolveOTotal() {
    ClientMembership a = pendente();
    ClientMembership b = pendente();
    when(clientMembershipRepository.findPendentesDeCancelamento(any(), any()))
        .thenReturn(List.of(a, b));

    int total = service.efetivarCancelamentosPendentes();

    assertThat(total).isEqualTo(2);
    assertThat(a.getStatus()).isEqualTo(ClientMembership.STATUS_CANCELADA);
    assertThat(b.getStatus()).isEqualTo(ClientMembership.STATUS_CANCELADA);
  }

  @Test
  void consultaExcluiQuemJaEstaCanceladoUsandoORelogioDaExecucao() {
    when(clientMembershipRepository.findPendentesDeCancelamento(any(), any()))
        .thenReturn(List.of());

    Instant antes = Instant.now();
    int total = service.efetivarCancelamentosPendentes();

    assertThat(total).isZero();

    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(clientMembershipRepository)
        .findPendentesDeCancelamento(statusCaptor.capture(), nowCaptor.capture());
    assertThat(statusCaptor.getValue()).isEqualTo(ClientMembership.STATUS_CANCELADA);
    assertThat(nowCaptor.getValue()).isAfterOrEqualTo(antes);
  }
}
