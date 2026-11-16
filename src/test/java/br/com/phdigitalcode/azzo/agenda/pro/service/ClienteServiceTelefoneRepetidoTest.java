package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ClienteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;

/** Analise de 15/11/2026, defeito A2: telefone repetido dava 500 ("erro inesperado"). */
class ClienteServiceTelefoneRepetidoTest {

  private final UUID tenantId = UUID.randomUUID();
  private ClienteRepository clienteRepository;
  private ClienteService service;
  private Cliente marina;

  @BeforeEach
  void setUp() {
    clienteRepository = mock(ClienteRepository.class);
    ClienteStatsRepository statsRepository = mock(ClienteStatsRepository.class);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(statsRepository.findStatsByTenantAndClient(any(), any()))
        .thenReturn(ClienteStatsRepository.ClienteStats.EMPTY);
    service = new ClienteService(
        clienteRepository,
        statsRepository,
        contextoTenant,
        mock(AuditService.class),
        mock(MinioStorageService.class),
        mock(AuthenticatedUser.class),
        mock(PermissionService.class));

    marina = new Cliente();
    marina.setId(UUID.randomUUID());
    marina.setTenantId(tenantId);
    marina.setName("Marina");
    marina.setPhone("11999990000");
    when(clienteRepository.findByIdAndTenantId(marina.getId(), tenantId)).thenReturn(Optional.of(marina));
  }

  private static ClienteRequest pedido(String nome, String telefone) {
    ClienteRequest req = new ClienteRequest();
    req.name = nome;
    req.phone = telefone;
    return req;
  }

  @Test
  @DisplayName("criar com telefone de outro cliente responde 409 com o nome de quem ja tem o telefone")
  void criarComTelefoneRepetidoRecusaComMensagemClara() {
    when(clienteRepository.findFirstByTenantIdAndPhone(eq(tenantId), anyString())).thenReturn(Optional.of(marina));

    assertThatThrownBy(() -> service.criar(pedido("Marina 2", "(11) 99999-0000")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Já existe um cliente com este telefone: Marina")
        .satisfies(e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(409));
    verify(clienteRepository, never()).save(any());
  }

  @Test
  @DisplayName("editar o proprio cliente sem trocar o telefone continua aceito")
  void editarOProprioClienteNaoConflitaConsigoMesmo() {
    when(clienteRepository.findFirstByTenantIdAndPhone(eq(tenantId), anyString())).thenReturn(Optional.of(marina));

    service.atualizar(marina.getId(), pedido("Marina Souza", "11999990000"));

    verify(clienteRepository).save(marina);
    assertThat(marina.getName()).isEqualTo("Marina Souza");
  }

  @Test
  @DisplayName("editar para o telefone de outro cliente responde 409 e nao grava")
  void editarParaTelefoneDeOutroRecusa() {
    Cliente joana = new Cliente();
    joana.setId(UUID.randomUUID());
    joana.setTenantId(tenantId);
    joana.setName("Joana");
    joana.setPhone("11988887777");
    when(clienteRepository.findByIdAndTenantId(joana.getId(), tenantId)).thenReturn(Optional.of(joana));
    when(clienteRepository.findFirstByTenantIdAndPhone(eq(tenantId), anyString())).thenReturn(Optional.of(marina));

    assertThatThrownBy(() -> service.atualizar(joana.getId(), pedido("Joana", "11999990000")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Marina");
    verify(clienteRepository, never()).save(any());
    assertThat(joana.getPhone()).isEqualTo("11988887777");
  }
}
