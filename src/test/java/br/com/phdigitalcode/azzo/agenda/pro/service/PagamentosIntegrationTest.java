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
 * mentira no lugar de "GETNET": criar cobranca, idempotencia, aviso pago, aviso repetido, token
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

    public String codigo() { return "GETNET"; }
    public String nome() { return "Operadora de teste"; }
    public Set<Capacidade> capacidades() { return EnumSet.of(Capacidade.PIX, Capacidade.ESTORNO, Capacidade.RECORRENTE); }

    volatile SituacaoDaAssinatura situacaoDaAssinatura = new SituacaoDaAssinatura(SituacaoDeAssinatura.AGUARDANDO_CARTAO, 0, false);

    public SituacaoDaAssinatura consultarAssinatura(Conta conta, String id) { return situacaoDaAssinatura; }

    public String assinaturaDoAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
      try {
        String a = new ObjectMapper().readTree(corpo).path("assinatura").asText("");
        return a.isBlank() ? null : a;
      } catch (Exception e) {
        return null;
      }
    }
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
        if (n.has("assinatura")) return new AvisoDaOperadora(n.path("evento").asText(), "assinatura", null, null);
        boolean semTaxa = n.path("semTaxa").asBoolean(false);
        return new AvisoDaOperadora(n.path("evento").asText(), "pagamento", n.path("cobranca").asText(),
            new StatusNaOperadora(Status.valueOf(n.path("status").asText()), BigDecimal.TEN,
                semTaxa ? null : new BigDecimal("0.15"), semTaxa ? null : new BigDecimal("9.85"), null, "NSU9", "AUT9",
                "VISA", "4111111111111111", null, null));
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
  @Autowired private ServicoDoConectorTef conector;
  @Autowired private br.com.phdigitalcode.azzo.agenda.pro.repository.CobrancaRepository cobrancas;

  private ServicoDePagamentos.NovaCobranca pix(String chave) {
    return new ServicoDePagamentos.NovaCobranca("TESTE", null, "PIX", BigDecimal.TEN, 1, "teste",
        new ProvedorDePagamento.Pagador(null, "Ana", null, null, null), null, null, null, null, chave);
  }

  @Test
  void cobrancaPagaPeloAvisoOuPelaConsultaSemDuplicarNada() {
    UUID tenant = criarTenant();
    ContaDePagamento conta = new ContaDePagamento();
    conta.setTenantId(tenant);
    conta.setProvedor("GETNET");
    conta.setApelido("Teste");
    conta.setAmbiente("SANDBOX");
    conta.setCredenciaisEnc(cifra.encrypt("{\"apiKey\":\"chave-de-teste\"}"));
    conta.setWebhookToken("token-" + UUID.randomUUID());
    conta.setPadraoPara("PIX");
    contas.save(conta);

    // Criar, e o clique duplo (mesma chave) devolve a mesma cobranca sem chamar a operadora de novo.
    int criadasAntes = operadora.criadas.size();
    PagamentosDtos.Cobranca criada = pagamentos.criar(tenant, pix("comanda-1:pix"));
    PagamentosDtos.Cobranca repetida = pagamentos.criar(tenant, pix("comanda-1:pix"));
    assertThat(criada.status).isEqualTo("AGUARDANDO");
    assertThat(criada.pixCopiaECola).isEqualTo("00020126pix");
    assertThat(repetida.id).isEqualTo(criada.id);
    assertThat(operadora.criadas).hasSize(criadasAntes + 1);

    // Aviso pago: status, taxa, liquido, NSU e so os 4 ultimos do cartao.
    // O id na operadora vem do banco: a operadora de teste e uma so para todos os testes.
    String op = jdbc.queryForObject("SELECT id_na_operadora FROM cobrancas WHERE id = ?::uuid", String.class, criada.id);
    String aviso = "{\"evento\":\"evt-1\",\"cobranca\":\"" + op + "\",\"status\":\"PAGA\"}";
    pagamentos.receberAviso("getnet", conta.getWebhookToken(), Map.of(), aviso);
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
    pagamentos.receberAviso("getnet", conta.getWebhookToken(), Map.of(), aviso);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_de_cobranca", Integer.class)).isEqualTo(eventosAntes);

    // Paga nao volta a "aguardando".
    pagamentos.receberAviso("getnet", conta.getWebhookToken(), Map.of(),
        "{\"evento\":\"evt-2\",\"cobranca\":\"" + op + "\",\"status\":\"AGUARDANDO\"}");
    assertThat(jdbc.queryForObject("SELECT status FROM cobrancas WHERE id = ?::uuid", String.class, criada.id)).isEqualTo("PAGA");

    // Token errado ou operadora trocada na URL: nao reconhece.
    assertThatThrownBy(() -> pagamentos.receberAviso("getnet", "token-errado", Map.of(), aviso))
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

  /** Fase 5: sem a taxa real, a cobranca paga ganha taxa, liquido e previsao pela tabela da conta. */
  @Test
  void taxaEstimadaPelaTabelaDaContaEntraNosRecebiveis() {
    UUID tenant = criarTenant();
    ContaDePagamento conta = new ContaDePagamento();
    conta.setTenantId(tenant);
    conta.setProvedor("GETNET");
    conta.setApelido("Caixa");
    conta.setAmbiente("SANDBOX");
    conta.setCredenciaisEnc(cifra.encrypt("{\"apiKey\":\"chave-de-teste\"}"));
    conta.setWebhookToken("token-" + UUID.randomUUID());
    conta.setPadraoPara("PIX");
    contas.save(conta);
    jdbc.update("INSERT INTO taxas_de_pagamento (id, tenant_id, conta_id, meio, percentual, valor_fixo, dias_para_receber) "
        + "VALUES (?, ?, ?, 'PIX', 1.000, 0.05, 2)", UUID.randomUUID(), tenant, conta.getId());

    PagamentosDtos.Cobranca criada = pagamentos.criar(tenant, pix("comanda-taxa:pix"));
    String idNaOperadora = jdbc.queryForObject("SELECT id_na_operadora FROM cobrancas WHERE id = ?::uuid", String.class, criada.id);
    pagamentos.receberAviso("getnet", conta.getWebhookToken(), Map.of(),
        "{\"evento\":\"evt-taxa\",\"cobranca\":\"" + idNaOperadora + "\",\"status\":\"PAGA\",\"semTaxa\":true}");

    Map<String, Object> linha = jdbc.queryForMap("SELECT * FROM cobrancas WHERE id = ?::uuid", criada.id);
    assertThat((BigDecimal) linha.get("taxa")).isEqualByComparingTo("0.15"); // 1% de 10,00 + 0,05
    assertThat((BigDecimal) linha.get("valor_liquido")).isEqualByComparingTo("9.85");
    java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
    assertThat(linha.get("previsao_recebimento").toString()).isEqualTo(hoje.plusDays(2).toString());
    assertThat(cobrancas.recebiveis(tenant, hoje, hoje.plusDays(5))).extracting(c -> c.getId().toString()).containsExactly(criada.id);
    assertThat(cobrancas.pagasSemPrevisao(tenant)).isZero();
  }

  /** Fase 7: o aviso de assinatura leva a operadora a ser consultada; a mensalidade paga ativa o clube. */
  @Test
  void avisoDeAssinaturaPagaAtivaOClubeUmaVezSo() {
    UUID tenant = criarTenant();
    ContaDePagamento conta = new ContaDePagamento();
    conta.setTenantId(tenant);
    conta.setProvedor("GETNET");
    conta.setApelido("Online");
    conta.setAmbiente("SANDBOX");
    conta.setCredenciaisEnc(cifra.encrypt("{\"apiKey\":\"chave-de-teste\"}"));
    conta.setWebhookToken("token-" + UUID.randomUUID());
    conta.setPadraoPara("ONLINE");
    contas.save(conta);
    UUID cliente = UUID.randomUUID();
    jdbc.update("INSERT INTO clients (id, tenant_id, name) VALUES (?, ?, 'Ana')", cliente, tenant);
    UUID membership = UUID.randomUUID();
    String assinatura = "sub-" + membership.toString().substring(0, 8);
    jdbc.update("INSERT INTO client_memberships (id, tenant_id, client_id, plan_nome, preco_mensal, status, period_start, period_end, "
        + "conta_pagamento_id, provedor_recorrencia, assinatura_na_operadora, link_do_cartao, created_at, updated_at) "
        + "VALUES (?, ?, ?, 'Clube', 89.90, 'AGUARDANDO_CARTAO', now(), now(), ?, 'GETNET', ?, 'https://op/cartao', now(), now())",
        membership, tenant, cliente, conta.getId(), assinatura);

    // Cartao cadastrado e primeira mensalidade paga: o aviso chega, a operadora e consultada.
    operadora.situacaoDaAssinatura = new ProvedorDePagamento.SituacaoDaAssinatura(ProvedorDePagamento.SituacaoDeAssinatura.ATIVA, 1, false);
    pagamentos.receberAviso("getnet", conta.getWebhookToken(), Map.of(),
        "{\"evento\":\"sub-evt-1\",\"assinatura\":\"" + assinatura + "\"}");
    Map<String, Object> linha = jdbc.queryForMap("SELECT * FROM client_memberships WHERE id = ?", membership);
    assertThat(linha.get("status")).isEqualTo("ATIVA");
    assertThat(linha.get("cobrancas_pagas_na_operadora")).isEqualTo(1);
    Object fim = linha.get("period_end");

    // Outro aviso sem cobranca nova: nao renova de novo.
    pagamentos.receberAviso("getnet", conta.getWebhookToken(), Map.of(),
        "{\"evento\":\"sub-evt-2\",\"assinatura\":\"" + assinatura + "\"}");
    assertThat(jdbc.queryForObject("SELECT period_end FROM client_memberships WHERE id = ?", Object.class, membership)).isEqualTo(fim);
  }

  /** Fase 6: o Azzo Conector pareia, pega a venda, aprova — e uma venda cancelada no caixa e desfeita. */
  @Test
  void conectorTefPareiaPegaAVendaEConfirmaSoOQueOAzzoAceita() {
    UUID tenant = criarTenant();
    ContaDePagamento conta = new ContaDePagamento();
    conta.setTenantId(tenant);
    conta.setProvedor("TEF");
    conta.setApelido("TEF do balcão");
    conta.setAmbiente("SANDBOX");
    conta.setCredenciaisEnc(cifra.encrypt("{\"sistema\":\"PAYGO\",\"pdcPaygo\":\"12345\"}"));
    conta.setWebhookToken("token-" + UUID.randomUUID());
    conta.setPadraoPara("CARTAO_PRESENCIAL");
    contas.save(conta);
    UUID terminal = UUID.randomUUID();
    String codigo = "ABCD" + terminal.toString().substring(0, 4).toUpperCase();
    jdbc.update("INSERT INTO terminais_de_pagamento (id, tenant_id, conta_id, serial, apelido, tipo, codigo_pareamento_hash, codigo_expira_em) "
        + "VALUES (?, ?, ?, ?, 'Recepção', 'TEF_LOCAL', ?, now() + interval '10 minutes')",
        terminal, tenant, conta.getId(), "CONECTOR-" + terminal.toString().substring(0, 6), ServicoDoConectorTef.hash(codigo));

    // Pareamento: codigo com hifen e minusculas vale; o token so sai aqui; o codigo nao serve de novo.
    var pedidoDePareamento = new br.com.phdigitalcode.azzo.agenda.pro.dto.ConectorTefDtos.PedidoDePareamento();
    pedidoDePareamento.codigo = (codigo.substring(0, 4) + "-" + codigo.substring(4)).toLowerCase();
    pedidoDePareamento.nomeDoComputador = "CAIXA-01";
    var pareado = conector.parear(pedidoDePareamento);
    assertThat(pareado.token).hasSize(64);
    assertThat(pareado.configuracao.sistema).isEqualTo("PAYGO");
    assertThat(pareado.configuracao.parametros).containsEntry("pdcPaygo", "12345");
    assertThat(jdbc.queryForObject("SELECT token_hash FROM terminais_de_pagamento WHERE id = ?", String.class, terminal))
        .isEqualTo(ServicoDoConectorTef.hash(pareado.token)).isNotEqualTo(pareado.token);
    assertThatThrownBy(() -> conector.parear(pedidoDePareamento)).isInstanceOf(ApiClientErrorException.class);
    assertThatThrownBy(() -> conector.autenticar("Bearer " + "0".repeat(64))).isInstanceOf(ApiClientErrorException.class);
    var t = conector.autenticar("Bearer " + pareado.token);

    // Venda: o PDV cria, o Conector pega uma vez so, aprova e o Azzo manda confirmar.
    var venda = pagamentos.criar(tenant, new ServicoDePagamentos.NovaCobranca("TESTE", null, "CREDITO", new BigDecimal("80.00"), 3,
        "Comanda", null, conta.getId(), terminal, "CONECTOR", null, "tef-1-" + terminal));
    assertThat(venda.status).isEqualTo("AGUARDANDO");
    var pedido = conector.proximoPedido(t, java.time.Duration.ZERO);
    assertThat(pedido.cobrancaId).isEqualTo(venda.id);
    assertThat(pedido.valorEmCentavos).isEqualTo(8000L);
    assertThat(pedido.parcelas).isEqualTo(3);
    assertThat(conector.proximoPedido(t, java.time.Duration.ZERO)).isNull();

    var aprovado = new br.com.phdigitalcode.azzo.agenda.pro.dto.ConectorTefDtos.Resultado();
    aprovado.aprovado = true;
    aprovado.nsu = "000123";
    aprovado.bandeira = "Visa";
    aprovado.cartaoFinal4 = "4111111111111111";
    aprovado.comprovanteCliente = "VIA CLIENTE";
    assertThat(conector.resultado(t, UUID.fromString(venda.id), aprovado).confirmar).isTrue();
    assertThat(conector.resultado(t, UUID.fromString(venda.id), aprovado).confirmar).isTrue(); // repetido
    Map<String, Object> linha = jdbc.queryForMap("SELECT * FROM cobrancas WHERE id = ?::uuid", venda.id);
    assertThat(linha.get("status")).isEqualTo("PAGA");
    assertThat(linha.get("nsu")).isEqualTo("000123");
    assertThat(linha.get("cartao_final4")).isEqualTo("1111");
    assertThat(linha.get("comprovante_cliente")).isEqualTo("VIA CLIENTE");

    // O caixa cancelou enquanto a cliente passava o cartao: aprovado no pinpad, o Azzo manda DESFAZER.
    var outra = pagamentos.criar(tenant, new ServicoDePagamentos.NovaCobranca("TESTE", null, "DEBITO", BigDecimal.TEN, 1,
        "Comanda", null, conta.getId(), terminal, "CONECTOR", null, "tef-2-" + terminal));
    conector.proximoPedido(t, java.time.Duration.ZERO);
    pagamentos.aplicar(UUID.fromString(outra.id), ProvedorDePagamento.StatusNaOperadora.so(ProvedorDePagamento.Status.CANCELADA),
        "MANUAL", "CANCELAR", "cancelar:" + outra.id, null);
    assertThat(conector.situacao(t, UUID.fromString(outra.id)).cancelada).isTrue();
    assertThat(conector.resultado(t, UUID.fromString(outra.id), aprovado).confirmar).isFalse();
    assertThat(jdbc.queryForObject("SELECT status FROM cobrancas WHERE id = ?::uuid", String.class, outra.id)).isEqualTo("CANCELADA");

    // Cancelada no pinpad pelo operador: a venda paga vira estornada.
    var cancelamento = new br.com.phdigitalcode.azzo.agenda.pro.dto.ConectorTefDtos.CancelamentoNoPinpad();
    cancelamento.nsu = "000123";
    conector.canceladaNoPinpad(t, cancelamento);
    assertThat(jdbc.queryForObject("SELECT status FROM cobrancas WHERE id = ?::uuid", String.class, venda.id)).isEqualTo("ESTORNADA");

    // Sem resposta do Conector por mais de 10 minutos: expira.
    var parada = pagamentos.criar(tenant, new ServicoDePagamentos.NovaCobranca("TESTE", null, "DEBITO", BigDecimal.TEN, 1,
        "Comanda", null, conta.getId(), terminal, "CONECTOR", null, "tef-3-" + terminal));
    jdbc.update("UPDATE cobrancas SET created_at = now() - interval '11 minutes' WHERE id = ?::uuid", parada.id);
    assertThat(conector.expirarSemResposta()).isGreaterThanOrEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM cobrancas WHERE id = ?::uuid", String.class, parada.id)).isEqualTo("EXPIRADA");
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
