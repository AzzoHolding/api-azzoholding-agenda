package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ClienteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * A auditoria do cliente NAO guarda dado pessoal (achado de 02/10/2026): a trilha dura 365 dias e e
 * encadeada por hash, entao nome/telefone/endereco ali sobreviviam a anonimizacao e a exclusao.
 */
class ClienteServiceAuditoriaTest {

  private static final Set<String> CHAVES_PESSOAIS =
      Set.of(
          "name", "email", "phone", "avatar", "birthDate", "notes", "zipCode", "street", "number",
          "complement", "neighborhood", "city", "state", "cpfCnpj");

  private final UUID tenantId = UUID.randomUUID();
  private ClienteRepository clienteRepository;
  private AuditService auditService;
  private ClienteService service;
  private Cliente cliente;

  @BeforeEach
  void setUp() {
    clienteRepository = mock(ClienteRepository.class);
    ClienteStatsRepository statsRepository = mock(ClienteStatsRepository.class);
    when(statsRepository.findStatsByTenantAndClient(any(), any()))
        .thenReturn(ClienteStatsRepository.ClienteStats.EMPTY);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    auditService = mock(AuditService.class);
    service =
        new ClienteService(
            clienteRepository,
            statsRepository,
            contextoTenant,
            auditService,
            mock(MinioStorageService.class));
    ReflectionTestUtils.setField(service, "vinculosDeExclusao", mock(VinculosDeExclusao.class));

    cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(tenantId);
    cliente.setName("Ana Souza");
    cliente.setPhone("11999990000");
    cliente.setEmail("ana@exemplo.com");
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), tenantId))
        .thenReturn(Optional.of(cliente));
    // Como o banco: quem ainda nao tem id ganha um ao salvar.
    when(clienteRepository.save(any(Cliente.class)))
        .thenAnswer(
            inv -> {
              Cliente salvo = inv.getArgument(0);
              if (salvo.getId() == null) salvo.setId(UUID.randomUUID());
              return salvo;
            });
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapa(Object valor) {
    return (Map<String, Object>) valor;
  }

  private AuditEventCommand eventoGravado() {
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    return captor.getValue();
  }

  private void assertSemDadoPessoal(Object retrato) {
    Map<String, Object> m = mapa(retrato);
    assertThat(m.keySet()).doesNotContainAnyElementsOf(CHAVES_PESSOAIS);
    assertThat(m.values().toString()).doesNotContain("Ana Souza", "11999990000", "ana@exemplo.com");
  }

  @Test
  void criarNaoGravaDadoPessoalNaAuditoria() {
    ClienteRequest req = new ClienteRequest();
    req.name = "Ana Souza";
    req.email = "ana@exemplo.com";
    req.phone = "11999990000";

    service.criar(req);

    AuditEventCommand evento = eventoGravado();
    assertThat(evento.action).isEqualTo("CLIENT_CREATE");
    assertSemDadoPessoal(evento.after);
    assertThat(mapa(evento.after)).containsKeys("id", "totalVisits", "totalSpent", "lastVisit");
  }

  @Test
  void atualizarGravaSoOsNomesDosCamposPessoaisAlterados() {
    ClienteRequest req = new ClienteRequest();
    req.name = "Ana Souza";
    req.email = "ana@exemplo.com";
    req.phone = "11988887777"; // so o telefone mudou

    service.atualizar(cliente.getId(), req);

    AuditEventCommand evento = eventoGravado();
    assertThat(evento.action).isEqualTo("CLIENT_UPDATE");
    assertThat(mapa(evento.after).get("camposPessoaisAlterados")).isEqualTo(List.of("phone"));
    assertThat(mapa(evento.after).values().toString()).doesNotContain("11988887777", "11999990000");
    assertSemDadoPessoal(evento.before);
  }

  @Test
  void atualizarSemMudarNadaPessoalNaoMencionaCampos() {
    ClienteRequest req = new ClienteRequest();
    req.name = "Ana Souza";
    req.email = "ana@exemplo.com";
    req.phone = "11999990000";

    service.atualizar(cliente.getId(), req);

    assertThat(mapa(eventoGravado().after)).doesNotContainKey("camposPessoaisAlterados");
  }

  @Test
  void excluirRedigeOsEventosAntigosDoCliente() {
    service.deletar(cliente.getId());

    AuditEventCommand evento = eventoGravado();
    assertThat(evento.action).isEqualTo("CLIENT_DELETE");
    assertSemDadoPessoal(evento.before);
    verify(auditService).redigirDadosPessoaisDoCliente(tenantId, cliente.getId());
  }
}
