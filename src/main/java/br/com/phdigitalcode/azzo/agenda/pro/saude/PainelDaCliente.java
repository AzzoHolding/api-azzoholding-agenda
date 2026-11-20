package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.util.IndicadoresDoCliente;

/**
 * Painel da cliente na area (pedido de 20/11/2026): numeros, evolucao mensal, servicos mais usados e todos os horarios,
 * de todos os salões da conta (ou de um so). Le so os cadastros ligados a conta (vinculos ativos).
 *
 * <p>O valor e o dos itens do agendamento (bruto menos desconto), o mesmo da lista de horarios; nao e o caixa do salao.
 */
@Service
public class PainelDaCliente {

  /** O banco guarda o rotulo do status (StatusAgendamento). */
  static final String CONCLUIDO = "Concluido";
  static final String FALTOU = "Nao compareceu";
  static final String CANCELADO = "Cancelado";
  static final Map<String, List<String>> SITUACOES = Map.of(
      "PROXIMOS", List.of("Pendente", "Confirmado", "Em andamento"),
      "CONCLUIDOS", List.of(CONCLUIDO),
      "CANCELADOS", List.of(CANCELADO),
      "FALTAS", List.of(FALTOU));

  private final NamedParameterJdbcTemplate jdbc;

  public PainelDaCliente(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** Os ultimos 12 meses (o atual incluso), do mais antigo ao mais novo, com zero onde nao ha atendimento. */
  static List<Map<String, Object>> serieMensal(Map<String, long[]> porMes, BigDecimal[] vazio, Map<String, BigDecimal> valores,
      YearMonth atual) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (int i = 11; i >= 0; i--) {
      String mes = atual.minusMonths(i).toString();
      long[] c = porMes.getOrDefault(mes, new long[] {0});
      r.add(m("mes", mes, "atendimentos", c[0], "valor", valores.getOrDefault(mes, vazio[0])));
    }
    return r;
  }

  /** Retorno esperado: a ultima visita mais o intervalo medio (sem intervalo, nao ha previsao). */
  static LocalDate retornoEsperado(LocalDate ultima, Integer intervaloMedio) {
    return ultima == null || intervaloMedio == null ? null : ultima.plusDays(intervaloMedio);
  }

  // ─── Leituras ──────────────────────────────────────────────────────────────

  private static final String DA_CONTA = """
      join vinculos_da_conta v on v.tenant_id = a.tenant_id and v.client_id = a.client_id
                              and v.conta_id = :conta and v.desfeito_em is null
      join tenants tn on tn.id = a.tenant_id""";

