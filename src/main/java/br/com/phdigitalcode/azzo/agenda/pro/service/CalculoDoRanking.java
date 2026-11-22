package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * As contas do ranking dos profissionais, sem banco: valida as regras do dono e pontua as metricas.
 *
 * <p>Dois modos, escolhidos por ranking:
 *
 * <ul>
 *   <li><b>PONTOS</b>: cada criterio vale {@code pontos} a cada {@code aCada} unidades (1 ponto a
 *       cada R$ 100; -3 a cada falta). Soma tudo.
 *   <li><b>PESO</b>: cada criterio vira nota de 0 a 100 contra o melhor do periodo (no criterio em
 *       que menor e melhor, a nota e invertida) e entra com o peso do dono; os pesos somam 100. Se
 *       ninguem pontuou num criterio (maior valor 0), todos ganham a nota cheia — ninguem perde por
 *       algo que nao aconteceu.
 * </ul>
 *
 * <p>Empate: quem faturou mais em servicos fica na frente; depois, ordem alfabetica.
 */
public final class CalculoDoRanking {

  public static final String PONTOS = "PONTOS";
  public static final String PESO = "PESO";
  private static final BigDecimal CEM = BigDecimal.valueOf(100);

  private CalculoDoRanking() {}

  /** O que cada criterio mede. O sentido e fixo: o dono escolhe so quanto vale. */
  public enum Criterio {
    FATURAMENTO_SERVICOS("Faturamento de serviços", "R$", true),
    ATENDIMENTOS_CONCLUIDOS("Atendimentos concluídos", "atendimento", true),
    CLIENTES_ATENDIDOS("Clientes atendidos", "cliente", true),
    CLIENTES_NOVOS("Clientes novos", "cliente", true),
    CLIENTES_QUE_VOLTARAM("Clientes que voltaram", "cliente", true),
    TICKET_MEDIO("Ticket médio", "R$", true),
    VENDA_DE_PRODUTOS("Venda de produtos", "R$", true),
    OCUPACAO("Ocupação da agenda", "%", true),
    FALTAS("Faltas de clientes", "falta", false),
    CANCELAMENTOS("Cancelamentos", "cancelamento", false);

    public final String rotulo;
    public final String unidade;
    public final boolean maiorEMelhor;

    Criterio(String rotulo, String unidade, boolean maiorEMelhor) {
      this.rotulo = rotulo;
      this.unidade = unidade;
      this.maiorEMelhor = maiorEMelhor;
    }

