package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Criterio;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Metricas;

/**
 * As metricas do ranking contra um Postgres REAL, com as migrations reais (V166): a consulta junta
 * comanda, agendamento, horario de trabalho e produto, e so o banco de verdade prova que cada
 * numero sai certo.
 */
@SpringBootTest(properties = {"app.audit.seal.enabled=false", "app.meta.app-secret=segredo-de-teste"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RankingDeProfissionaisIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ServicoRankingDeProfissionais servico;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transacao;

  private final LocalDate inicio = LocalDate.of(2026, 10, 1);
  private final LocalDate fim = LocalDate.of(2026, 10, 31);

  @Test
  void cadaMetricaSaiCertaDoBanco() {
    UUID tenant = inserir("tenants", Map.of());
    UUID ana = inserir("professionals", Map.of("tenant_id", tenant, "name", "Ana", "is_active", true));
    UUID bia = inserir("professionals", Map.of("tenant_id", tenant, "name", "Bia", "is_active", true));
    UUID inativa = inserir("professionals", Map.of("tenant_id", tenant, "name", "Saiu", "is_active", false));
    UUID clienteAntigo = inserir("clients", Map.of("tenant_id", tenant, "name", "Cliente Antigo"));
    UUID clienteNovo = inserir("clients", Map.of("tenant_id", tenant, "name", "Cliente Novo"));

    // Antes do periodo: o cliente antigo ja era da Ana.
    agendamento(tenant, ana, clienteAntigo, LocalDate.of(2026, 9, 10), "Concluido", "10:00", "11:00", "100");
    // No periodo, Ana: 2 concluidos (antigo voltou + novo), 1 falta. Sem comanda: vale o preco do agendamento.
    agendamento(tenant, ana, clienteAntigo, LocalDate.of(2026, 10, 5), "Concluido", "10:00", "11:00", "150");
    agendamento(tenant, ana, clienteNovo, LocalDate.of(2026, 10, 6), "Concluido", "10:00", "10:30", "50");
    agendamento(tenant, ana, clienteNovo, LocalDate.of(2026, 10, 7), "Nao compareceu", "10:00", "10:30", "50");
    // Bia: 1 cancelado e 1 concluido com comanda fechada (o valor e o da comanda, com desconto).
    agendamento(tenant, bia, clienteAntigo, LocalDate.of(2026, 10, 8), "Cancelado", "09:00", "10:00", "80");
    UUID agBia = agendamento(tenant, bia, clienteAntigo, LocalDate.of(2026, 10, 9), "Concluido", "09:00", "10:00", "200");
    UUID comanda = inserir("comandas", Map.of(
        "tenant_id", tenant, "appointment_id", agBia, "status", "FECHADA",
        "subtotal", new BigDecimal("250"), "desconto", new BigDecimal("50"),
        "closed_at", java.sql.Timestamp.from(Instant.parse("2026-10-09T15:00:00Z"))));
    inserir("comanda_itens", Map.of("tenant_id", tenant, "comanda_id", comanda, "tipo", "SERVICO",
        "professional_id", bia, "total", new BigDecimal("200")));
    inserir("comanda_itens", Map.of("tenant_id", tenant, "comanda_id", comanda, "tipo", "PRODUTO",
        "professional_id", bia, "total", new BigDecimal("50")));

    List<Metricas> metricas = transacao.execute(s ->
        servico.metricas(tenant, new ServicoRankingDeProfissionais.Periodo(inicio, fim)));
    Map<String, Metricas> porNome = metricas.stream().collect(Collectors.toMap(Metricas::nome, m -> m));

    assertThat(porNome).containsOnlyKeys("Ana", "Bia"); // a inativa sem movimento fica de fora
    Metricas a = porNome.get("Ana");
    assertThat(a.valor(Criterio.FATURAMENTO_SERVICOS)).isEqualByComparingTo("200");
    assertThat(a.valor(Criterio.ATENDIMENTOS_CONCLUIDOS)).isEqualByComparingTo("2");
    assertThat(a.valor(Criterio.CLIENTES_ATENDIDOS)).isEqualByComparingTo("2");
    assertThat(a.valor(Criterio.CLIENTES_NOVOS)).isEqualByComparingTo("1");
    assertThat(a.valor(Criterio.CLIENTES_QUE_VOLTARAM)).isEqualByComparingTo("1");
    assertThat(a.valor(Criterio.TICKET_MEDIO)).isEqualByComparingTo("100");
    assertThat(a.valor(Criterio.FALTAS)).isEqualByComparingTo("1");

    Metricas b = porNome.get("Bia");
    // 200 de servico com 20% de desconto rateado (50 de 250) = 160; produto 50 -> 40.
    assertThat(b.valor(Criterio.FATURAMENTO_SERVICOS)).isEqualByComparingTo("160");
    assertThat(b.valor(Criterio.VENDA_DE_PRODUTOS)).isEqualByComparingTo("40");
    assertThat(b.valor(Criterio.CANCELAMENTOS)).isEqualByComparingTo("1");
    assertThat(b.valor(Criterio.CLIENTES_NOVOS)).isEqualByComparingTo("0");
    assertThat(inativa).isNotNull();
  }

  private UUID agendamento(
      UUID tenant, UUID profissional, UUID cliente, LocalDate dia, String status, String de, String ate, String preco) {
    UUID id = inserir("appointments", Map.of(
        "tenant_id", tenant, "professional_id", profissional, "client_id", cliente,
        "date", java.sql.Date.valueOf(dia), "status", status,
        "start_time", java.sql.Time.valueOf(LocalTime.parse(de)), "end_time", java.sql.Time.valueOf(LocalTime.parse(ate))));
    inserir("appointment_items", Map.of("tenant_id", tenant, "appointment_id", id, "total_price", new BigDecimal(preco),
        "gross_amount", new BigDecimal(preco), "discount_amount", BigDecimal.ZERO));
    return id;
  }

  /**
   * Insere preenchendo so o necessario: o que o teste passa, e um valor neutro nas colunas
   * obrigatorias sem padrao. As chaves estrangeiras de apoio (servico, plano...) nao importam aqui.
   */
  private UUID inserir(String tabela, Map<String, Object> valores) {
    UUID id = UUID.randomUUID();
    Map<String, Object> linha = new HashMap<>(valores);
    linha.putIfAbsent("id", id);
    List<Map<String, Object>> colunas = jdbc.queryForList(
        "SELECT column_name, data_type, udt_name FROM information_schema.columns WHERE table_schema = 'azzo_app' "
            + "AND table_name = ? AND is_nullable = 'NO' AND column_default IS NULL "
            + "AND is_identity = 'NO' AND is_generated = 'NEVER'", tabela);
    for (Map<String, Object> coluna : colunas) {
      String nome = (String) coluna.get("column_name");
      if (linha.containsKey(nome)) continue;
      String tipo = (String) coluna.get("data_type");
      linha.put(nome, switch (tipo) {
        case "uuid" -> UUID.randomUUID();
        case "boolean" -> false;
        case "integer", "bigint", "smallint" -> 0;
        case "numeric", "double precision", "real" -> BigDecimal.ZERO;
        case "timestamp with time zone", "timestamp without time zone" -> java.sql.Timestamp.from(Instant.now());
        case "date" -> java.sql.Date.valueOf(LocalDate.now());
        case "time without time zone" -> java.sql.Time.valueOf(LocalTime.of(9, 0));
        default -> nome.length() > 8 ? nome.substring(0, 8) : nome;
      });
    }
    List<String> nomes = new ArrayList<>(linha.keySet());
    String sql = "INSERT INTO " + tabela + " (" + String.join(",", nomes) + ") VALUES ("
        + String.join(",", java.util.Collections.nCopies(nomes.size(), "?")) + ")";
    jdbc.execute((Connection con) -> {
      try (var st = con.createStatement()) {
        st.execute("SET session_replication_role = replica");
      }
      try (PreparedStatement ps = con.prepareStatement(sql)) {
        for (int i = 0; i < nomes.size(); i++) ps.setObject(i + 1, linha.get(nomes.get(i)));
        ps.executeUpdate();
      } finally {
        try (var st = con.createStatement()) {
          st.execute("SET session_replication_role = origin");
        }
      }
      return null;
    });
    return (UUID) linha.get("id");
  }
}
