package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cupom;
import br.com.phdigitalcode.azzo.agenda.pro.util.CodigoAleatorio;

/** Regras do cupom e do vale-presente (item 6), sem banco. */
class CuponsEValesRegrasTest {

  private static final LocalDate HOJE = LocalDate.of(2026, 11, 17);

  private static Cupom cupom(String tipo, String valor) {
    Cupom c = new Cupom();
    c.setTipo(tipo);
    c.setValor(new BigDecimal(valor));
    return c;
  }

  @Test
  @DisplayName("desconto: % arredondado em centavos; R$ ate a base; nunca passa da base")
  void desconto() {
    assertThat(ServicoDeCupons.desconto(cupom("PERCENTUAL", "20"), new BigDecimal("80.00"))).isEqualByComparingTo("16.00");
    assertThat(ServicoDeCupons.desconto(cupom("PERCENTUAL", "33.33"), new BigDecimal("10.00"))).isEqualByComparingTo("3.33");
    assertThat(ServicoDeCupons.desconto(cupom("VALOR", "30"), new BigDecimal("80.00"))).isEqualByComparingTo("30.00");
    assertThat(ServicoDeCupons.desconto(cupom("VALOR", "100"), new BigDecimal("80.00"))).isEqualByComparingTo("80.00");
    assertThat(ServicoDeCupons.desconto(cupom("PERCENTUAL", "10"), BigDecimal.ZERO)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("cada motivo de recusa tem a sua frase")
  void motivosDeRecusa() {
    Cupom c = cupom("PERCENTUAL", "10");
    c.setAtivo(false);
    assertThatThrownBy(() -> ServicoDeCupons.conferirRegras(c, null, BigDecimal.TEN, HOJE)).hasMessageContaining("não está ativo");
    c.setAtivo(true);
    c.setFim(HOJE.minusDays(1));
    assertThatThrownBy(() -> ServicoDeCupons.conferirRegras(c, null, BigDecimal.TEN, HOJE)).hasMessageContaining("venceu em 16/11/2026");
    c.setFim(null);
    c.setInicio(HOJE.plusDays(3));
    assertThatThrownBy(() -> ServicoDeCupons.conferirRegras(c, null, BigDecimal.TEN, HOJE)).hasMessageContaining("a partir de 20/11/2026");
    c.setInicio(null);
    c.setValorMinimo(new BigDecimal("50"));
    assertThatThrownBy(() -> ServicoDeCupons.conferirRegras(c, null, BigDecimal.TEN, HOJE)).hasMessageContaining("a partir de R$ 50,00");
    c.setValorMinimo(null);
    c.setLimitePorCliente(1);
    assertThatThrownBy(() -> ServicoDeCupons.conferirRegras(c, null, BigDecimal.TEN, HOJE)).hasMessageContaining("identifique a cliente");
    assertThatCode(() -> ServicoDeCupons.conferirRegras(c, UUID.randomUUID(), BigDecimal.TEN, HOJE)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("situacao no lote: usado, nao usado, vencido, desativado")
  void situacaoNoLote() {
    Cupom c = cupom("PERCENTUAL", "10");
    assertThat(ServicoDeCupons.situacaoNoLote(c, HOJE)).isEqualTo("NAO_USADO");
    c.setFim(HOJE.minusDays(1));
    assertThat(ServicoDeCupons.situacaoNoLote(c, HOJE)).isEqualTo("VENCIDO");
    c.setAtivo(false);
    assertThat(ServicoDeCupons.situacaoNoLote(c, HOJE)).isEqualTo("DESATIVADO");
  }

  @Test
  @DisplayName("codigos aleatorios: tamanho certo, sem O/0/I/1, e 200 de 200 diferentes")
  void codigos() {
    Set<String> vistos = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      String c = CodigoAleatorio.gerar(6);
      assertThat(c).hasSize(6).matches("[A-HJ-NP-Z2-9]{6}");
      vistos.add(c);
    }
    assertThat(vistos).hasSize(200);
  }

  @Test
  @DisplayName("vale: R$ 10 a R$ 5.000, validade 1 a 60 meses (padrao 12)")
  void limitesDoVale() {
    assertThatThrownBy(() -> ServicoDeVales.exigirValorDeVenda(new BigDecimal("9.99"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ServicoDeVales.exigirValorDeVenda(new BigDecimal("5000.01"))).isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> ServicoDeVales.exigirValorDeVenda(new BigDecimal("100"))).doesNotThrowAnyException();
    assertThat(ServicoDeVales.validadeEmMeses(null)).isEqualTo(12);
    assertThatThrownBy(() -> ServicoDeVales.validadeEmMeses(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("motivo do desconto com um texto de cupom so, e sem ele quando o cupom sai")
  void motivoComCupom() {
    assertThat(ServicoComanda.motivoComCupom("Cortesia", "MAES20")).isEqualTo("Cortesia; Cupom MAES20");
    assertThat(ServicoComanda.motivoComCupom("Cortesia; Cupom MAES20", "NATAL-ABC123")).isEqualTo("Cortesia; Cupom NATAL-ABC123");
    assertThat(ServicoComanda.motivoComCupom("Cortesia; Cupom MAES20", null)).isEqualTo("Cortesia");
    assertThat(ServicoComanda.motivoComCupom("Cupom MAES20", null)).isNull();
  }

  @Test
  @DisplayName("prefixo do salao: 6 digitos, iguais para o mesmo documento com ou sem mascara")
  void prefixoDoSalao() {
    UUID tenant = UUID.randomUUID();
    String comMascara = CodigoAleatorio.prefixoDoSalao("12.345.678/0001-95", tenant);
    assertThat(comMascara).matches("\\d{6}");
    assertThat(CodigoAleatorio.prefixoDoSalao("12345678000195", UUID.randomUUID())).isEqualTo(comMascara);
    assertThat(CodigoAleatorio.prefixoDoSalao("123.456.789-09", tenant)).isNotEqualTo(comMascara);
    // Sem documento: o hash e do id do salao (sempre o mesmo para o mesmo salao).
    assertThat(CodigoAleatorio.prefixoDoSalao(null, tenant)).isEqualTo(CodigoAleatorio.prefixoDoSalao("", tenant));
    assertThat(CodigoAleatorio.pareceDeOutroSalao("000000-NATAL-ABC234", comMascara))
        .isEqualTo(!comMascara.equals("000000"));
    assertThat(CodigoAleatorio.pareceDeOutroSalao(comMascara + "-NATAL-ABC234", comMascara)).isFalse();
    assertThat(CodigoAleatorio.pareceDeOutroSalao("MAES20", comMascara)).isFalse();
  }

  @Test
  @DisplayName("miolo do vale: hash do UUID com o documento, no alfabeto sem ambiguos e no tamanho pedido")
  void codigoDoValePorHash() {
    UUID semente = UUID.randomUUID();
    String a = CodigoAleatorio.deHash(semente, "12.345.678/0001-95", 10);
    assertThat(a).hasSize(10).matches("[A-HJ-NP-Z2-9]{10}");
    // O mesmo UUID e documento (com ou sem mascara) dao o mesmo codigo; outro documento ou outro UUID, outro.
    assertThat(CodigoAleatorio.deHash(semente, "12345678000195", 10)).isEqualTo(a);
    assertThat(CodigoAleatorio.deHash(semente, "123.456.789-09", 10)).isNotEqualTo(a);
    assertThat(CodigoAleatorio.deHash(UUID.randomUUID(), "12345678000195", 10)).isNotEqualTo(a);
    assertThat(CodigoAleatorio.deHash(semente, null, 51)).hasSize(51);
    Set<String> mil = new HashSet<>();
    for (int i = 0; i < 1000; i++) mil.add(CodigoAleatorio.deHash(UUID.randomUUID(), "12345678000195", 10));
    assertThat(mil).hasSize(1000);
  }

  @Test
  @DisplayName("motivo com varios cupons (V183): 'Cupons A, B', e o texto antigo sai inteiro")
  void motivoComVariosCupons() {
    assertThat(ServicoComanda.motivoComCupons("Cortesia", List.of("MAES20", "NATAL-ABC123")))
        .isEqualTo("Cortesia; Cupons MAES20, NATAL-ABC123");
    assertThat(ServicoComanda.motivoComCupons("Cortesia; Cupons MAES20, NATAL-ABC123", List.of("MAES20")))
        .isEqualTo("Cortesia; Cupom MAES20");
    assertThat(ServicoComanda.motivoComCupons("Cupons MAES20, NATAL-ABC123", List.of())).isNull();
  }

  @Test
  @DisplayName("dois cupons de lista no mesmo servico: o segundo so leva o que sobrou")
  void tirarDosServicosNaoPassaDoQueResta() {
    UUID escova = UUID.randomUUID();
    List<ComandaItem> itens = List.of(servicoItem(escova, "60.00"), servicoItem(UUID.randomUUID(), "80.00"));
    List<BigDecimal> restantes = new java.util.ArrayList<>(List.of(new BigDecimal("60.00"), new BigDecimal("80.00")));
    assertThat(ServicoComanda.tirarDosServicos(itens, restantes, Set.of(escova), new BigDecimal("45.00")))
        .isEqualByComparingTo("45.00");
    assertThat(ServicoComanda.tirarDosServicos(itens, restantes, Set.of(escova), new BigDecimal("30.00")))
        .isEqualByComparingTo("15.00");
    assertThat(restantes.get(0)).isEqualByComparingTo("0");
    assertThat(restantes.get(1)).isEqualByComparingTo("80.00");
  }

  @Test
  @DisplayName("fechamento com dois cupons de lista: cada um sai so dos servicos dele")
  void rateioComDoisCuponsDeLista() {
    UUID escova = UUID.randomUUID();
    UUID corte = UUID.randomUUID();
    ComandaItem itemEscova = servicoItem(escova, "60.00");
    ComandaItem itemCorte = servicoItem(corte, "80.00");
    // R$ 10 so na escova + R$ 20 so no corte: total 140 - 30 = 110, sem outro desconto.
    List<BigDecimal> valores = ServicoComanda.ratearComCuponsDeLista(List.of(itemEscova, itemCorte),
        new BigDecimal("140.00"), new BigDecimal("110.00"), List.of(
            new ServicoComanda.CupomDeLista(new BigDecimal("10.00"), Set.of(escova)),
            new ServicoComanda.CupomDeLista(new BigDecimal("20.00"), Set.of(corte))));
    assertThat(valores.get(0)).isEqualByComparingTo("50.00");
    assertThat(valores.get(1)).isEqualByComparingTo("60.00");
  }

  @Test
  @DisplayName("sinal do link acompanha o desconto do cupom")
  void sinalComDesconto() {
    assertThat(ServicoPublicBooking.sinalComDesconto(new BigDecimal("30.00"), new BigDecimal("100.00"), new BigDecimal("20.00")))
        .isEqualByComparingTo("24.00");
    assertThat(ServicoPublicBooking.sinalComDesconto(new BigDecimal("30.00"), new BigDecimal("100.00"), BigDecimal.ZERO))
        .isEqualByComparingTo("30.00");
  }

  @Test
  @DisplayName("rateio do desconto: o vale vendido fica com o valor cheio")
  void rateioComVale() {
    ServicoComanda servico = org.mockito.Mockito.mock(ServicoComanda.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    ComandaItem corte = item(ComandaItem.TIPO_SERVICO, "80.00");
    ComandaItem vale = item(ComandaItem.TIPO_VALE, "100.00");
    ComandaItem escova = item(ComandaItem.TIPO_SERVICO, "20.00");
    // subtotal 200, desconto 10 (so sobre os 100 de servicos) -> total 190
    List<BigDecimal> valores = servico.ratearDesconto(List.of(corte, vale, escova), new BigDecimal("200.00"), new BigDecimal("190.00"));
    assertThat(valores.get(0)).isEqualByComparingTo("72.00");
    assertThat(valores.get(1)).isEqualByComparingTo("100.00");
    assertThat(valores.get(2)).isEqualByComparingTo("18.00");
    // Sem vale, como antes.
    List<BigDecimal> semVale = servico.ratearDesconto(List.of(corte, escova), new BigDecimal("100.00"), new BigDecimal("90.00"));
    assertThat(semVale.get(0)).isEqualByComparingTo("72.00");
    assertThat(semVale.get(1)).isEqualByComparingTo("18.00");
  }

  private static ComandaItem item(String tipo, String total) {
    ComandaItem i = new ComandaItem();
    i.setTipo(tipo);
    i.setTotal(new BigDecimal(total));
    return i;
  }

  // ─── Cupom so para alguns servicos (V182) ─────────────────────────────────

  @Test
  @DisplayName("base do cupom de lista: so o valor dos servicos da lista; sem lista, a conta toda")
  void baseElegivel() {
    UUID escova = UUID.randomUUID();
    UUID corte = UUID.randomUUID();
    java.util.Map<UUID, BigDecimal> valores = java.util.Map.of(escova, new BigDecimal("60.00"), corte, new BigDecimal("80.00"));
    assertThat(ServicoDeCupons.baseElegivel(Set.of(), new BigDecimal("140.00"), valores)).isEqualByComparingTo("140.00");
    assertThat(ServicoDeCupons.baseElegivel(Set.of(escova), new BigDecimal("140.00"), valores)).isEqualByComparingTo("60.00");
    assertThat(ServicoDeCupons.baseElegivel(Set.of(UUID.randomUUID()), new BigDecimal("140.00"), valores)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("fechamento com cupom de lista: o cupom sai so da escova; o desconto manual se divide por todos")
  void rateioComCupomDeLista() {
    UUID escova = UUID.randomUUID();
    ComandaItem itemEscova = servicoItem(escova, "60.00");
    ComandaItem itemCorte = servicoItem(UUID.randomUUID(), "80.00");
    // Cupom de R$ 12 so na escova; desconto manual de R$ 12,80 (10% de 128): total 140 - 12 - 12,80 = 115,20
    List<BigDecimal> valores = ServicoComanda.ratearComCupomDeLista(List.of(itemEscova, itemCorte),
        new BigDecimal("140.00"), new BigDecimal("115.20"), new BigDecimal("12.00"), Set.of(escova));
    // Escova: 60 - 12 = 48, depois 10% -> 43,20. Corte: 80, depois 10% -> 72,00.
    assertThat(valores.get(0)).isEqualByComparingTo("43.20");
    assertThat(valores.get(1)).isEqualByComparingTo("72.00");
    assertThat(valores.get(0).add(valores.get(1))).isEqualByComparingTo("115.20");
  }

  private static ComandaItem servicoItem(UUID servico, String total) {
    ComandaItem i = item(ComandaItem.TIPO_SERVICO, total);
    i.setReferenciaId(servico);
    return i;
  }
}
