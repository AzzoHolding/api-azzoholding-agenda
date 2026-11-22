package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditChainVerification;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;

/**
 * A trilha de auditoria contra um Postgres REAL, com as migrations reais (V164): escrita
 * simultanea, selo, conferencia, expurgo com ancora e adulteracao direta no banco.
 *
 * <p>Ate 07/11/2026 a escrita simultanea bifurcava a cadeia (52 bifurcacoes em producao). Aqui 20
 * threads gravam ao mesmo tempo e a cadeia tem de sair sem buraco e sem bifurcacao.
 */
@SpringBootTest(properties = {"app.audit.seal.enabled=false", "app.meta.app-secret=segredo-de-teste"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CadeiaDaAuditoriaIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AuditService auditService;
  @Autowired private SeladorDaAuditoria selador;
  @Autowired private ConferenciaDaAuditoria conferencia;
  @Autowired private AuditRetentionPurgeService expurgo;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void escritaSimultaneaSelaSemBifurcarEAdulteracaoEDetectada() throws Exception {
    UUID tenant = criarTenant();

    ExecutorService pool = Executors.newFixedThreadPool(20);
    List<Future<?>> tarefas = new ArrayList<>();
    for (int t = 0; t < 20; t++) {
      int thread = t;
      tarefas.add(pool.submit(() -> {
        for (int i = 0; i < 10; i++) {
          AuditEventCommand c = new AuditEventCommand();
          c.tenantId = tenant;
          c.module = "FINANCE";
          c.action = "PAGAMENTO";
          c.entityType = "PAYMENT";
          c.entityId = thread + "-" + i;
          c.ipAddress = "10.0.0." + thread;
          c.actorUserId = UUID.randomUUID();
          c.after = Map.of("valor", i);
          auditService.recordSuccess(c);
        }
        return null;
      }));
    }
    for (Future<?> f : tarefas) f.get();
    pool.shutdown();

    assertThat(selador.selarPendentes()).isEqualTo(200);
    Long bifurcacoes = jdbc.queryForObject(
        "SELECT count(*) FROM (SELECT prev_event_hash FROM audit_events WHERE tenant_id = ? "
            + "GROUP BY prev_event_hash HAVING count(*) > 1) b", Long.class, tenant);
    assertThat(bifurcacoes).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT max(chain_seq) FROM audit_events WHERE tenant_id = ?", Long.class, tenant)).isEqualTo(200);

    AuditChainVerification integra = conferencia.conferir(tenant, "TESTE", false);
    assertThat(integra.getStatus()).isEqualTo(ConferenciaDaAuditoria.INTEGRA);
    assertThat(integra.getSealedEvents()).isEqualTo(200);
    assertThat(integra.getLastChainSeq()).isEqualTo(200);

    // Expurgo no meio da cadeia: a ancora mantem a conferencia integra.
    Instant corte = jdbc.queryForObject(
        "SELECT created_at FROM audit_events WHERE tenant_id = ? AND chain_seq = 101", Instant.class, tenant);
    long removidos = expurgo.purgeTenant(tenant, corte, 1, "exec-teste");
    assertThat(removidos).isPositive();
    assertThat(conferencia.conferir(tenant, "TESTE", false).getStatus()).isEqualTo(ConferenciaDaAuditoria.INTEGRA);
    Long ancora = jdbc.queryForObject(
        "SELECT chain_anchor_seq FROM audit_retention_events WHERE tenant_id = ?", Long.class, tenant);
    Long primeiro = jdbc.queryForObject(
        "SELECT min(chain_seq) FROM audit_events WHERE tenant_id = ?", Long.class, tenant);
    assertThat(primeiro).isEqualTo(ancora + 1);

    // A aplicacao nao consegue alterar nem apagar.
    assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET ip_address = '1.2.3.4' WHERE tenant_id = ?", tenant))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_events")).hasMessageContaining("TRUNCATE blocked");

    // Quem passa por cima dos gatilhos (superusuario) e pego pela conferencia.
    jdbc.execute((java.sql.Connection con) -> {
      try (var st = con.createStatement()) {
        st.execute("SET session_replication_role = replica");
        st.execute("UPDATE audit_events SET ip_address = '1.2.3.4' WHERE chain_seq = 150 AND tenant_id = '" + tenant + "'");
        st.execute("SET session_replication_role = origin");
      }
      return null;
    });
    AuditChainVerification violada = conferencia.conferir(tenant, "TESTE", false);
    assertThat(violada.getStatus()).isEqualTo(ConferenciaDaAuditoria.VIOLADA);
    assertThat(violada.getFirstFailureReason()).contains("event_hash");
  }

  /** Um salao minimo: preenche so as colunas obrigatorias sem padrao. */
  private UUID criarTenant() {
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> colunas = jdbc.queryForList(
        "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'azzo_app' "
            + "AND table_name = 'tenants' AND is_nullable = 'NO' AND column_default IS NULL "
            + "AND is_identity = 'NO' AND is_generated = 'NEVER'");
    List<String> nomes = new ArrayList<>();
    List<Object> valores = new ArrayList<>();
    for (Map<String, Object> coluna : colunas) {
      String nome = (String) coluna.get("column_name");
      String tipo = (String) coluna.get("data_type");
      nomes.add(nome);
      valores.add(switch (tipo) {
        case "uuid" -> nome.equals("id") ? id : UUID.randomUUID();
        case "boolean" -> false;
        case "integer", "bigint", "smallint", "numeric" -> 0;
        case "timestamp with time zone", "timestamp without time zone" -> java.sql.Timestamp.from(Instant.now());
        case "date" -> java.sql.Date.valueOf(java.time.LocalDate.now());
        default -> nome + "-" + id.toString().substring(0, 8);
      });
    }
    if (!nomes.contains("id")) {
      nomes.add("id");
      valores.add(id);
    }
    String sql = "INSERT INTO tenants (" + String.join(",", nomes) + ") VALUES ("
        + String.join(",", java.util.Collections.nCopies(nomes.size(), "?")) + ")";
    // Fixture: as chaves estrangeiras do salao (plano, status...) nao importam para a trilha.
    jdbc.execute((java.sql.Connection con) -> {
      try (var st = con.createStatement()) {
        st.execute("SET session_replication_role = replica");
      }
      try (var ps = con.prepareStatement(sql)) {
        for (int i = 0; i < valores.size(); i++) ps.setObject(i + 1, valores.get(i));
        ps.executeUpdate();
      } finally {
        try (var st = con.createStatement()) {
          st.execute("SET session_replication_role = origin");
        }
      }
      return null;
    });
    return id;
  }
}
