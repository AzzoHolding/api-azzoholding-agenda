package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.NotificationPublisher;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoAgendamentos;

/**
 * O que a cliente ve e faz na area dela (PLANO-AREA-DA-CLIENTE.md, secao 5). Tudo parte da conta da sessao; cada salao so
 * entra pelo vinculo ativo. A cliente ve os dados de todos os salões dela; um salao continua vendo so o proprio cadastro.
 *
 * <p>Decisao do dono (19/11/2026): a cliente nao ve a observacao interna da profissional (ficha e secoes), as notas
 * internas (adendo do tipo NOTA), nem os alertas manuais do salao. Ficha em rascunho tambem nao aparece.
 */
@Service
public class AreaDaCliente {

  /** O banco guarda o rotulo do status (StatusAgendamento: "Pendente", "Confirmado", "Em andamento"). */
  private static final List<String> ATIVOS = List.of("Pendente", "Confirmado", "Em andamento");

  private final NamedParameterJdbcTemplate jdbc;
  private final ContasDaCliente contas;
  private final LinksDaCliente links;
  private final AnamneseDaCliente anamnese;
  private final AssinaturasDaCliente assinaturas;
  private final ArquivosProtegidos arquivos;
  private final ExamesDaCliente exames;
  private final AcessoDeSaude acesso;
  private final ModelosDeSaude modelos;
  private final LgpdDeSaude lgpd;

  /** Desmarcar pela area usa o mesmo caminho do salao (comanda, cupom, ficha, aviso). Por setter e preguicoso: evita ciclo. */
  private ServicoAgendamentos agendamentos;
  private ContextoTenant contextoTenant;
  private NotificationPublisher avisos;

  @Autowired(required = false)
  void setAgendamentos(@Lazy ServicoAgendamentos agendamentos) {
    this.agendamentos = agendamentos;
  }

  @Autowired(required = false)
  void setContextoTenant(ContextoTenant contextoTenant) {
    this.contextoTenant = contextoTenant;
  }

  @Autowired(required = false)
  void setAvisos(NotificationPublisher avisos) {
    this.avisos = avisos;
  }

  public AreaDaCliente(NamedParameterJdbcTemplate jdbc, ContasDaCliente contas, LinksDaCliente links, AnamneseDaCliente anamnese,
      AssinaturasDaCliente assinaturas, ArquivosProtegidos arquivos, ExamesDaCliente exames, AcessoDeSaude acesso,
      ModelosDeSaude modelos, LgpdDeSaude lgpd) {
    this.modelos = modelos;
    this.lgpd = lgpd;
    this.jdbc = jdbc;
    this.contas = contas;
    this.links = links;
    this.anamnese = anamnese;
    this.assinaturas = assinaturas;
    this.arquivos = arquivos;
    this.exames = exames;
    this.acesso = acesso;
  }

  /** Registro de acesso do salao, com a cliente como autora. */
  private void registrar(UUID t, UUID clientId, String recurso, UUID conta) {
    acesso.auditar(t, "HEALTH_DATA_VIEW", clientId, m("recurso", recurso, "autor", "CLIENTE", "conta", conta), false);
  }

  record Salao(UUID tenantId, UUID clientId, String nome) {}

  private List<Salao> saloes(UUID conta) {
    List<Salao> r = new ArrayList<>();
    for (Map<String, Object> v : jdbc.queryForList("""
        select v.tenant_id, v.client_id, t.name from vinculos_da_conta v join tenants t on t.id = v.tenant_id
         where v.conta_id = :c and v.desfeito_em is null order by t.name""", Map.of("c", conta))) {
      r.add(new Salao((UUID) v.get("tenant_id"), (UUID) v.get("client_id"), String.valueOf(v.get("name"))));
    }
    return r;
  }

  private Salao salao(UUID conta, UUID tenantId) {
    UUID c = contas.cadastroNoSalao(conta, tenantId);
    String nome = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", tenantId), String.class);
    return new Salao(tenantId, c, nome);
  }

  // ─── Leituras por salao ────────────────────────────────────────────────────

