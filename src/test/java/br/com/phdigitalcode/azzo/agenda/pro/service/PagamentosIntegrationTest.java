package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PagamentosDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ContaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Pagamentos (Fase 0) contra um Postgres REAL, com as migrations reais (V167) e uma operadora de
 * mentira no lugar de "PAGARME": criar cobranca, idempotencia, aviso pago, aviso repetido, token
 * errado, operadora trocada na URL e a consulta de reserva quando o aviso se perde.
 */
@SpringBootTest(properties = {
    "app.audit.seal.enabled=false",
    "app.meta.app-secret=segredo-de-teste",
    "app.pagamentos.consulta.initial-delay-ms=3600000"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PagamentosIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /** A operadora de mentira e quem ouve as mudancas de status. */
  @TestConfiguration
  static class Configuracao {
    @Bean
    OperadoraDeTeste operadoraDeTeste() {
      return new OperadoraDeTeste();
    }

    @Bean
    Ouvinte ouvinte() {
      return new Ouvinte();
    }
  }

  static class Ouvinte {
    final List<ServicoDePagamentos.CobrancaMudouDeStatus> recebidos = new CopyOnWriteArrayList<>();

    @EventListener
    void ouvir(ServicoDePagamentos.CobrancaMudouDeStatus evento) {
      recebidos.add(evento);
    }
  }

  static class OperadoraDeTeste implements ProvedorDePagamento {
    final List<String> criadas = new ArrayList<>();
    volatile Status statusNaConsulta = Status.AGUARDANDO;

    public String codigo() { return "PAGARME"; }
    public String nome() { return "Operadora de teste"; }
    public Set<Capacidade> capacidades() { return EnumSet.of(Capacidade.PIX, Capacidade.ESTORNO); }
    public List<CampoDeCredencial> camposDeCredencial() { return List.of(new CampoDeCredencial("apiKey", "Chave", true)); }
    public ResultadoValidacao validarCredenciais(Conta conta) { return new ResultadoValidacao(true, "ok"); }

    public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
      String id = "op-" + (criadas.size() + 1);
      criadas.add(id);
      return new CobrancaCriada(id, Status.AGUARDANDO, "00020126pix", null, null);
    }

    public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
      return new StatusNaOperadora(statusNaConsulta, BigDecimal.TEN, new BigDecimal("0.20"), new BigDecimal("9.80"),
          null, "NSU1", null, null, null, null, null);
    }

    public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
      try {
        JsonNode n = new ObjectMapper().readTree(corpo);
        return new AvisoDaOperadora(n.path("evento").asText(), "pagamento", n.path("cobranca").asText(),
            new StatusNaOperadora(Status.valueOf(n.path("status").asText()), BigDecimal.TEN, new BigDecimal("0.15"),
                new BigDecimal("9.85"), null, "NSU9", "AUT9", "VISA", "4111111111111111", null, null));
      } catch (Exception e) {
        throw new AvisoInvalido("ilegível");
      }
    }
  }

  @Autowired private ServicoDePagamentos pagamentos;
  @Autowired private ContaDePagamentoRepository contas;
  @Autowired private EncryptionService cifra;
  @Autowired private OperadoraDeTeste operadora;
  @Autowired private Ouvinte ouvinte;
  @Autowired private JdbcTemplate jdbc;

  private ServicoDePagamentos.NovaCobranca pix(String chave) {
    return new ServicoDePagamentos.NovaCobranca("TESTE", null, "PIX", BigDecimal.TEN, 1, "teste",
        new ProvedorDePagamento.Pagador(null, "Ana", null, null, null), null, null, null, null, chave);
  }

  @Test
  void cobrancaPagaPeloAvisoOuPelaConsultaSemDuplicarNada() {
    UUID tenant = criarTenant();
    ContaDePagamento conta = new ContaDePagamento();
    conta.setTenantId(tenant);
    conta.setProvedor("PAGARME");
    conta.setApelido("Teste");
    conta.setAmbiente("SANDBOX");
    conta.setCredenciaisEnc(cifra.encrypt("{\"apiKey\":\"chave-de-teste\"}"));
    conta.setWebhookToken("token-" + UUID.randomUUID());
    conta.setPadraoPara("PIX");
    contas.save(conta);

    // Criar, e o clique duplo (mesma chave) devolve a mesma cobranca sem chamar a operadora de novo.
    PagamentosDtos.Cobranca criada = pagamentos.criar(tenant, pix("comanda-1:pix"));
    PagamentosDtos.Cobranca repetida = pagamentos.criar(tenant, pix("comanda-1:pix"));
    assertThat(criada.status).isEqualTo("AGUARDANDO");
    assertThat(criada.pixCopiaECola).isEqualTo("00020126pix");
    assertThat(repetida.id).isEqualTo(criada.id);
    assertThat(operadora.criadas).hasSize(1);

    // Aviso pago: status, taxa, liquido, NSU e so os 4 ultimos do cartao.
    String aviso = "{\"evento\":\"evt-1\",\"cobranca\":\"op-1\",\"status\":\"PAGA\"}";
    pagamentos.receberAviso("pagarme", conta.getWebhookToken(), Map.of(), aviso);
    Map<String, Object> linha = jdbc.queryForMap("SELECT * FROM cobrancas WHERE id = ?::uuid", criada.id);
    assertThat(linha.get("status")).isEqualTo("PAGA");
    assertThat((BigDecimal) linha.get("valor_liquido")).isEqualByComparingTo("9.85");
    assertThat(linha.get("nsu")).isEqualTo("NSU9");
    assertThat(linha.get("cartao_final4")).isEqualTo("1111");
    assertThat(ouvinte.recebidos).anySatisfy(e -> {
      assertThat(e.cobrancaId().toString()).isEqualTo(criada.id);
      assertThat(e.statusNovo()).isEqualTo("PAGA");
    });

    // O mesmo aviso de novo nao cria evento nem muda nada.
    int eventosAntes = jdbc.queryForObject("SELECT count(*) FROM eventos_de_cobranca", Integer.class);
    pagamentos.receberAviso("pagarme", conta.getWebhookToken(), Map.of(), aviso);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_de_cobranca", Integer.class)).isEqualTo(eventosAntes);

    // Paga nao volta a "aguardando".
    pagamentos.receberAviso("pagarme", conta.getWebhookToken(), Map.of(),
        "{\"evento\":\"evt-2\",\"cobranca\":\"op-1\",\"status\":\"AGUARDANDO\"}");
    assertThat(jdbc.queryForObject("SELECT status FROM cobrancas WHERE id = ?::uuid", String.class, criada.id)).isEqualTo("PAGA");

    // Token errado ou operadora trocada na URL: nao reconhece.
    assertThatThrownBy(() -> pagamentos.receberAviso("pagarme", "token-errado", Map.of(), aviso))
        .isInstanceOf(ApiClientErrorException.class).hasMessage("Aviso não reconhecido.");
    assertThatThrownBy(() -> pagamentos.receberAviso("asaas", conta.getWebhookToken(), Map.of(), aviso))
        .isInstanceOf(ApiClientErrorException.class).hasMessage("Aviso não reconhecido.");

    // Aviso perdido: a consulta de reserva acha a cobranca esperando e atualiza.
    PagamentosDtos.Cobranca outra = pagamentos.criar(tenant, pix("comanda-2:pix"));
    jdbc.update("UPDATE cobrancas SET created_at = now() - interval '2 minutes' WHERE id = ?::uuid", outra.id);
    operadora.statusNaConsulta = ProvedorDePagamento.Status.PAGA;
    assertThat(pagamentos.consultarPendentes()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM cobrancas WHERE id = ?::uuid", String.class, outra.id)).isEqualTo("PAGA");
  }

  @Test
  void semContaPadraoParaOUsoAvisaOndeConfigurar() {
    UUID tenant = criarTenant();
    assertThatThrownBy(() -> pagamentos.criar(tenant, pix("x")))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Configurações → Integrações → Pagamentos");
  }

  /** Um salao minimo (as chaves estrangeiras do salao nao importam aqui). */
  private UUID criarTenant() {
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> colunas = jdbc.queryForList(
        "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'azzo_app' "
            + "AND table_name = 'tenants' AND is_nullable = 'NO' AND column_default IS NULL "
            + "AND is_identity = 'NO' AND is_generated = 'NEVER'");
    List<String> nomes = new ArrayList<>();
    List<Object> valores = new ArrayList<>();
    for (Map<String, Object> c : colunas) {
      String nome = (String) c.get("column_name");
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
    String sql = "INSERT INTO tenants (" + String.join(",", nomes) + ") VALUES ("
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