  private Map<String, Object> p(UUID conta, UUID salao) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("conta", conta);
    p.put("salao", salao);
    return p;
  }

  private static String filtroDoSalao(UUID salao) {
    return salao == null ? "" : " and a.tenant_id = :salao";
  }

  @Transactional(readOnly = true)
  public Map<String, Object> painel(UUID conta, UUID salao) {
    Map<String, Object> p = p(conta, salao);
    String f = filtroDoSalao(salao);
    Map<String, Object> tot = jdbc.queryForMap("""
        select count(*) filter (where a.status = 'Concluido') as concluidos,
               count(*) filter (where a.status = 'Nao compareceu') as faltas,
               count(*) filter (where a.status = 'Cancelado') as cancelados,
               count(*) filter (where a.status in ('Pendente', 'Confirmado', 'Em andamento') and a.date >= current_date) as proximos,
               min(a.date) filter (where a.status = 'Concluido') as primeira,
               max(a.date) filter (where a.status = 'Concluido') as ultima
          from appointments a""" + "\n" + DA_CONTA + " where true" + f, p);
    BigDecimal gasto = valor("a.status = 'Concluido'", f, p);
    BigDecimal gasto12 = valor("a.status = 'Concluido' and a.date >= current_date - interval '12 months'", f, p);
    List<LocalDate> dias = jdbc.queryForList("select distinct a.date from appointments a " + DA_CONTA
        + " where a.status = 'Concluido'" + f + " order by a.date", p, java.sql.Date.class).stream().map(java.sql.Date::toLocalDate).toList();
    Integer intervalo = IndicadoresDoCliente.intervaloMedioEmDias(dias);
    int concluidos = ((Number) tot.get("concluidos")).intValue();
    int faltas = ((Number) tot.get("faltas")).intValue();
    LocalDate ultima = tot.get("ultima") == null ? null : ((java.sql.Date) tot.get("ultima")).toLocalDate();

    Map<String, long[]> porMes = new LinkedHashMap<>();
    Map<String, BigDecimal> valorPorMes = new LinkedHashMap<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select to_char(a.date, 'YYYY-MM') as mes, count(distinct a.id) as n,
               coalesce(sum(i.gross_amount - i.discount_amount), 0) as valor
          from appointments a""" + "\n" + DA_CONTA + """

          left join appointment_items i on i.appointment_id = a.id
         where a.status = 'Concluido' and a.date >= date_trunc('month', current_date) - interval '11 months'""" + f
        + " group by 1", p)) {
      porMes.put(String.valueOf(x.get("mes")), new long[] {((Number) x.get("n")).longValue()});
      valorPorMes.put(String.valueOf(x.get("mes")), (BigDecimal) x.get("valor"));
    }

    List<Map<String, Object>> servicos = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select sv.name as servico, count(*) as vezes, max(a.date) as ultima,
               coalesce(sum(i.gross_amount - i.discount_amount), 0) as valor
          from appointments a""" + "\n" + DA_CONTA + """

          join appointment_items i on i.appointment_id = a.id
          join services sv on sv.id = i.service_id
         where a.status = 'Concluido'""" + f + " group by sv.name order by vezes desc, ultima desc limit 8", p)) {
      servicos.add(m("servico", x.get("servico"), "vezes", x.get("vezes"), "ultima", String.valueOf(x.get("ultima")), "valor", x.get("valor")));
    }

    List<Map<String, Object>> saloes = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select a.tenant_id, tn.name as salao, count(distinct a.id) as visitas, max(a.date) as ultima,
               coalesce(sum(i.gross_amount - i.discount_amount), 0) as valor
          from appointments a""" + "\n" + DA_CONTA + """

          left join appointment_items i on i.appointment_id = a.id
         where a.status = 'Concluido'""" + f + " group by a.tenant_id, tn.name order by visitas desc", p)) {
      saloes.add(m("salaoId", x.get("tenant_id"), "salao", x.get("salao"), "visitas", x.get("visitas"),
          "ultima", String.valueOf(x.get("ultima")), "valor", x.get("valor")));
    }

    List<Map<String, Object>> pacotes = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select tn.name as salao, pp.package_nome, b.service_nome, b.sessoes_totais, b.sessoes_usadas, b.expira_em
          from client_package_purchases pp join client_package_balances b on b.purchase_id = pp.id
          join vinculos_da_conta v on v.tenant_id = pp.tenant_id and v.client_id = pp.client_id
                                  and v.conta_id = :conta and v.desfeito_em is null
          join tenants tn on tn.id = pp.tenant_id
         where (b.expira_em is null or b.expira_em > now())""" + (salao == null ? "" : " and pp.tenant_id = :salao")
        + " order by pp.created_at desc limit 20", p)) {
      pacotes.add(m("salao", x.get("salao"), "pacote", x.get("package_nome"), "servico", x.get("service_nome"),
          "total", x.get("sessoes_totais"), "usadas", x.get("sessoes_usadas"), "expiraEm", Saude.instante(x.get("expira_em"))));
    }

    return m("numeros", m("concluidos", concluidos, "faltas", faltas, "cancelados", tot.get("cancelados"), "proximos", tot.get("proximos"),
            "gastoTotal", gasto, "gasto12Meses", gasto12, "comparecimento", IndicadoresDoCliente.taxaDeComparecimento(concluidos, faltas),
            "intervaloMedioDias", intervalo, "primeiraVisita", tot.get("primeira") == null ? null : tot.get("primeira").toString(),
            "ultimaVisita", ultima == null ? null : ultima.toString(),
            "retornoEsperado", retornoEsperado(ultima, intervalo) == null ? null : retornoEsperado(ultima, intervalo).toString()),
        "porMes", serieMensal(porMes, new BigDecimal[] {BigDecimal.ZERO}, valorPorMes, YearMonth.now(AnamneseDaCliente.ZONA_BR)),
        "servicos", servicos, "saloes", saloes, "pacotes", pacotes);
  }

  private BigDecimal valor(String condicao, String f, Map<String, Object> p) {
    BigDecimal v = jdbc.queryForObject("select coalesce(sum(i.gross_amount - i.discount_amount), 0) from appointments a "
        + DA_CONTA + " join appointment_items i on i.appointment_id = a.id where " + condicao + f, p, BigDecimal.class);
    return v == null ? BigDecimal.ZERO : v;
  }

  /** Todos os horarios da conta, do mais novo ao mais antigo, 30 por pagina. */
  @Transactional(readOnly = true)
  public Map<String, Object> agendamentos(UUID conta, UUID salao, String situacao, LocalDate de, LocalDate ate, int pagina) {
    Map<String, Object> p = p(conta, salao);
    StringBuilder w = new StringBuilder(" where true").append(filtroDoSalao(salao));
    if (situacao != null && !situacao.isBlank()) {
      List<String> st = SITUACOES.get(situacao.trim().toUpperCase(java.util.Locale.ROOT));
      if (st == null) throw new ApiClientErrorException("Situação inválida.", 400);
      w.append(" and a.status in (:st)");
      p.put("st", st);
    }
    if (de != null) {
      w.append(" and a.date >= :de");
      p.put("de", java.sql.Date.valueOf(de));
    }
    if (ate != null) {
      w.append(" and a.date <= :ate");
      p.put("ate", java.sql.Date.valueOf(ate));
    }
    int pg = Math.max(0, pagina);
    p.put("lim", 30);
    p.put("off", pg * 30);
    Long total = jdbc.queryForObject("select count(*) from appointments a " + DA_CONTA + w, p, Long.class);
    List<Map<String, Object>> itens = new ArrayList<>();
    for (Map<String, Object> a : jdbc.queryForList("""
        select a.id, a.tenant_id, tn.name as salao, a.date, a.start_time, a.status, pr.name as profissional,
               (select string_agg(sv.name, ', ' order by sv.name) from appointment_items i join services sv on sv.id = i.service_id
                 where i.appointment_id = a.id) as servicos,
               (select coalesce(sum(i.gross_amount - i.discount_amount), 0) from appointment_items i where i.appointment_id = a.id) as total
          from appointments a""" + "\n" + DA_CONTA + "\n left join professionals pr on pr.id = a.professional_id" + w
        + " order by a.date desc, a.start_time desc limit :lim offset :off", p)) {
      itens.add(m("id", a.get("id"), "salaoId", a.get("tenant_id"), "salao", a.get("salao"), "data", a.get("date").toString(),
          "inicio", String.valueOf(a.get("start_time")), "status", a.get("status"), "profissional", a.get("profissional"),
          "servicos", a.get("servicos"), "total", a.get("total")));
    }
    return m("itens", itens, "total", total, "pagina", pg, "porPagina", 30);
  }
}