  List<Map<String, Object>> agendamentos(Salao s, boolean futuros) {
    LocalDate hoje = LocalDate.now(AnamneseDaCliente.ZONA_BR);
    String filtro = futuros ? "a.date >= :hoje and a.status in (:ativos)" : "(a.date < :hoje or a.status not in (:ativos))";
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> a : jdbc.queryForList("""
        select a.id, a.date, a.start_time, a.end_time, a.status, p.name as profissional,
               (select string_agg(sv.name, ', ' order by sv.name) from appointment_items i join services sv on sv.id = i.service_id
                 where i.appointment_id = a.id) as servicos,
               (select coalesce(sum(i.gross_amount - i.discount_amount), 0) from appointment_items i where i.appointment_id = a.id) as total
          from appointments a left join professionals p on p.id = a.professional_id
         where a.tenant_id = :t and a.client_id = :c""" + " and " + filtro
        + " order by a.date" + (futuros ? "" : " desc") + ", a.start_time" + (futuros ? "" : " desc") + " limit 100",
        m("t", s.tenantId(), "c", s.clientId(), "hoje", java.sql.Date.valueOf(hoje), "ativos", ATIVOS))) {
      Map<String, Object> x = m("id", a.get("id"), "salaoId", s.tenantId(), "salao", s.nome(), "data", a.get("date").toString(),
          "inicio", String.valueOf(a.get("start_time")), "fim", String.valueOf(a.get("end_time")), "status", a.get("status"),
          "profissional", a.get("profissional"), "servicos", a.get("servicos"), "total", a.get("total"));
      if (futuros) x.put("desmarcar", porQueNaoDesmarca(s, (UUID) a.get("id"), String.valueOf(a.get("status")),
          ((java.sql.Date) a.get("date")).toLocalDate(), hora(a.get("start_time"))));
      r.add(x);
    }
    return r;
  }

  // ─── Desmarcar ─────────────────────────────────────────────────────────────

  /** appointments.start_time e texto ("16:00"). */
  static java.time.LocalTime hora(Object v) {
    String h = String.valueOf(v);
    return java.time.LocalTime.parse(h.length() >= 5 ? h.substring(0, 5) : h);
  }

  /**
   * Nulo quando a cliente pode desmarcar; senao, o motivo. Segue a politica de cancelamento do salao (horas minimas
   * antes do horario); horario com sinal pago ou ja em atendimento so o salao desmarca.
   */
  String porQueNaoDesmarca(Salao s, UUID agendamento, String status, LocalDate data, java.time.LocalTime inicio) {
    if (!"Pendente".equals(status) && !"Confirmado".equals(status)) return "Este horário já começou. Fale com o salão.";
    java.time.ZonedDateTime comeca = java.time.ZonedDateTime.of(data, inicio, AnamneseDaCliente.ZONA_BR);
    java.time.ZonedDateTime agora = java.time.ZonedDateTime.now(AnamneseDaCliente.ZONA_BR);
    if (!comeca.isAfter(agora)) return "O horário já passou. Fale com o salão.";
    Boolean sinal = jdbc.queryForObject("select exists(select 1 from appointment_deposits where tenant_id = :t and appointment_id = :a"
        + " and paid_at is not null)", Map.of("t", s.tenantId(), "a", agendamento), Boolean.class);
    if (Boolean.TRUE.equals(sinal)) return "Este horário tem sinal pago. Para desmarcar, fale com o salão.";
    Map<String, Object> pol = jdbc.query("select cancellation_policy_enabled, cancellation_min_hours_before from tenant_operational_settings"
        + " where tenant_id = :t", Map.of("t", s.tenantId()), rs -> rs.next()
            ? m("ativa", rs.getBoolean(1), "horas", rs.getObject(2) == null ? 0 : rs.getInt(2)) : null);
    if (pol != null && Boolean.TRUE.equals(pol.get("ativa"))) {
      int horas = (Integer) pol.get("horas");
      if (horas > 0 && comeca.isBefore(agora.plusHours(horas))) {
        return "Pela regra do salão, só dá para desmarcar até " + horas + " horas antes. Fale com o salão.";
      }
    }
    return null;
  }

  @Transactional
  public Map<String, Object> desmarcar(UUID conta, UUID tenantId, UUID agendamentoId) {
    Salao s = salao(conta, tenantId);
    List<Map<String, Object>> r = jdbc.queryForList("select status, date, start_time from appointments where tenant_id = :t and id = :a"
        + " and client_id = :c", Map.of("t", s.tenantId(), "a", agendamentoId, "c", s.clientId()));
    if (r.isEmpty()) throw new ApiClientErrorException("Horário não encontrado.", 404);
    String motivo = porQueNaoDesmarca(s, agendamentoId, String.valueOf(r.get(0).get("status")),
        ((java.sql.Date) r.get(0).get("date")).toLocalDate(), hora(r.get(0).get("start_time")));
    if (motivo != null) throw new IllegalArgumentException(motivo);
    if (agendamentos == null || contextoTenant == null) throw new IllegalStateException("Desmarcar não está disponível.");
    contextoTenant.definirTenantId(s.tenantId());
    try {
      agendamentos.atualizarStatus(agendamentoId, "CANCELLED");
    } finally {
      contextoTenant.limparTenantIdOverride();
    }
    acesso.auditar(s.tenantId(), "CLIENT_AREA_APPOINTMENT_CANCEL", s.clientId(), m("agendamento", agendamentoId, "conta", conta), false);
    avisarSalao(s, agendamentoId, "DESMARCOU", "desmarcou o horário de " + r.get(0).get("date") + " às "
        + String.valueOf(r.get(0).get("start_time")).substring(0, 5) + " pela área da cliente.");
    contas.evento(conta, "DESMARCOU_HORARIO", s.nome());
    return doSalao(conta, tenantId);
  }

  /** Aviso no sino do salao quando a cliente faz algo na area. Falhar aqui nunca derruba a acao dela. */
  void avisarSalao(Salao s, UUID agendamento, String tipo, String texto) {
    if (avisos == null) return;
    try {
      String nome = jdbc.queryForObject("select name from clients where id = :c", Map.of("c", s.clientId()), String.class);
      avisos.publish(s.tenantId(), agendamento, null, "CLIENT_AREA", "cliente:" + s.clientId() + ":" + tipo,
          nome + " " + texto, StatusNotification.SENT, null, java.time.Instant.now(), 300L);
    } catch (Exception ignored) {
      // so um aviso
    }
  }

  // ─── Pacotes, assinaturas, vales e pontos (so leitura) ─────────────────────

  Map<String, Object> beneficios(Salao s) {
    Map<String, Object> p = Map.of("t", s.tenantId(), "c", s.clientId());
    List<Map<String, Object>> pacotes = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select pp.package_nome, pp.created_at, b.service_nome, b.sessoes_totais, b.sessoes_usadas, b.expira_em
          from client_package_purchases pp join client_package_balances b on b.purchase_id = pp.id
         where pp.tenant_id = :t and pp.client_id = :c order by pp.created_at desc, b.service_nome""", p)) {
      pacotes.add(m("pacote", x.get("package_nome"), "servico", x.get("service_nome"), "total", x.get("sessoes_totais"),
          "usadas", x.get("sessoes_usadas"), "expiraEm", Saude.instante(x.get("expira_em")), "compradoEm", Saude.instante(x.get("created_at"))));
    }
    List<Map<String, Object>> assinaturasDoClube = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select ms.id, ms.plan_nome, ms.status, ms.period_end, ms.cancel_at_period_end from client_memberships ms
         where ms.tenant_id = :t and ms.client_id = :c order by ms.created_at desc""", p)) {
      List<Map<String, Object>> saldos = new ArrayList<>();
      for (Map<String, Object> b : jdbc.queryForList("select service_nome, quantidade_mensal, usadas_no_periodo from client_membership_balances"
          + " where membership_id = :m order by service_nome", Map.of("m", x.get("id")))) {
        saldos.add(m("servico", b.get("service_nome"), "porMes", b.get("quantidade_mensal"), "usadas", b.get("usadas_no_periodo")));
      }
      assinaturasDoClube.add(m("plano", x.get("plan_nome"), "status", x.get("status"), "ate", Saude.instante(x.get("period_end")),
          "encerraNoFim", x.get("cancel_at_period_end"), "servicos", saldos));
    }
    List<Map<String, Object>> vales = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select codigo, valor_inicial, saldo, expira_em, status, destinatario_nome, created_at from vales_presente
         where tenant_id = :t and comprador_client_id = :c order by created_at desc""", p)) {
      vales.add(m("codigo", x.get("codigo"), "valor", x.get("valor_inicial"), "saldo", x.get("saldo"),
          "expiraEm", x.get("expira_em") == null ? null : x.get("expira_em").toString(), "status", x.get("status"),
          "para", x.get("destinatario_nome"), "compradoEm", Saude.instante(x.get("created_at"))));
    }
    Map<String, Object> pontos = jdbc.query("""
        select c.loyalty_points, l.pontos_por_resgate_real from clients c
          join tenant_loyalty_settings l on l.tenant_id = c.tenant_id and l.ativo where c.id = :c and c.tenant_id = :t""",
        p, rs -> rs.next() ? m("saldo", rs.getObject(1), "pontosPorReal", rs.getObject(2)) : null);
    return m("pacotes", pacotes, "clube", assinaturasDoClube, "vales", vales, "pontos", pontos);
  }

  /** Tudo o que o salao tem da cliente, para ela baixar (a mesma base da exportacao LGPD). */
  @Transactional(readOnly = true)
  public Map<String, Object> meusDados(UUID conta, UUID tenantId) {
    Salao s = salao(conta, tenantId);
    Map<String, Object> cadastro = jdbc.queryForMap("select name as nome, phone as telefone, email, birth_date::text as nascimento,"
        + " cpf_cnpj as documento, created_at::text as cliente_desde from clients where id = :c", Map.of("c", s.clientId()));
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("salao", s.nome());
    r.put("geradoEm", java.time.Instant.now().toString());
    r.put("cadastro", cadastro);
    List<Map<String, Object>> horarios = new ArrayList<>(agendamentos(s, true));
    horarios.addAll(agendamentos(s, false));
    r.put("horarios", horarios);
    r.put("saude", lgpd.exportar(s.tenantId(), s.clientId()));
    r.put("beneficios", beneficios(s));
    registrar(s.tenantId(), s.clientId(), "AREA_MEUS_DADOS", conta);
    return r;
  }

  /** Exames que os servicos dos proximos horarios exigem e que a cliente nao enviou nos ultimos 12 meses. */
  List<Map<String, Object>> examesExigidos(Salao s, List<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    List<UUID> servicos = jdbc.queryForList("select distinct service_id from appointment_items where appointment_id in (:a)"
        + " and service_id is not null", Map.of("a", ids), UUID.class);
    Boolean temExame = jdbc.queryForObject("select exists(select 1 from exames_da_cliente where tenant_id = :t and client_id = :c"
        + " and arquivado_em is null and created_at > now() - interval '12 months')", Map.of("t", s.tenantId(), "c", s.clientId()), Boolean.class);
    if (Boolean.TRUE.equals(temExame)) return List.of();
    List<Map<String, Object>> r = new ArrayList<>();
    Set<String> vistos = new LinkedHashSet<>();
    for (Map<String, Object> e : modelos.exigenciasDosServicos(s.tenantId(), servicos)) {
      Object exame = e.get("exame_exigido");
      if (exame != null && vistos.add(String.valueOf(exame))) r.add(m("descricao", exame, "servico", e.get("servico")));
    }
    return r;
  }

  /** Alertas calculados das anamneses atuais. Os alertas manuais do salao ficam de fora (decisao do dono). */
  List<Map<String, Object>> alertas(Salao s) {
    Set<String> vistos = new LinkedHashSet<>();
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> a : jdbc.queryForList("""
        select v.respostas, mv.perguntas from anamneses a
          join versoes_de_anamnese v on v.id = a.versao_atual_id
          join versoes_de_modelo_de_anamnese mv on mv.id = v.versao_do_modelo_id
         where a.tenant_id = :t and a.client_id = :c""", Map.of("t", s.tenantId(), "c", s.clientId()))) {
      for (Map<String, Object> x : AnamneseDaCliente.alertasDasRespostas(Saude.lista(a.get("perguntas")), Saude.mapa(a.get("respostas")))) {
        if (vistos.add(x.get("tipo") + "|" + x.get("texto"))) r.add(m("tipo", x.get("tipo"), "texto", x.get("texto")));
      }
    }
    return r;
  }

  /** Fichas fechadas, so com o que a cliente pode ver. */
  List<Map<String, Object>> atendimentos(Salao s) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> f : jdbc.queryForList("""
        select f.id, f.appointment_id, f.cuidados, f.opiniao_cliente, f.fechada_em, a.date, a.start_time, p.name as profissional
          from fichas_de_atendimento f join appointments a on a.id = f.appointment_id
          left join professionals p on p.id = f.professional_id
         where f.tenant_id = :t and f.client_id = :c and f.status = 'FECHADA' order by a.date desc, a.start_time desc limit 100""",
        Map.of("t", s.tenantId(), "c", s.clientId()))) {
      UUID ficha = (UUID) f.get("id");
      List<Map<String, Object>> secoes = new ArrayList<>();
      for (Map<String, Object> x : jdbc.queryForList("select servico_nome, procedimento, tecnicas, produtos from secoes_da_ficha"
          + " where ficha_id = :f order by ordem", Map.of("f", ficha))) {
        List<String> produtos = new ArrayList<>();
        for (Map<String, Object> p : Saude.lista(x.get("produtos"))) if (p.get("nome") != null) produtos.add(String.valueOf(p.get("nome")));
        secoes.add(m("servico", x.get("servico_nome"), "procedimento", x.get("procedimento"), "tecnicas", x.get("tecnicas"),
            "produtos", produtos));
      }
      List<Map<String, Object>> inter = new ArrayList<>();
      for (Map<String, Object> i : jdbc.queryForList("select descricao, gravidade, conduta, created_at from intercorrencias_da_ficha"
          + " where ficha_id = :f order by created_at", Map.of("f", ficha))) {
        inter.add(m("descricao", i.get("descricao"), "gravidade", i.get("gravidade"), "conduta", i.get("conduta"),
            "em", Saude.instante(i.get("created_at"))));
      }
      List<Map<String, Object>> adendos = new ArrayList<>();
      for (Map<String, Object> d : jdbc.queryForList("select tipo, texto, gravidade, created_at from adendos_da_ficha"
          + " where ficha_id = :f and tipo <> 'NOTA' order by created_at", Map.of("f", ficha))) {
        adendos.add(m("tipo", d.get("tipo"), "texto", d.get("texto"), "gravidade", d.get("gravidade"), "em", Saude.instante(d.get("created_at"))));
      }
      List<Map<String, Object>> fotos = new ArrayList<>();
      for (Map<String, Object> x : jdbc.queryForList("select id, etapa, descricao, created_at from fotos_da_ficha where ficha_id = :f"
          + " and removida_em is null and arquivo_id is not null order by created_at", Map.of("f", ficha))) {
        fotos.add(m("id", x.get("id"), "etapa", x.get("etapa"), "descricao", x.get("descricao"), "em", Saude.instante(x.get("created_at"))));
      }
      r.add(m("id", ficha, "appointmentId", f.get("appointment_id"), "salaoId", s.tenantId(), "salao", s.nome(),
          "data", f.get("date").toString(), "inicio", String.valueOf(f.get("start_time")), "profissional", f.get("profissional"),
          "servicos", secoes, "cuidados", f.get("cuidados"), "opiniao", f.get("opiniao_cliente"), "intercorrencias", inter,
          "adendos", adendos, "fotos", fotos));
    }
    return r;
  }

  List<Map<String, Object>> documentos(Salao s) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> a : assinaturas.historico(s.tenantId(), s.clientId())) {
      r.add(m("id", a.get("id"), "evento", a.get("evento"), "tipo", a.get("tipo_documento"),
          "documento", a.get("documento") != null ? a.get("documento") : AssinaturasDaCliente.rotulo(String.valueOf(a.get("tipo_documento"))),
          "em", a.get("created_at"), "hash", a.get("hash_conteudo"), "salaoId", s.tenantId(), "salao", s.nome()));
    }
    return r;
  }

  private List<UUID> futuros(Salao s) {
    List<UUID> r = new ArrayList<>();
    for (Map<String, Object> a : agendamentos(s, true)) r.add((UUID) a.get("id"));
    return r;
  }

  /** O que falta a cliente fazer naquele salao. */
  List<Map<String, Object>> pendencias(Salao s, List<Map<String, Object>> anamneses, List<Map<String, Object>> termos,
      List<Map<String, Object>> pedidos) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> a : anamneses) {
      if (!Boolean.TRUE.equals(a.get("completa"))) r.add(m("tipo", "ANAMNESE", "texto", "Preencha a sua ficha: " + a.get("nome"), "id", a.get("modeloId")));
      else if (!Boolean.TRUE.equals(a.get("confirmada"))) r.add(m("tipo", "ANAMNESE", "texto", "Confirme a sua ficha: " + a.get("nome"), "id", a.get("modeloId")));
    }
    for (Map<String, Object> t : termos) {
      if (Boolean.TRUE.equals(t.get("exigido")) && !Boolean.TRUE.equals(t.get("assinado"))) {
        r.add(m("tipo", "TERMO", "texto", "Assine: " + t.get("nome"), "id", t.get("documentoId")));
      }
    }
    for (Map<String, Object> p : pedidos) r.add(m("tipo", "EXAME", "texto", "Envie o exame: " + p.get("descricao"), "id", p.get("id")));
    for (Map<String, Object> e : examesExigidos(s, futuros(s))) {
      r.add(m("tipo", "EXAME", "texto", "Envie o exame: " + e.get("descricao") + " (pedido para " + e.get("servico") + ")",
          "id", "servico:" + e.get("descricao")));
    }
    for (Map<String, Object> x : r) {
      x.put("salaoId", s.tenantId());
      x.put("salao", s.nome());
    }
    return r;
  }

  // ─── Telas ─────────────────────────────────────────────────────────────────

  /** Tela inicial: os salões, as pendências e os próximos horarios de todos os salões. */
  @Transactional(readOnly = true)
  public Map<String, Object> inicio(UUID conta) {
    List<Map<String, Object>> lista = new ArrayList<>();
    List<Map<String, Object>> pend = new ArrayList<>();
    List<Map<String, Object>> proximos = new ArrayList<>();
    for (Salao s : saloes(conta)) {
      List<Map<String, Object>> ag = agendamentos(s, true);
      List<UUID> ids = ag.stream().map(a -> (UUID) a.get("id")).toList();
      List<Map<String, Object>> p = pendencias(s, links.anamnesesOferecidas(s.tenantId(), s.clientId(), ids),
          links.termosOferecidos(s.tenantId(), s.clientId(), ids), exames.pedidosAbertos(s.tenantId(), s.clientId()));
      pend.addAll(p);
      proximos.addAll(ag);
      lista.add(m("salaoId", s.tenantId(), "salao", s.nome(), "pendencias", p.size(), "proximos", ag.size()));
    }
    proximos.sort(Comparator.comparing((Map<String, Object> a) -> a.get("data") + "T" + a.get("inicio")));
    Map<String, Object> c = contas.minhaConta(conta);
    return m("nome", c.get("nome"), "saloes", lista, "pendencias", pend, "proximos", proximos);
  }

  /** "Tudo": uma linha do tempo com os registros de todos os salões, com o nome do salao em cada item. */
  @Transactional(readOnly = true)
  public Map<String, Object> linhaDoTempo(UUID conta, UUID somenteSalao) {
    List<Map<String, Object>> itens = new ArrayList<>();
    for (Salao s : saloes(conta)) {
      if (somenteSalao != null && !somenteSalao.equals(s.tenantId())) continue;
      for (Map<String, Object> a : atendimentos(s)) {
        List<String> nomes = new ArrayList<>();
        for (Object x : (List<?>) a.get("servicos")) {
          Object n = ((Map<?, ?>) x).get("servico");
          if (n != null) nomes.add(String.valueOf(n));
        }
        itens.add(m("tipo", "ATENDIMENTO", "em", a.get("data") + "T" + a.get("inicio"), "id", a.get("id"), "salaoId", s.tenantId(),
            "salao", s.nome(), "titulo", "Atendimento: " + (nomes.isEmpty() ? "serviço" : String.join(", ", nomes)),
            "detalhe", a.get("profissional") == null ? null : "com " + a.get("profissional"), "cuidados", a.get("cuidados")));
      }
      for (Map<String, Object> v : jdbc.queryForList("""
          select v.id, v.numero, v.created_at, m.nome from versoes_de_anamnese v join anamneses a on a.id = v.anamnese_id
            join modelos_de_anamnese m on m.id = a.modelo_id where v.tenant_id = :t and v.client_id = :c order by v.created_at desc limit 100""",
          Map.of("t", s.tenantId(), "c", s.clientId()))) {
        itens.add(m("tipo", "ANAMNESE", "em", Saude.instante(v.get("created_at")), "id", v.get("id"), "salaoId", s.tenantId(),
            "salao", s.nome(), "titulo", "Ficha de saúde: " + v.get("nome") + " (versão " + v.get("numero") + ")"));
      }
      for (Map<String, Object> d : documentos(s)) {
        String verbo = switch (String.valueOf(d.get("evento"))) {
          case "ASSINATURA" -> "Assinado";
          case "RECUSA" -> "Recusado";
          default -> "Revogado";
        };
        itens.add(m("tipo", "DOCUMENTO", "em", d.get("em"), "id", d.get("id"), "salaoId", s.tenantId(), "salao", s.nome(),
            "titulo", verbo + ": " + d.get("documento")));
      }
      for (Map<String, Object> e : exames.lista(s.tenantId(), s.clientId(), false)) {
        itens.add(m("tipo", "EXAME", "em", e.get("dataExame") != null ? e.get("dataExame") + "T00:00" : e.get("enviadoEm"), "id", e.get("id"),
            "salaoId", s.tenantId(), "salao", s.nome(), "titulo", "Exame: " + e.get("titulo"),
            "detalhe", "CLIENTE".equals(e.get("enviadoPor")) ? "enviado por você" : "enviado pelo salão"));
      }
      registrar(s.tenantId(), s.clientId(), "AREA_LINHA_DO_TEMPO", conta);
    }
    itens.sort(Comparator.comparing((Map<String, Object> x) -> String.valueOf(x.get("em"))).reversed());
    return m("itens", itens.size() > 300 ? itens.subList(0, 300) : itens);
  }

  /** Tudo de um salao. */
  @Transactional(readOnly = true)
  public Map<String, Object> doSalao(UUID conta, UUID tenantId) {
    Salao s = salao(conta, tenantId);
    List<UUID> ids = futuros(s);
    List<Map<String, Object>> anamneses = links.anamnesesOferecidas(s.tenantId(), s.clientId(), ids);
    List<Map<String, Object>> termos = links.termosOferecidos(s.tenantId(), s.clientId(), ids);
    List<Map<String, Object>> pedidos = exames.pedidosAbertos(s.tenantId(), s.clientId());
    Map<String, Object> cli = jdbc.queryForMap("select birth_date from clients where id = :c", Map.of("c", s.clientId()));
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("salaoId", s.tenantId());
    r.put("salao", s.nome());
    r.put("menor", AnamneseDaCliente.menor(cli.get("birth_date") == null ? null : ((java.sql.Date) cli.get("birth_date")).toLocalDate(),
        LocalDate.now(AnamneseDaCliente.ZONA_BR)));
    r.put("pendencias", pendencias(s, anamneses, termos, pedidos));
    r.put("proximos", agendamentos(s, true));
    r.put("historico", agendamentos(s, false));
    r.put("atendimentos", atendimentos(s));
    r.put("alertas", alertas(s));
    r.put("anamneses", anamneses);
    r.put("termos", termos);
    r.put("documentos", documentos(s));
    r.put("exames", exames.lista(s.tenantId(), s.clientId(), false));
    r.put("pedidosDeExame", pedidos);
    r.put("beneficios", beneficios(s));
    registrar(s.tenantId(), s.clientId(), "AREA_SALAO", conta);
    return r;
  }

  // ─── Acoes ─────────────────────────────────────────────────────────────────

  @Transactional
  public Map<String, Object> salvarAnamnese(UUID conta, UUID tenantId, UUID modeloId, Map<String, Object> respostas) {
    Salao s = salao(conta, tenantId);
    if (!links.modelosOferecidos(s.tenantId(), futuros(s)).contains(modeloId)) {
      throw new ApiClientErrorException("Esta ficha não está disponível para você neste salão.", 403);
    }
    UUID v = anamnese.salvarVersao(s.tenantId(), s.clientId(), modeloId, respostas, "AREA", null, null, conta);
    avisarSalao(s, null, "ANAMNESE", "atualizou a ficha de saúde pela área da cliente.");
    return m("versaoId", v, "salao", doSalao(conta, tenantId));
  }

  @Transactional
  public Map<String, Object> assinar(UUID conta, UUID tenantId, AssinaturasDaCliente.Pedido pedido, String ip, String userAgent) {
    Salao s = salao(conta, tenantId);
    List<UUID> ids = futuros(s);
    if ("ANAMNESE".equals(pedido.tipoDocumento())) {
      Set<UUID> modelos = links.modelosOferecidos(s.tenantId(), ids);
      Boolean ok = modelos.isEmpty() ? Boolean.FALSE : jdbc.queryForObject("select exists(select 1 from versoes_de_anamnese v"
          + " join anamneses a on a.id = v.anamnese_id where v.id = :v and v.client_id = :c and a.modelo_id in (:ms))",
          Map.of("v", pedido.versaoAnamneseId(), "c", s.clientId(), "ms", new ArrayList<>(modelos)), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new ApiClientErrorException("Esta ficha não está disponível para você neste salão.", 403);
    } else if ("FICHA".equals(pedido.tipoDocumento())) {
      throw new ApiClientErrorException("A ficha do atendimento é assinada no salão.", 403);
    } else {
      boolean oferecido = links.termosOferecidos(s.tenantId(), s.clientId(), ids).stream()
          .anyMatch(x -> String.valueOf(x.get("documentoId")).equals(String.valueOf(pedido.documentoId())));
      if (!oferecido) throw new ApiClientErrorException("Este termo não está disponível para você neste salão.", 403);
    }
    AssinaturasDaCliente.Pedido semAgendamento = new AssinaturasDaCliente.Pedido(pedido.evento(), pedido.tipoDocumento(),
        pedido.documentoId(), pedido.versaoAnamneseId(), null, pedido.assinanteNome(), pedido.assinanteDocumento(),
        pedido.responsavelLegal(), pedido.parentesco(), pedido.imagemPng(), pedido.motivo(), pedido.aceite());
    Map<String, Object> r = assinaturas.registrar(s.tenantId(), s.clientId(), semAgendamento,
        new AssinaturasDaCliente.Origem("AREA", ip, userAgent, null, null, conta));
    String verbo = "RECUSA".equals(pedido.evento()) ? "recusou" : "REVOGACAO".equals(pedido.evento()) ? "revogou" : "assinou";
    avisarSalao(s, null, "DOCUMENTO", verbo + " um documento pela área da cliente (" + AssinaturasDaCliente.rotulo(pedido.tipoDocumento()) + ").");
    return m("assinatura", r, "salao", doSalao(conta, tenantId));
  }

  @Transactional(readOnly = true)
  public byte[] pdf(UUID conta, UUID tenantId, UUID assinaturaId) {
    Salao s = salao(conta, tenantId);
    Map<String, Object> a = assinaturas.linha(s.tenantId(), s.clientId(), assinaturaId);
    if (!"ASSINATURA".equals(a.get("evento"))) throw new IllegalArgumentException("Documento não encontrado.");
    registrar(s.tenantId(), s.clientId(), "AREA_DOCUMENTO", conta);
    return assinaturas.pdf(s.tenantId(), a);
  }

  @Transactional(readOnly = true)
  public ArquivosProtegidos.Arquivo foto(UUID conta, UUID tenantId, UUID fotoId) {
    Salao s = salao(conta, tenantId);
    List<Map<String, Object>> r = jdbc.queryForList("""
        select f.arquivo_id from fotos_da_ficha f join fichas_de_atendimento fi on fi.id = f.ficha_id
         where f.tenant_id = :t and f.client_id = :c and f.id = :f and f.removida_em is null and fi.status = 'FECHADA'""",
        Map.of("t", s.tenantId(), "c", s.clientId(), "f", fotoId));
    if (r.isEmpty() || r.get(0).get("arquivo_id") == null) throw new IllegalArgumentException("Foto não encontrada.");
    registrar(s.tenantId(), s.clientId(), "AREA_FOTO", conta);
    return arquivos.ler(s.tenantId(), (UUID) r.get(0).get("arquivo_id"));
  }

  @Transactional
  public Map<String, Object> enviarExame(UUID conta, UUID tenantId, byte[] bytes, ExamesDaCliente.Envio envio) {
    Salao s = salao(conta, tenantId);
    exames.guardar(s.tenantId(), s.clientId(), bytes, envio, "CLIENTE", null, conta);
    avisarSalao(s, null, "EXAME", "enviou um exame pela área da cliente.");
    return doSalao(conta, tenantId);
  }

  @Transactional(readOnly = true)
  public ArquivosProtegidos.Arquivo exame(UUID conta, UUID tenantId, UUID exameId) {
    Salao s = salao(conta, tenantId);
    registrar(s.tenantId(), s.clientId(), "AREA_EXAME", conta);
    return exames.arquivo(s.tenantId(), s.clientId(), exameId);
  }

  @Transactional
  public Map<String, Object> apagarExame(UUID conta, UUID tenantId, UUID exameId) {
    Salao s = salao(conta, tenantId);
    exames.apagarDaCliente(s.tenantId(), s.clientId(), conta, exameId);
    return doSalao(conta, tenantId);
  }
}
