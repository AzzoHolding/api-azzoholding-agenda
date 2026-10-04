package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequestAuditContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Espelha {@code modules/audit/application/AuditService.java}. */
class AuditServiceTest {

  private AuditEventRepository auditEventRepository;
  private RequestAuditContext requestAuditContext;
  private AuditService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    auditEventRepository = mock(AuditEventRepository.class);
    requestAuditContext = mock(RequestAuditContext.class);
    service = new AuditService(auditEventRepository, new ObjectMapper(), new SimpleMeterRegistry(), requestAuditContext);
  }

  private AuditEventCommand baseCommand() {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = "finance";
    command.action = "payment_created";
    command.entityType = "PAYMENT";
    command.entityId = "123";
    return command;
  }

  @Test
  void recordSuccessPersisteComStatusSuccessEModuloAcaoEmMaiusculas() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());

    AuditEvent saved = service.recordSuccess(baseCommand());

    assertThat(saved.getStatus()).isEqualTo(AuditConstants.Status.SUCCESS);
    assertThat(saved.getModule()).isEqualTo("FINANCE");
    assertThat(saved.getAction()).isEqualTo("PAYMENT_CREATED");
    assertThat(saved.getEventHash()).isNotBlank();
    assertThat(saved.getPrevEventHash()).isNull();
    verify(auditEventRepository).save(saved);
  }

  @Test
  void recordErrorPersisteComStatusError() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEvent saved = service.recordError(baseCommand());
    assertThat(saved.getStatus()).isEqualTo(AuditConstants.Status.ERROR);
  }

  @Test
  void recordDeniedPersisteComStatusDenied() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEvent saved = service.recordDenied(baseCommand());
    assertThat(saved.getStatus()).isEqualTo(AuditConstants.Status.DENIED);
  }

  @Test
  void encadeiaComOHashDoUltimoEventoDoTenant() {
    AuditEvent previous = new AuditEvent();
    previous.setEventHash("hash-anterior");
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.of(previous));

    AuditEvent saved = service.recordSuccess(baseCommand());

    assertThat(saved.getPrevEventHash()).isEqualTo("hash-anterior");
  }

  @Test
  void mascaraCamposSensiveisNoBeforeEAfter() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEventCommand command = baseCommand();
    command.before = Map.of("password", "segredo123", "nome", "Fulano");
    command.after = Map.of("password", "novoSegredo", "nome", "Fulano");

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.getBeforeJson()).contains("\"***\"").doesNotContain("segredo123");
    assertThat(saved.getAfterJson()).contains("\"***\"").doesNotContain("novoSegredo");
    assertThat(saved.getBeforeJson()).contains("Fulano");
  }

  @Test
  void detectaCamposAlteradosEntreBeforeEAfter() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEventCommand command = baseCommand();
    command.before = Map.of("status", "PENDING", "valor", 10);
    command.after = Map.of("status", "PAID", "valor", 10);

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.isHasChanges()).isTrue();
    assertThat(saved.getChangedFieldsJson()).contains("status");
    assertThat(saved.getChangedFieldsJson()).doesNotContain("valor");
  }

  @Test
  void semAlteracaoQuandoBeforeEAfterSaoIguais() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEventCommand command = baseCommand();
    command.before = Map.of("status", "PAID");
    command.after = Map.of("status", "PAID");

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.isHasChanges()).isFalse();
  }

  @Test
  void marcaAlteracaoDeRaizQuandoApenasUmLadoEhNulo() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEventCommand command = baseCommand();
    command.before = null;
    command.after = Map.of("status", "PAID");

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.isHasChanges()).isTrue();
    assertThat(saved.getChangedFieldsJson()).contains("_root");
  }

  @Test
  void lancaQuandoCommandNulo() {
    assertThatThrownBy(() -> service.recordSuccess(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoTenantIdAusente() {
    AuditEventCommand command = baseCommand();
    command.tenantId = null;
    assertThatThrownBy(() -> service.recordSuccess(command)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoActionAusente() {
    AuditEventCommand command = baseCommand();
    command.action = " ";
    assertThatThrownBy(() -> service.recordSuccess(command)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lancaQuandoModuleAusente() {
    AuditEventCommand command = baseCommand();
    command.module = null;
    assertThatThrownBy(() -> service.recordSuccess(command)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void geraRequestIdQuandoNaoInformado() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEventCommand command = baseCommand();
    command.requestId = null;

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.getRequestId()).isNotBlank();
  }

  @Test
  void usaSourceChannelSystemPorPadrao() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    AuditEvent saved = service.recordSuccess(baseCommand());
    assertThat(saved.getSourceChannel()).isEqualTo(AuditConstants.SourceChannel.SYSTEM);
  }

  @Test
  void enriqueceComRequestAuditContextQuandoCamposNaoInformados() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    when(requestAuditContext.getRequestId()).thenReturn("req-do-contexto");
    when(requestAuditContext.getIpAddress()).thenReturn("10.0.0.1");
    when(requestAuditContext.getUserAgent()).thenReturn("agente-teste");

    AuditEvent saved = service.recordSuccess(baseCommand());

    assertThat(saved.getRequestId()).isEqualTo("req-do-contexto");
    assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
    assertThat(saved.getUserAgent()).isEqualTo("agente-teste");
  }

  @Test
  void naoEnriqueceQuandoRequestAuditContextForaDeEscopo() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    when(requestAuditContext.getRequestId()).thenThrow(new IllegalStateException("fora de escopo de requisicao"));
    AuditEventCommand command = baseCommand();
    command.requestId = "meu-request-id";

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.getRequestId()).isEqualTo("meu-request-id");
  }

  @Test
  void repropagaEQualquerExcecaoDoRepositorio() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    doThrow(new RuntimeException("falha no banco")).when(auditEventRepository).save(any());

    assertThatThrownBy(() -> service.recordSuccess(baseCommand()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha no banco");
  }

  /**
   * Ate 2026-09-16 os eventos de comanda, lancamento e caixa eram gravados SEM ator — a trilha dizia
   * o que mudou no dinheiro, mas nao quem mudou.
   */
  @Test
  void semAtorInformado_usaOUsuarioAutenticadoDaRequisicao() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    UUID usuario = UUID.randomUUID();
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal(
                    usuario, tenantId, "recepcao@salao.test", "Carla", 0L),
                null,
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_STAFF"))));
    try {
      AuditEvent saved = service.recordSuccess(baseCommand());

      assertThat(saved.getActorUserId()).isEqualTo(usuario);
      assertThat(saved.getActorRole()).isEqualTo("STAFF");
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @Test
  void atorInformadoPeloChamadorNaoESobrescrito() {
    when(auditEventRepository.findLastByTenant(tenantId)).thenReturn(java.util.Optional.empty());
    UUID informado = UUID.randomUUID();
    AuditEventCommand command = baseCommand();
    command.actorUserId = informado;

    AuditEvent saved = service.recordSuccess(command);

    assertThat(saved.getActorUserId()).isEqualTo(informado);
  }

  // ── redacao de dado pessoal do cliente (LGPD) ─────────────────────────────

  private AuditEvent eventoDeCliente(String before, String after) {
    AuditEvent evento = new AuditEvent();
    evento.setTenantId(tenantId);
    evento.setEntityType("CLIENT");
    evento.setEntityId("c-1");
    evento.setBeforeJson(before);
    evento.setAfterJson(after);
    evento.setEventHash("hash-original");
    evento.setPrevEventHash("hash-anterior");
    return evento;
  }

  @Test
  void redigirTrocaOValorPessoalMantendoAChaveEOQueNaoIdentifica() throws Exception {
    UUID clientId = UUID.randomUUID();
    AuditEvent evento =
        eventoDeCliente(
            null,
            "{\"id\":\"c-1\",\"name\":\"Ana Souza\",\"phone\":\"11999990000\","
                + "\"email\":null,\"city\":\"Sao Paulo\",\"totalVisits\":4}");
    when(auditEventRepository.findNaoRedigidosDaEntidade(tenantId, "CLIENT", clientId.toString()))
        .thenReturn(java.util.List.of(evento));

    int redigidos = service.redigirDadosPessoaisDoCliente(tenantId, clientId);

    assertThat(redigidos).isEqualTo(1);
    com.fasterxml.jackson.databind.JsonNode depois = new ObjectMapper().readTree(evento.getAfterJson());
    assertThat(depois.get("name").asText()).isEqualTo("[REDIGIDO]");
    assertThat(depois.get("phone").asText()).isEqualTo("[REDIGIDO]");
    assertThat(depois.get("city").asText()).isEqualTo("[REDIGIDO]");
    assertThat(depois.get("email").isNull()).isTrue(); // o que ja era nulo continua nulo
    assertThat(depois.get("id").asText()).isEqualTo("c-1");
    assertThat(depois.get("totalVisits").asInt()).isEqualTo(4);
    assertThat(evento.getAfterJson()).doesNotContain("Ana Souza", "11999990000", "Sao Paulo");
    assertThat(evento.getRedactedAt()).isNotNull();
    // A cadeia segue ligada: o hash NAO e recalculado (cobre o conteudo original).
    assertThat(evento.getEventHash()).isEqualTo("hash-original");
    assertThat(evento.getPrevEventHash()).isEqualTo("hash-anterior");
    verify(auditEventRepository).saveAll(java.util.List.of(evento));
  }

  /** Sem ligar a excecao do gatilho antes do UPDATE, o banco recusa ("append-only table"). */
  @Test
  void redigirLigaAExcecaoDoGatilhoAntesDeGravar() {
    UUID clientId = UUID.randomUUID();
    AuditEvent evento = eventoDeCliente(null, "{\"name\":\"Ana\"}");
    when(auditEventRepository.findNaoRedigidosDaEntidade(tenantId, "CLIENT", clientId.toString()))
        .thenReturn(java.util.List.of(evento));

    service.redigirDadosPessoaisDoCliente(tenantId, clientId);

    org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(auditEventRepository);
    ordem.verify(auditEventRepository).habilitarRedacaoNestaTransacao();
    ordem.verify(auditEventRepository).saveAll(any());
  }

  @Test
  void redigirSemEventosNaoTocaNoBancoNemLigaAExcecao() {
    UUID clientId = UUID.randomUUID();
    when(auditEventRepository.findNaoRedigidosDaEntidade(tenantId, "CLIENT", clientId.toString()))
        .thenReturn(java.util.List.of());

    assertThat(service.redigirDadosPessoaisDoCliente(tenantId, clientId)).isZero();
    org.mockito.Mockito.verify(auditEventRepository, org.mockito.Mockito.never()).saveAll(any());
    org.mockito.Mockito.verify(auditEventRepository, org.mockito.Mockito.never())
        .habilitarRedacaoNestaTransacao();
  }

  @Test
  void redigirComArgumentoNuloNaoFazNada() {
    assertThat(service.redigirDadosPessoaisDoCliente(null, UUID.randomUUID())).isZero();
    assertThat(service.redigirDadosPessoaisDoCliente(tenantId, null)).isZero();
  }

  /** Engolir deixaria o dado pessoal no lugar e quem chamou acharia que terminou. */
  @Test
  void redigirComJsonInvalidoFalhaEmVezDeIgnorar() {
    UUID clientId = UUID.randomUUID();
    when(auditEventRepository.findNaoRedigidosDaEntidade(tenantId, "CLIENT", clientId.toString()))
        .thenReturn(java.util.List.of(eventoDeCliente(null, "{nao-e-json")));

    assertThatThrownBy(() -> service.redigirDadosPessoaisDoCliente(tenantId, clientId))
        .isInstanceOf(IllegalStateException.class);
  }
}
