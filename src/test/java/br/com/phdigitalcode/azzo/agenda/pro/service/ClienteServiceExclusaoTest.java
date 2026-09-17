package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cliente com historico nao se exclui (analise de 2026-09-16, A6): o banco levava junto os pacotes
 * COMPRADOS e as assinaturas, e o resto estourava como "erro inesperado". O caminho para tirar os
 * dados pessoais e a anonimizacao da LGPD.
 */
class ClienteServiceExclusaoTest {

  private ClienteRepository clienteRepository;
  private VinculosDeExclusao vinculosDeExclusao;
  private ClienteService service;

  private final UUID tenantId = UUID.randomUUID();
  private Cliente cliente;

  @BeforeEach
  void setUp() {
    clienteRepository = mock(ClienteRepository.class);
    ClienteStatsRepository clienteStatsRepository = mock(ClienteStatsRepository.class);
    when(clienteStatsRepository.findStatsByTenantAndClient(any(), any()))
        .thenReturn(ClienteStatsRepository.ClienteStats.EMPTY);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    vinculosDeExclusao = mock(VinculosDeExclusao.class);

    service =
        new ClienteService(
            clienteRepository,
            clienteStatsRepository,
            contextoTenant,
            mock(AuditService.class),
            mock(MinioStorageService.class));
    ReflectionTestUtils.setField(service, "vinculosDeExclusao", vinculosDeExclusao);

    cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(tenantId);
    cliente.setName("Marina");
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId))
        .thenReturn(Optional.of(cliente));
  }

  @Test
  void clienteComPacoteCompradoNaoSeExclui() {
    when(vinculosDeExclusao.doCliente(tenantId, cliente.getId()))
        .thenReturn(List.of("pacotes comprados"));

    assertThatThrownBy(() -> service.deletar(cliente.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pacotes comprados")
        .hasMessageContaining("anonimizacao");
    verify(clienteRepository, never()).delete(any(Cliente.class));
  }

  @Test
  void clienteSemHistoricoSeExclui() {
    when(vinculosDeExclusao.doCliente(tenantId, cliente.getId())).thenReturn(List.of());

    service.deletar(cliente.getId());

    verify(clienteRepository).delete(cliente);
  }
}
