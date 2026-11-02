package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ClienteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteDetailResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;

class ClienteServiceAcessoTest {

  private final UUID tenantId = UUID.randomUUID();
  private ClienteRepository clienteRepository;
  private ClienteStatsRepository statsRepository;
  private AuthenticatedUser authenticatedUser;
  private PermissionService permissionService;
  private ClienteService service;
  private Cliente cliente;

  @BeforeEach
  void setUp() {
    clienteRepository = mock(ClienteRepository.class);
    statsRepository = mock(ClienteStatsRepository.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    permissionService = mock(PermissionService.class);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    service =
        new ClienteService(
            clienteRepository,
            statsRepository,
            contextoTenant,
            mock(AuditService.class),
            mock(MinioStorageService.class),
            authenticatedUser,
            permissionService);

    cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(tenantId);
    cliente.setName("Marina");
    cliente.setPhone("11999990000");
    cliente.setEmail("marina@exemplo.com");
    cliente.setCpfCnpj("52998224725");
    cliente.setNotes("alergia a amonia");
    when(clienteRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of(cliente));
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId)).thenReturn(Optional.of(cliente));
    when(statsRepository.findStatsByTenant(tenantId)).thenReturn(Map.of());
    when(statsRepository.findStatsByTenantAndClient(tenantId, cliente.getId()))
        .thenReturn(ClienteStatsRepository.ClienteStats.EMPTY);
  }

  private void logadoComoProfissional() {
    when(authenticatedUser.isProfessional()).thenReturn(true);
  }

  private void assertSoNomeETelefone(ClienteListResponse resposta) {
    assertThat(resposta.name).isEqualTo("Marina");
    assertThat(resposta.phone).isEqualTo("11999990000");
    assertThat(resposta.cpfCnpj).isNull();
    assertThat(resposta.email).isNull();
    assertThat(resposta.notes).isNull();
    assertThat(resposta.totalSpent).isNull();
  }

  @Test
  @DisplayName("Profissional lista clientes so com nome e telefone")
  void profissionalListaSoNomeETelefone() {
    logadoComoProfissional();

    assertSoNomeETelefone(service.listar().get(0));
  }

  @Test
  @DisplayName("A ficha do cliente tambem sai so com nome e telefone para o profissional")
  void profissionalLeFichaReduzida() {
    logadoComoProfissional();

    ClienteDetailResponse ficha = service.obterPorId(cliente.getId());

    assertSoNomeETelefone(ficha);
    assertThat(ficha.stats).isNull();
    assertThat(ficha.recentAppointments).isEmpty();
    assertThat(ficha.careTimeline).isEmpty();
  }

  @Test
  @DisplayName("Profissional com a tela Clientes no perfil ve a ficha completa")
  void perfilComClientesVeTudo() {
    logadoComoProfissional();
    when(permissionService.possuiPermissao("client:full")).thenReturn(true);

    ClienteListResponse resposta = service.listar().get(0);

    assertThat(resposta.cpfCnpj).isEqualTo("52998224725");
    assertThat(resposta.email).isEqualTo("marina@exemplo.com");
  }

  @Test
  @DisplayName("Dono e recepcao nao sao afetados")
  void donoVeTudo() {
    when(authenticatedUser.isProfessional()).thenReturn(false);

    assertThat(service.listar().get(0).notes).isEqualTo("alergia a amonia");
  }

  @Test
  @DisplayName("Profissional nao altera cliente nem le indicadores")
  void profissionalNaoAltera() {
    logadoComoProfissional();

    assertThatThrownBy(() -> service.atualizar(cliente.getId(), new ClienteRequest()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.indicadores(cliente.getId()))
        .isInstanceOf(AccessDeniedException.class);

    verify(clienteRepository, never()).save(any());
  }
}
