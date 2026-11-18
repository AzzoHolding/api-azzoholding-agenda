package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

/**
 * Cupons e vales-presente contra um Postgres REAL (V180/V181; item 6): os limites do cupom e o saldo do vale com
 * pedidos ao mesmo tempo, a devolucao e o vencimento.
 */
@SpringBootTest(properties = {
    "app.audit.seal.enabled=false",
    "app.meta.app-secret=segredo-de-teste",
    "app.pagamentos.consulta.initial-delay-ms=3600000"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CuponsEValesIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ServicoDeCupons cupons;
  @Autowired private ServicoDeVales vales;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void cupomDeUmUsoComCincoComandasAoMesmoTempoSoPassaUma() throws Exception {
    UUID tenant = criarTenant();
    UUID cupom = criarCupom(tenant, "UNICO1", 1, null);
    List<Boolean> resultados = emParalelo(5, i -> {
      cupons.reservar(tenant, cupom, UUID.randomUUID(), null, null, new BigDecimal("100.00"));
      return true;
    });
    assertThat(resultados.stream().filter(b -> b).count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select usos from cupons where id = ?", Integer.class, cupom)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "select count(*) from usos_de_cupom where cupom_id = ? and status = 'ATIVO'", Integer.class, cupom)).isEqualTo(1);
  }

  @Test
  void limitePorClienteValeMesmoAoMesmoTempo() throws Exception {
    UUID tenant = criarTenant();
    UUID cliente = criarCliente(tenant);
    UUID cupom = criarCupom(tenant, "UMPORCLI", null, 1);
    List<Boolean> resultados = emParalelo(3, i -> {
      cupons.reservar(tenant, cupom, UUID.randomUUID(), null, cliente, new BigDecimal("100.00"));
      return true;
    });
    assertThat(resultados.stream().filter(b -> b).count()).isEqualTo(1);
    // Outra cliente ainda usa.
    UUID outra = criarCliente(tenant);
    cupons.reservar(tenant, cupom, UUID.randomUUID(), null, outra, new BigDecimal("100.00"));
    assertThat(jdbc.queryForObject("select usos from cupons where id = ?", Integer.class, cupom)).isEqualTo(2);
  }

  @Test
  void liberarDevolveOUsoAoCupom() {
    UUID tenant = criarTenant();
    UUID cupom = criarCupom(tenant, "VOLTA1", 1, null);
    UUID comanda = UUID.randomUUID();
    cupons.reservar(tenant, cupom, comanda, null, null, new BigDecimal("50.00"));
    cupons.liberarDaComanda(tenant, comanda);
    assertThat(jdbc.queryForObject("select usos from cupons where id = ?", Integer.class, cupom)).isZero();
    // E pode ser usado de novo.
    cupons.reservar(tenant, cupom, UUID.randomUUID(), null, null, new BigDecimal("50.00"));
    assertThat(jdbc.queryForObject("select usos from cupons where id = ?", Integer.class, cupom)).isEqualTo(1);
  }

  @Test
  void doisPagamentosComOMesmoValeMaioresQueOSaldoSoUmPassa() throws Exception {
    UUID tenant = criarTenant();
    String codigo = criarVale(tenant, "70.00", LocalDate.now().plusMonths(6));
    List<Boolean> resultados = emParalelo(2, i -> {
      vales.debitar(tenant, codigo, new BigDecimal("50.00"), UUID.randomUUID());
      return true;
    });
    assertThat(resultados.stream().filter(b -> b).count()).isEqualTo(1);
    assertThat(saldo(tenant, codigo)).isEqualByComparingTo("20.00");
    assertThat(jdbc.queryForObject(
        "select count(*) from movimentos_de_vale m join vales_presente v on v.id = m.vale_id"
            + " where v.codigo = ? and m.tipo = 'USO'", Integer.class, codigo)).isEqualTo(1);
  }

  @Test
  void valeEsgotadoEVencidoVoltamAValerNaDevolucao() {
    UUID tenant = criarTenant();
    String codigo = criarVale(tenant, "40.00", LocalDate.now().plusDays(10));
    UUID comanda = UUID.randomUUID();
    var vale = vales.debitar(tenant, codigo, new BigDecimal("40.00"), comanda);
    assertThat(status(tenant, codigo)).isEqualTo("ESGOTADO");
    // Venceu enquanto isso.
    jdbc.update("update vales_presente set expira_em = ?, status = 'VENCIDO' where id = ?",
        java.sql.Date.valueOf(LocalDate.now().minusDays(1)), vale.getId());
    vales.devolver(tenant, vale.getId(), new BigDecimal("40.00"), comanda, "Cancelamento da comanda");
    assertThat(saldo(tenant, codigo)).isEqualByComparingTo("40.00");
    assertThat(status(tenant, codigo)).isEqualTo("ATIVO");
    LocalDate expira = jdbc.queryForObject("select expira_em from vales_presente where id = ?", LocalDate.class, vale.getId());
    assertThat(expira).isAfterOrEqualTo(LocalDate.now().plusDays(30));
  }

  @Test
  void rotinaDiariaVenceOValeAtivoPassadoDaValidade() {
    UUID tenant = criarTenant();
    String codigo = criarVale(tenant, "25.00", LocalDate.now().minusDays(2));
    vales.expirarVencidos();
    assertThat(status(tenant, codigo)).isEqualTo("VENCIDO");
    assertThat(jdbc.queryForObject(
        "select count(*) from movimentos_de_vale m join vales_presente v on v.id = m.vale_id"
            + " where v.codigo = ? and m.tipo = 'EXPIRACAO'", Integer.class, codigo)).isEqualTo(1);
  }

  // ─── apoio ─────────────────────────────────────────────────────────────────

  private interface Tarefa {
    Boolean executar(int i) throws Exception;
  }

  private List<Boolean> emParalelo(int n, Tarefa tarefa) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Boolean>> futuros = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      int k = i;
      futuros.add(pool.submit(() -> {
        largada.await();
        try {
          return tarefa.executar(k);
        } catch (IllegalArgumentException recusado) {
          return false;
        }
      }));
    }
    largada.countDown();
    List<Boolean> resultados = new ArrayList<>();
    for (Future<Boolean> f : futuros) resultados.add(f.get(60, TimeUnit.SECONDS));
    pool.shutdown();
    return resultados;
  }

  private BigDecimal saldo(UUID tenant, String codigo) {
    return jdbc.queryForObject("select saldo from vales_presente where tenant_id = ? and codigo = ?",
        BigDecimal.class, tenant, codigo);
  }

  private String status(UUID tenant, String codigo) {
    return jdbc.queryForObject("select status from vales_presente where tenant_id = ? and codigo = ?",
        String.class, tenant, codigo);
  }

  private UUID criarCupom(UUID tenant, String codigo, Integer limiteTotal, Integer limitePorCliente) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into cupons (id, tenant_id, codigo, tipo, valor, limite_total, limite_por_cliente)"
        + " values (?, ?, ?, 'PERCENTUAL', 10, ?, ?)", id, tenant, codigo, limiteTotal, limitePorCliente);
    return id;
  }

  private String criarVale(UUID tenant, String valor, LocalDate expira) {
    String codigo = br.com.phdigitalcode.azzo.agenda.pro.util.CodigoAleatorio.gerar(10);
    jdbc.update("insert into vales_presente (id, tenant_id, codigo, valor_inicial, saldo, expira_em)"
        + " values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), tenant, codigo, new BigDecimal(valor), new BigDecimal(valor),
        java.sql.Date.valueOf(expira));
    return codigo;
  }

  private UUID criarTenant() {
    return inserirComObrigatorias("tenants", Map.of());
  }

  private UUID criarCliente(UUID tenant) {
    return inserirComObrigatorias("clients", Map.of("tenant_id", tenant));
  }

  /** Mesmo apoio do FidelidadeIntegrationTest: preenche as colunas obrigatorias sem padrao. */
  private UUID inserirComObrigatorias(String tabela, Map<String, Object> fixos) {
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> colunas = jdbc.queryForList(
        "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'azzo_app' "
            + "AND table_name = ? AND is_nullable = 'NO' AND column_default IS NULL "
            + "AND is_identity = 'NO' AND is_generated = 'NEVER'", tabela);
    List<String> nomes = new ArrayList<>();
    List<Object> valores = new ArrayList<>();
    for (Map<String, Object> c : colunas) {
      String nome = (String) c.get("column_name");
      if (fixos.containsKey(nome)) continue;
      nomes.add(nome);
      valores.add(switch ((String) c.get("data_type")) {
        case "uuid" -> nome.equals("id") ? id : UUID.randomUUID();
        case "boolean" -> false;
        case "integer", "bigint", "smallint", "numeric" -> 0;
        case "timestamp with time zone", "timestamp without time zone" -> java.sql.Timestamp.from(Instant.now());
        case "date" -> java.sql.Date.valueOf(LocalDate.now());
        default -> nome + "-" + id.toString().substring(0, 8);
      });
    }
    if (!nomes.contains("id")) {
      nomes.add("id");
      valores.add(id);
    }
    fixos.forEach((k, v) -> {
      nomes.add(k);
      valores.add(v);
    });
    String sql = "INSERT INTO " + tabela + " (" + String.join(",", nomes) + ") VALUES ("
        + String.join(",", java.util.Collections.nCopies(nomes.size(), "?")) + ")";
    jdbc.execute((Connection con) -> {
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
