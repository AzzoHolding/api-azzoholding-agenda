package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;

class ServicoOptOutDoTitularTest {

  @Test
  void entendeAsMesmasPalavrasDoWhatsapp() {
    assertThat(ServicoOptOutDoTitular.ehPedidoDeOptOut(" pare ")).isTrue();
    assertThat(ServicoOptOutDoTitular.ehPedidoDeOptOut("Não quero")).isTrue();
    assertThat(ServicoOptOutDoTitular.ehPedidoDeOptOut("STOP")).isTrue();
    // CANCELAR e a resposta do lembrete para cancelar o horario, nao as mensagens (INT-006).
    assertThat(ServicoOptOutDoTitular.ehPedidoDeOptOut("CANCELAR")).isFalse();
    assertThat(ServicoOptOutDoTitular.ehPedidoDeOptOut("pare de me mandar promocao")).isFalse();
    assertThat(ServicoOptOutDoTitular.ehPedidoDeOptOut(null)).isFalse();
  }

  @Test
  void registraOptOutECancelaAReativacao() {
    ClienteRepository clientes = mock(ClienteRepository.class);
    WhatsAppBookingReactivationCycleRepository ciclos = mock(WhatsAppBookingReactivationCycleRepository.class);
    WhatsAppBookingReactivationService reativacao = mock(WhatsAppBookingReactivationService.class);
    Cliente cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(UUID.randomUUID());
    cliente.setWhatsappOptIn(true);
    WhatsAppBookingReactivationCycleEntity ciclo = new WhatsAppBookingReactivationCycleEntity();
    when(ciclos.listActiveByTenantAndClient(cliente.getTenantId(), cliente.getId())).thenReturn(List.of(ciclo));

    new ServicoOptOutDoTitular(clientes, ciclos, reativacao).registrar(cliente);

    assertThat(cliente.getWhatsappOptOut()).isTrue();
    assertThat(cliente.getWhatsappOptOutAt()).isNotNull();
    assertThat(cliente.getWhatsappOptIn()).isFalse();
    verify(clientes).save(cliente);
    verify(reativacao).cancelCycle(ciclo, "OPT_OUT");
  }
}
