package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LgpdDataSubjectRequestRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class ServicoExportacaoTitularTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  private ClienteRepository clienteRepository;
  private LgpdDataSubjectRequestRepository requestRepository;
  private EncryptionService encryptionService;
  private AuditService auditService;
  private EntityManager entityManager;
  private ServicoExportacaoTitular servico;

  @BeforeEach
  void setUp() {
    ContextoTenant contexto = mock(ContextoTenant.class);
    when(contexto.obterTenantIdOuFalhar()).thenReturn(tenantId);
    clienteRepository = mock(ClienteRepository.class);
    TenantRepository tenantRepository = mock(TenantRepository.class);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
    requestRepository = mock(LgpdDataSubjectRequestRepository.class);
    when(requestRepository.findByTenantAndProtocol(any(), any())).thenReturn(Optional.empty());
    encryptionService = mock(EncryptionService.class);
    auditService = mock(AuditService.class);
    servico = new ServicoExportacaoTitular(
        contexto, mock(AuthenticatedUser.class), clienteRepository, tenantRepository, requestRepository,
        encryptionService, auditService);

    entityManager = mock(EntityManager.class);
    ReflectionTestUtils.setField(servico, "entityManager", entityManager);
    Query vazia = consulta(List.of());
    when(entityManager.createNativeQuery(anyString())).thenReturn(vazia);
  }

  private static Query consulta(List<?> linhas) {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList()).thenReturn((List) linhas);
    return query;
  }

  private Cliente cliente() {
    Cliente c = new Cliente();
    c.setId(clientId);
    c.setTenantId(tenantId);
    c.setName("Marina Alves");
    c.setEmail("marina@exemplo.com");
    c.setPhone("21987599613");
    c.setCpfCnpj("52998224725");
    return c;
  }

  @Test
  @SuppressWarnings("unchecked")
  void exportaFichaMensagensDecifradasERegistraOAtendimento() {
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente()));
    Query mensagens = consulta(List.<Object[]>of(new Object[] {"2026-10-13T10:00:00Z", "INBOUND", "cifrado"}));
    when(entityManager.createNativeQuery(argThat((String sql) -> sql != null && sql.contains("FROM chat_messages"))))
        .thenReturn(mensagens);
    when(encryptionService.decrypt("cifrado")).thenReturn("Oi, quero remarcar");

    Map<String, Object> exportacao = servico.exportar(clientId);

    Map<String, Object> titular = (Map<String, Object>) exportacao.get("titular");
    assertThat(titular).containsEntry("nome", "Marina Alves").containsEntry("documento", "52998224725");
    List<Map<String, Object>> conversa = (List<Map<String, Object>>) exportacao.get("mensagens");
    assertThat(conversa).singleElement()
        .satisfies(m -> assertThat(m).containsEntry("texto", "Oi, quero remarcar").containsEntry("sentido", "do cliente"));
    assertThat(exportacao).containsKeys(
        "agendamentos", "comandas", "notasDeAtendimento", "pacotes", "assinaturas", "notasFiscais",
        "notificacoes", "consentimentos", "sobreOTratamento", "protocolo");

    ArgumentCaptor<LgpdDataSubjectRequest> pedido = ArgumentCaptor.forClass(LgpdDataSubjectRequest.class);
    verify(requestRepository).save(pedido.capture());
    assertThat(pedido.getValue().getRequestType()).isEqualTo("ACESSO");
    assertThat(pedido.getValue().getStatus()).isEqualTo("ENCERRADO");
    assertThat((String) exportacao.get("protocolo")).startsWith("LGPD-");
    verify(auditService).recordSuccess(argThat((AuditEventCommand c) -> "LGPD_CLIENT_DATA_EXPORT".equals(c.action)));
  }

  @Test
  void clienteDeOutroSalaoNaoExporta() {
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servico.exportar(clientId)).isInstanceOf(ResponseStatusException.class);
  }
}
