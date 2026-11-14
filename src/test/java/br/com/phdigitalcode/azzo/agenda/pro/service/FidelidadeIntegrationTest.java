package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pontos de fidelidade contra um Postgres REAL (migrations reais, V175): o resgate ao mesmo tempo
 * (relatorio de 14/11, Critico 1), o historico e o estorno.
 */
@SpringBootTest(properties = {
    "app.audit.seal.enabled=false",
    "app.meta.app-secret=segredo-de-teste",
    "app.pagamentos.consulta.initial-delay-ms=3600000"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FidelidadeIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ServicoDePontos pontos;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transacoes;

  @Test
  void doisResgatesAoMesmoTempoNaoGastamOsMesmosPontos() throws Exception {
    UUID tenant = criarTenant();
    UUID cliente = criarCliente(tenant, 41);
    TransactionTemplate tx = new TransactionTemplate(transacoes);

    // Dez tentativas de 40 pontos, todas juntas: so uma pode passar.
    int tentativas = 10;
    ExecutorService threads = Executors.newFixedThreadPool(tentativas);
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Boolean>> resultados = new ArrayList<>();
    for (int i = 0; i < tentativas; i++) {
      resultados.add(threads.submit(() -> {
        largada.await();
        try {
          tx.executeWithoutResult(s -> pontos.debitarResgate(tenant, cliente, UUID.randomUUID(), 40));
          return true;
        } catch (IllegalArgumentException e) {
          return false;
        }
      }));
    }
    largada.countDown();
    int passaram = 0;
    for (Future<Boolean> r : resultados) if (r.get(30, TimeUnit.SECONDS)) passaram++;
    threads.shutdown();

    assertThat(passaram).isEqualTo(1);
    assertThat(saldo(cliente)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movimentos_de_pontos WHERE client_id = ? AND tipo = 'RESGATE'",
        Integer.class, cliente)).isEqualTo(1);
    // O credito inicial ficou com 1 ponto sobrando (41 - 40).
    assertThat(jdbc.queryForObject("SELECT sum(pontos_restantes) FROM movimentos_de_pontos WHERE client_id = ?",
        Integer.class, cliente)).isEqualTo(1);
  }

  @Test
  void creditoDevolucaoEEstornoFicamNoHistorico() {
    UUID tenant = criarTenant();
    UUID cliente = criarCliente(tenant, 0);
    UUID venda = UUID.randomUUID();
    TransactionTemplate tx = new TransactionTemplate(transacoes);

    tx.executeWithoutResult(s -> pontos.creditarVenda(tenant, cliente, venda, 300, 90));
    Map<String, Object> credito = jdbc.queryForMap(
        "SELECT pontos, saldo_apos, pontos_restantes, expira_em FROM movimentos_de_pontos WHERE comanda_id = ?", venda);
    assertThat(credito.get("pontos")).isEqualTo(300);
    assertThat(credito.get("pontos_restantes")).isEqualTo(300);
    assertThat(credito.get("expira_em")).isNotNull();

    // A cliente gasta 250; o estorno da venda so consegue tirar 50 (ate zero, como hoje).
    tx.executeWithoutResult(s -> pontos.debitarResgate(tenant, cliente, UUID.randomUUID(), 250));
    assertThat(pontos.pontosJaUsadosDaVenda(tenant, cliente, venda, 300)).isEqualTo(250);
    tx.executeWithoutResult(s -> pontos.estornarCreditoDaVenda(tenant, cliente, venda, 300, ServicoDePontos.ModoDeEstorno.ATE_ZERO));
    assertThat(saldo(cliente)).isZero();

    // Com a escolha do dono, o saldo fica negativo e os proximos pontos pagam a divida primeiro.
    UUID outraVenda = UUID.randomUUID();
    tx.executeWithoutResult(s -> pontos.creditarVenda(tenant, cliente, outraVenda, 100, null));
    tx.executeWithoutResult(s -> pontos.debitarResgate(tenant, cliente, UUID.randomUUID(), 100));
    tx.executeWithoutResult(s -> pontos.estornarCreditoDaVenda(tenant, cliente, outraVenda, 100, ServicoDePontos.ModoDeEstorno.SALDO_NEGATIVO));
    assertThat(saldo(cliente)).isEqualTo(-100);
    tx.executeWithoutResult(s -> pontos.creditarVenda(tenant, cliente, UUID.randomUUID(), 150, null));
    assertThat(saldo(cliente)).isEqualTo(50);
    assertThat(jdbc.queryForObject("SELECT sum(pontos_restantes) FROM movimentos_de_pontos WHERE client_id = ?",
        Integer.class, cliente)).isEqualTo(50);
    // Com saldo negativo, nao ha resgate (o debito condicional exige saldo).
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movimentos_de_pontos WHERE client_id = ?", Integer.class, cliente))
        .isEqualTo(7);
  }

  /** A validade definida pelo dono: o credito vencido sai do saldo; o que nao venceu fica. */
  @Test
  void pontosVencidosSaemDoSaldoNaRotinaDiaria() {
    UUID tenant = criarTenant();
    UUID cliente = criarCliente(tenant, 0);
    TransactionTemplate tx = new TransactionTemplate(transacoes);
    UUID vendaAntiga = UUID.randomUUID();
    tx.executeWithoutResult(s -> pontos.creditarVenda(tenant, cliente, vendaAntiga, 120, 30));
    tx.executeWithoutResult(s -> pontos.creditarVenda(tenant, cliente, UUID.randomUUID(), 80, 30));
    // A primeira venda "foi ha 31 dias"; a cliente usou 20 dos 120 pontos dela.
    jdbc.update("UPDATE movimentos_de_pontos SET expira_em = now() - interval '1 day', created_at = now() - interval '31 days' "
        + "WHERE comanda_id = ?", vendaAntiga);
    tx.executeWithoutResult(s -> pontos.debitarResgate(tenant, cliente, UUID.randomUUID(), 20));
    assertThat(saldo(cliente)).isEqualTo(180);

    assertThat(pontos.expirarVencidos(500)).isGreaterThanOrEqualTo(1);
    // Venceram os 100 que sobraram da venda antiga (o resgate consumiu primeiro o que vencia antes).
    assertThat(saldo(cliente)).isEqualTo(80);
    assertThat(jdbc.queryForObject("SELECT pontos FROM movimentos_de_pontos WHERE client_id = ? AND tipo = 'EXPIRACAO'",
        Integer.class, cliente)).isEqualTo(-100);
    // Rodar de novo nao vence nada duas vezes.
    pontos.expirarVencidos(500);
    assertThat(saldo(cliente)).isEqualTo(80);
  }

  /** V178: devolucao com o vencimento original volta para o credito de onde saiu; a nova vira credito novo. */
  @Test
  void pontosDevolvidosVoltamComOVencimentoEscolhido() {
    UUID tenant = criarTenant();
    UUID cliente = criarCliente(tenant, 0);
    TransactionTemplate tx = new TransactionTemplate(transacoes);
    UUID venda = UUID.randomUUID();
    tx.executeWithoutResult(s -> pontos.creditarVenda(tenant, cliente, venda, 100, 30));
    java.sql.Timestamp vencimentoOriginal = jdbc.queryForObject(
        "SELECT expira_em FROM movimentos_de_pontos WHERE comanda_id = ?", java.sql.Timestamp.class, venda);

    UUID comanda1 = UUID.randomUUID();
    tx.executeWithoutResult(s -> pontos.debitarResgate(tenant, cliente, comanda1, 60));
    tx.executeWithoutResult(s -> pontos.devolverResgate(tenant, cliente, comanda1, 60, 30, "teste",
        ServicoDePontos.VencimentoDaDevolucao.ORIGINAL));
    // Os 60 voltaram para o credito original, com o mesmo vencimento; a devolucao nao criou credito novo.
    assertThat(jdbc.queryForObject("SELECT pontos_restantes FROM movimentos_de_pontos WHERE comanda_id = ? AND tipo = 'CREDITO'",
        Integer.class, venda)).isEqualTo(100);
    assertThat(jdbc.queryForObject("SELECT pontos_restantes FROM movimentos_de_pontos WHERE comanda_id = ? AND tipo = 'DEVOLUCAO'",
        Integer.class, comanda1)).isZero();
    assertThat(jdbc.queryForObject("SELECT expira_em FROM movimentos_de_pontos WHERE comanda_id = ? AND tipo = 'CREDITO'",
        java.sql.Timestamp.class, venda)).isEqualTo(vencimentoOriginal);

    UUID comanda2 = UUID.randomUUID();
    tx.executeWithoutResult(s -> pontos.debitarResgate(tenant, cliente, comanda2, 40));
    tx.executeWithoutResult(s -> pontos.devolverResgate(tenant, cliente, comanda2, 40, 90, "teste",
        ServicoDePontos.VencimentoDaDevolucao.NOVO));
    // Vencimento novo: credito novo de 40, vencendo em 90 dias; o original ficou com 60.
    assertThat(jdbc.queryForObject("SELECT pontos_restantes FROM movimentos_de_pontos WHERE comanda_id = ? AND tipo = 'DEVOLUCAO'",
        Integer.class, comanda2)).isEqualTo(40);
    assertThat(jdbc.queryForObject("SELECT pontos_restantes FROM movimentos_de_pontos WHERE comanda_id = ? AND tipo = 'CREDITO'",
        Integer.class, venda)).isEqualTo(60);
    assertThat(saldo(cliente)).isEqualTo(100);
  }

  private int saldo(UUID cliente) {
    return jdbc.queryForObject("SELECT loyalty_points FROM clients WHERE id = ?", Integer.class, cliente);
  }

  private UUID criarCliente(UUID tenant, int pontosIniciais) {
    UUID id = inserirComObrigatorias("clients", Map.of("tenant_id", tenant, "loyalty_points", pontosIniciais));
    if (pontosIniciais != 0) {
      jdbc.update("INSERT INTO movimentos_de_pontos (tenant_id, client_id, tipo, pontos, saldo_apos, pontos_restantes) "
          + "VALUES (?, ?, 'SALDO_INICIAL', ?, ?, ?)", tenant, id, pontosIniciais, pontosIniciais, Math.max(0, pontosIniciais));
    }
    return id;
  }

  private UUID criarTenant() {
    return inserirComObrigatorias("tenants", Map.of());
  }

  /** Insere uma linha preenchendo so as colunas obrigatorias (as chaves estrangeiras nao importam aqui). */
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
        case "date" -> java.sql.Date.valueOf(java.time.LocalDate.now());
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