    static Criterio deChave(String chave) {
      if (chave == null) return null;
      try {
        return Criterio.valueOf(chave.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }

  /** A regra do dono para um criterio. PONTOS usa {@code pontos}/{@code aCada}; PESO usa {@code peso}. */
  public record Regra(String chave, BigDecimal pontos, BigDecimal aCada, BigDecimal peso) {}

  /** As metricas de um profissional no periodo. */
  public record Metricas(UUID profissionalId, String nome, Map<Criterio, BigDecimal> valores) {
    BigDecimal valor(Criterio criterio) {
      BigDecimal v = valores == null ? null : valores.get(criterio);
      return v == null ? BigDecimal.ZERO : v;
    }
  }

  public record Item(String chave, BigDecimal valor, BigDecimal pontos) {}

  public record Linha(int posicao, UUID profissionalId, String nome, BigDecimal total, List<Item> criterios) {}

  /** Todos os problemas de uma vez, em portugues, para a tela mostrar. Vazio: pode salvar. */
  public static List<String> validar(String nome, String modo, List<Regra> regras) {
    List<String> problemas = new ArrayList<>();
    if (nome == null || nome.isBlank()) problemas.add("Dê um nome ao ranking.");
    else if (nome.trim().length() > 80) problemas.add("O nome pode ter no máximo 80 caracteres.");
    if (!PONTOS.equals(modo) && !PESO.equals(modo)) {
      problemas.add("Escolha como pontuar: pontos por unidade ou peso em %.");
      return problemas;
    }
    if (regras == null || regras.isEmpty()) {
      problemas.add("Escolha pelo menos um critério.");
      return problemas;
    }
    Set<Criterio> vistos = new HashSet<>();
    BigDecimal somaDosPesos = BigDecimal.ZERO;
    for (Regra regra : regras) {
      Criterio criterio = Criterio.deChave(regra.chave());
      if (criterio == null) {
        problemas.add("Critério desconhecido: " + regra.chave() + ".");
        continue;
      }
      if (!vistos.add(criterio)) problemas.add("O critério \"" + criterio.rotulo + "\" aparece duas vezes.");
      if (PONTOS.equals(modo)) {
        if (regra.pontos() == null) problemas.add("Informe os pontos de \"" + criterio.rotulo + "\".");
        if (regra.aCada() == null || regra.aCada().signum() <= 0) {
          problemas.add("Em \"" + criterio.rotulo + "\", o \"a cada\" precisa ser maior que zero.");
        }
      } else {
        if (regra.peso() == null || regra.peso().signum() <= 0 || regra.peso().compareTo(CEM) > 0) {
          problemas.add("O peso de \"" + criterio.rotulo + "\" precisa ficar entre 1 e 100.");
        } else {
          somaDosPesos = somaDosPesos.add(regra.peso());
        }
      }
    }
    if (PESO.equals(modo) && problemas.isEmpty() && somaDosPesos.compareTo(CEM) != 0) {
      problemas.add("Os pesos somam " + somaDosPesos.stripTrailingZeros().toPlainString() + "%; precisam somar 100%.");
    }
    return problemas;
  }

  /** Pontua e ordena. As regras ja vieram validadas. */
  public static List<Linha> calcular(String modo, List<Regra> regras, List<Metricas> profissionais) {
    Map<Criterio, BigDecimal> maiores = new EnumMap<>(Criterio.class);
    for (Regra regra : regras) {
      Criterio c = Criterio.deChave(regra.chave());
      BigDecimal maior = BigDecimal.ZERO;
      for (Metricas m : profissionais) maior = maior.max(m.valor(c));
      maiores.put(c, maior);
    }

    record Parcial(Metricas metricas, BigDecimal total, List<Item> itens) {}
    List<Parcial> parciais = new ArrayList<>();
    for (Metricas m : profissionais) {
      List<Item> itens = new ArrayList<>();
      BigDecimal total = BigDecimal.ZERO;
      for (Regra regra : regras) {
        Criterio c = Criterio.deChave(regra.chave());
        BigDecimal valor = m.valor(c);
        BigDecimal pontos = PONTOS.equals(modo)
            ? regra.pontos().multiply(valor).divide(regra.aCada(), 4, RoundingMode.HALF_UP)
            : nota(c, valor, maiores.get(c)).multiply(regra.peso()).divide(CEM, 4, RoundingMode.HALF_UP);
        pontos = pontos.setScale(2, RoundingMode.HALF_UP);
        total = total.add(pontos);
        itens.add(new Item(c.name(), valor, pontos));
      }
      parciais.add(new Parcial(m, total, itens));
    }

    parciais.sort(Comparator.comparing((Parcial p) -> p.total()).reversed()
        .thenComparing(p -> p.metricas().valor(Criterio.FATURAMENTO_SERVICOS), Comparator.reverseOrder())
        .thenComparing(p -> p.metricas().nome() == null ? "" : p.metricas().nome(), String.CASE_INSENSITIVE_ORDER));

    List<Linha> linhas = new ArrayList<>();
    for (int i = 0; i < parciais.size(); i++) {
      Parcial p = parciais.get(i);
      linhas.add(new Linha(i + 1, p.metricas().profissionalId(), p.metricas().nome(), p.total(), p.itens()));
    }
    return linhas;
  }

  /** Nota de 0 a 100 contra o melhor do periodo. */
  static BigDecimal nota(Criterio criterio, BigDecimal valor, BigDecimal maior) {
    if (maior == null || maior.signum() <= 0) return CEM;
    BigDecimal proporcao = valor.max(BigDecimal.ZERO).multiply(CEM).divide(maior, 4, RoundingMode.HALF_UP);
    return criterio.maiorEMelhor ? proporcao : CEM.subtract(proporcao);
  }
}
