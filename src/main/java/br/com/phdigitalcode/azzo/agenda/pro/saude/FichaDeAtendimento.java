package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

/**
 * Ficha de atendimento: uma por agendamento, com uma secao por servico (plano, 3.3). Rascunho editavel; fechada na
 * conclusao do atendimento (so adendos depois); arquivada se o agendamento for cancelado com conteudo.
 *
 * <p>Substitui as notas do atendimento (appointment_customer_notes): para concluir, basta a ficha com conteudo ou
 * uma nota antiga (agendamentos de antes do modulo).
 */
@Service
public class FichaDeAtendimento {

  static final Set<String> GRAVIDADES = Set.of("LEVE", "MODERADA", "GRAVE");
  static final Set<String> TIPOS_DE_ADENDO = Set.of("NOTA", "INTERCORRENCIA", "CORRECAO");

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;

  public FichaDeAtendimento(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  static boolean preenchido(Object v) {
    if (v == null) return false;
    if (v instanceof List<?> l) return !l.isEmpty();
    return !v.toString().isBlank() && !"[]".equals(v.toString().trim());
  }

  /** A ficha tem algo registrado? (texto em qualquer campo, intercorrencia ou foto) */
  static boolean temConteudo(Map<String, Object> ficha, List<Map<String, Object>> secoes, int intercorrencias, int fotos) {
    if (intercorrencias > 0 || fotos > 0) return true;
    for (String c : List.of("observacoes", "opiniao_cliente", "cuidados")) if (preenchido(ficha.get(c))) return true;
    for (Map<String, Object> s : secoes) {
      for (String c : List.of("procedimento", "tecnicas", "observacoes", "produtos")) if (preenchido(s.get(c))) return true;
    }
    return false;
  }

  // ─── Leitura ───────────────────────────────────────────────────────────────

  Map<String, Object> agendamento(UUID t, UUID appointmentId) {
    List<Map<String, Object>> r = jdbc.queryForList("select id, client_id, professional_id, status, date, start_time"
        + " from appointments where tenant_id = :t and id = :a", Map.of("t", t, "a", appointmentId));
    if (r.isEmpty()) throw new IllegalArgumentException("Agendamento não encontrado.");
    return r.get(0);
  }

  Map<String, Object> fichaDoAgendamento(UUID t, UUID appointmentId) {
    List<Map<String, Object>> r = jdbc.queryForList("select * from fichas_de_atendimento where tenant_id = :t and appointment_id = :a",
        Map.of("t", t, "a", appointmentId));
    return r.isEmpty() ? null : new LinkedHashMap<>(r.get(0));
  }

  Map<String, Object> fichaPorId(UUID t, UUID fichaId) {
    List<Map<String, Object>> r = jdbc.queryForList("select * from fichas_de_atendimento where tenant_id = :t and id = :f",
        Map.of("t", t, "f", fichaId));
    if (r.isEmpty()) throw new IllegalArgumentException("Ficha não encontrada.");
    return new LinkedHashMap<>(r.get(0));
  }

  List<Map<String, Object>> secoes(UUID fichaId) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> s : jdbc.queryForList("""
        select s.id, s.appointment_item_id, s.service_id, s.servico_nome, s.professional_id, p.name as profissional,
               s.procedimento, s.tecnicas, s.produtos, s.observacoes
          from secoes_da_ficha s left join professionals p on p.id = s.professional_id
         where s.ficha_id = :f order by s.ordem""", Map.of("f", fichaId))) {
      Map<String, Object> x = new LinkedHashMap<>(s);
      x.put("produtos", Saude.lista(s.get("produtos")));
      r.add(x);
    }
    return r;
  }

  int contar(String tabela, UUID fichaId) {
    Integer n = jdbc.queryForObject("select count(*) from " + tabela + " where ficha_id = :f"
        + ("fotos_da_ficha".equals(tabela) ? " and removida_em is null" : ""), Map.of("f", fichaId), Integer.class);
    return n == null ? 0 : n;
  }

  /** A ficha do agendamento (ou nula), com pendencias e o que o usuario pode fazer. */
  @Transactional(readOnly = true)
  public Map<String, Object> obter(UUID appointmentId) {
    UUID t = acesso.tenant();
    Map<String, Object> ag = agendamento(t, appointmentId);
    UUID profissional = (UUID) ag.get("professional_id");
    UUID clientId = (UUID) ag.get("client_id");
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("pendencias", anamnese.pendencias(t, appointmentId, true));
    boolean podeVer = acesso.podeVerFicha(t, profissional);
    r.put("podeVer", podeVer);
    r.put("podeEscrever", acesso.podeEscreverFicha(t, profissional));
    r.put("podeVerFotos", acesso.podeVerFotos(t, profissional));
    Map<String, Object> ficha = fichaDoAgendamento(t, appointmentId);
    if (ficha == null || !podeVer) {
      r.put("ficha", ficha == null ? null : m("id", ficha.get("id"), "status", ficha.get("status")));
      r.put("produtosDaComanda", List.of());
      return r;
    }
    r.put("ficha", detalhe(t, ficha));
    r.put("produtosDaComanda", jdbc.queryForList("""
        select distinct ci.referencia_id as produto_id, ci.descricao as nome from comanda_itens ci
          join comandas c on c.id = ci.comanda_id
         where c.tenant_id = :t and c.appointment_id = :a and ci.tipo = 'PRODUTO'""", Map.of("t", t, "a", appointmentId)));
    if (clientId != null) acesso.registrarAcesso(t, "FICHA", clientId, (UUID) ficha.get("id"));
    return r;
  }

  Map<String, Object> detalhe(UUID t, Map<String, Object> ficha) {
    UUID f = (UUID) ficha.get("id");
    Map<String, Object> d = new LinkedHashMap<>();
    for (String c : List.of("id", "appointment_id", "client_id", "professional_id", "status", "observacoes", "opiniao_cliente",
        "cuidados", "justificativa_inicio", "arquivada_motivo")) d.put(c, ficha.get(c));
    d.put("inicio", Saude.instante(ficha.get("inicio")));
    d.put("fim", Saude.instante(ficha.get("fim")));
    d.put("fechada_em", Saude.instante(ficha.get("fechada_em")));
    d.put("created_at", Saude.instante(ficha.get("created_at")));
    d.put("secoes", secoes(f));
    d.put("intercorrencias", jdbc.queryForList("select id, descricao, gravidade, conduta, created_at from intercorrencias_da_ficha"
        + " where ficha_id = :f order by created_at", Map.of("f", f)));
    d.put("adendos", jdbc.queryForList("""
        select a.id, a.tipo, a.texto, a.gravidade, a.motivo, a.created_at, u.name as autor
          from adendos_da_ficha a left join users u on u.id = a.created_by where a.ficha_id = :f order by a.created_at""", Map.of("f", f)));
    d.put("assinaturas", jdbc.queryForList("select id, evento, assinante_nome, metodo, hash_conteudo, created_at"
        + " from assinaturas_do_cliente where tenant_id = :t and ficha_id = :f and tipo_documento = 'FICHA' order by created_at",
        Map.of("t", t, "f", f)));
    d.put("assinadaDepoisDoUltimoAdendo", assinadaAtual(t, f));
    return d;
  }

  /** Ha assinatura da ficha mais nova que o ultimo adendo (adendo depois da assinatura pede nova confirmacao). */
  boolean assinadaAtual(UUID t, UUID fichaId) {
    Boolean r = jdbc.queryForObject("""
        select coalesce((select max(created_at) from assinaturas_do_cliente where tenant_id = :t and ficha_id = :f
                          and tipo_documento = 'FICHA' and evento = 'ASSINATURA')
                        > coalesce((select max(created_at) from adendos_da_ficha where ficha_id = :f), 'epoch'::timestamptz), false)""",
        Map.of("t", t, "f", fichaId), Boolean.class);
    return Boolean.TRUE.equals(r);
  }

  // ─── Escrita ───────────────────────────────────────────────────────────────

  /** Abre a ficha (ou devolve a que ja existe). */
  @Transactional
  public Map<String, Object> abrir(UUID appointmentId) {
    UUID t = acesso.tenant();
    Map<String, Object> ag = agendamento(t, appointmentId);
    if (!acesso.podeEscreverFicha(t, (UUID) ag.get("professional_id"))) {
      throw new ApiClientErrorException("Só a profissional do atendimento abre a ficha.", 403);
    }
    abrirInterno(t, ag, null);
    return obter(appointmentId);
  }

  /** Cria a ficha se nao existe; a unicidade e do banco (appointment_id unico). */
  UUID abrirInterno(UUID t, Map<String, Object> ag, String justificativa) {
    UUID appointmentId = (UUID) ag.get("id");
    String status = String.valueOf(ag.get("status"));
    if (status.equals("CANCELLED") || status.equals("NO_SHOW")) {
      throw new IllegalArgumentException("Agendamento cancelado ou falta não tem ficha.");
    }
    Map<String, Object> existente = fichaDoAgendamento(t, appointmentId);
    if (existente != null) return (UUID) existente.get("id");
    if (ag.get("client_id") == null) throw new IllegalArgumentException("O agendamento não tem cliente.");
    UUID id = UUID.randomUUID();
    int criadas = jdbc.update("""
        insert into fichas_de_atendimento (id, tenant_id, appointment_id, client_id, professional_id, justificativa_inicio, created_by)
        values (:id, :t, :a, :c, :p, :j, :u) on conflict (appointment_id) do nothing""",
        m("id", id, "t", t, "a", appointmentId, "c", ag.get("client_id"), "p", ag.get("professional_id"),
            "j", Saude.limpar(justificativa, 1000), "u", acesso.usuarioId()));
    if (criadas == 0) return (UUID) fichaDoAgendamento(t, appointmentId).get("id");
    int ordem = 0;
    for (Map<String, Object> item : jdbc.queryForList("""
        select ai.id, ai.service_id, s.name,
               (select ci.professional_id from comanda_itens ci join comandas c on c.id = ci.comanda_id
                 where c.appointment_id = ai.appointment_id and ci.tipo = 'SERVICO' and ci.referencia_id = ai.service_id
                   and ci.professional_id is not null limit 1) as profissional_da_comanda
          from appointment_items ai left join services s on s.id = ai.service_id
         where ai.appointment_id = :a order by ai.created_at""", Map.of("a", appointmentId))) {
      Object prof = item.get("profissional_da_comanda") != null ? item.get("profissional_da_comanda") : ag.get("professional_id");
      jdbc.update("""
          insert into secoes_da_ficha (tenant_id, ficha_id, appointment_item_id, service_id, servico_nome, professional_id, ordem)
          values (:t, :f, :i, :s, :n, :p, :o)""", m("t", t, "f", id, "i", item.get("id"), "s", item.get("service_id"),
          "n", item.get("name"), "p", prof, "o", ordem++));
    }
    acesso.auditar(t, "HEALTH_RECORD_OPEN", (UUID) ag.get("client_id"), m("ficha", id, "agendamento", appointmentId), false);
    return id;
  }

  /** Salva o rascunho (campos, secoes e intercorrencias). Depois de fechada, so adendo. */
  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String, Object> salvar(UUID appointmentId, Map<String, Object> dados) {
    UUID t = acesso.tenant();
    Map<String, Object> ag = agendamento(t, appointmentId);
    if (!acesso.podeEscreverFicha(t, (UUID) ag.get("professional_id"))) {
      throw new ApiClientErrorException("Só a profissional do atendimento escreve na ficha.", 403);
    }
    UUID f = abrirInterno(t, ag, null);
    Map<String, Object> ficha = fichaPorId(t, f);
    if (!"RASCUNHO".equals(ficha.get("status"))) {
      throw new IllegalArgumentException("A ficha já foi fechada: use um adendo para acrescentar ou corrigir.");
    }
    jdbc.update("""
        update fichas_de_atendimento set observacoes = :o, opiniao_cliente = :op, cuidados = :c,
               inicio = coalesce(inicio, :ini), updated_at = now() where id = :f""",
        m("o", Saude.limpar(Saude.texto(dados.get("observacoes")), 5000), "op", Saude.limpar(Saude.texto(dados.get("opiniaoCliente")), 2000),
            "c", Saude.limpar(Saude.texto(dados.get("cuidados")), 5000), "ini", Timestamp.from(Instant.now()), "f", f));
    if (dados.get("secoes") instanceof List<?> lista) {
      for (Object o : lista) {
        if (!(o instanceof Map<?, ?> s)) continue;
        UUID secao = Saude.uuidOuNulo(Saude.texto(s.get("id")));
        List<Map<String, Object>> produtos = new ArrayList<>();
        if (s.get("produtos") instanceof List<?> ps) {
          for (Object p : ps) {
            if (!(p instanceof Map<?, ?> pm)) continue;
            String nome = Saude.limpar(Saude.texto(pm.get("nome")), 160);
            if (nome == null) continue;
            Map<String, Object> produto = m("nome", nome);
            UUID pid = Saude.uuidOuNulo(Saude.texto(pm.get("produtoId")));
            if (pid != null && Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from products where tenant_id = :t and id = :p)",
                Map.of("t", t, "p", pid), Boolean.class))) produto.put("produtoId", pid.toString());
            String qtd = Saude.limpar(Saude.texto(pm.get("quantidade")), 40);
            if (qtd != null) produto.put("quantidade", qtd);
            produtos.add(produto);
          }
        }
        int n = jdbc.update("""
            update secoes_da_ficha set procedimento = :p, tecnicas = :te, produtos = cast(:pr as jsonb), observacoes = :o
             where ficha_id = :f and id = :s""", m("p", Saude.limpar(Saude.texto(s.get("procedimento")), 3000),
            "te", Saude.limpar(Saude.texto(s.get("tecnicas")), 2000), "pr", Saude.json(produtos),
            "o", Saude.limpar(Saude.texto(s.get("observacoes")), 3000), "f", f, "s", secao));
        if (n == 0) throw new IllegalArgumentException("Seção da ficha não encontrada.");
      }
    }
    if (dados.get("intercorrencias") instanceof List<?> lista) {
      jdbc.update("delete from intercorrencias_da_ficha where ficha_id = :f", Map.of("f", f));
      for (Object o : lista) {
        if (!(o instanceof Map<?, ?> i)) continue;
        String descricao = Saude.limpar(Saude.texto(i.get("descricao")), 2000);
        if (descricao == null) continue;
        String gravidade = Saude.texto(i.get("gravidade"));
        if (!GRAVIDADES.contains(gravidade)) throw new IllegalArgumentException("Informe a gravidade da intercorrência.");
        jdbc.update("insert into intercorrencias_da_ficha (tenant_id, ficha_id, descricao, gravidade, conduta, created_by)"
            + " values (:t, :f, :d, :g, :c, :u)", m("t", t, "f", f, "d", descricao, "g", gravidade,
            "c", Saude.limpar(Saude.texto(i.get("conduta")), 2000), "u", acesso.usuarioId()));
        if ("GRAVE".equals(gravidade) && Boolean.TRUE.equals(i.get("gerarAlerta"))) {
          anamnese.criarAlertaInterno(t, (UUID) ag.get("client_id"), "OBSERVACAO", "Intercorrência grave: " + Saude.limpar(descricao, 250),
              "INTERCORRENCIA", f);
        }
      }
    }
    acesso.auditar(t, "HEALTH_RECORD_SAVE", (UUID) ag.get("client_id"), m("ficha", f), false);
    return obter(appointmentId);
  }

  @Transactional
  public Map<String, Object> adendo(UUID appointmentId, String tipo, String texto, String gravidade, String motivo, boolean gerarAlerta) {
    UUID t = acesso.tenant();
    Map<String, Object> ag = agendamento(t, appointmentId);
    if (!acesso.podeEscreverFicha(t, (UUID) ag.get("professional_id"))) {
      throw new ApiClientErrorException("Só a profissional do atendimento escreve na ficha.", 403);
    }
    Map<String, Object> ficha = fichaDoAgendamento(t, appointmentId);
    if (ficha == null) throw new IllegalArgumentException("Este atendimento ainda não tem ficha.");
    if ("RASCUNHO".equals(ficha.get("status"))) throw new IllegalArgumentException("A ficha ainda está aberta: edite direto.");
    String tp = tipo == null ? "NOTA" : tipo;
    if (!TIPOS_DE_ADENDO.contains(tp)) throw new IllegalArgumentException("Tipo de adendo inválido.");
    String x = Saude.limpar(texto, 3000);
    if (x == null) throw new IllegalArgumentException("Escreva o adendo.");
    String g = null;
    if (tp.equals("INTERCORRENCIA")) {
      if (!GRAVIDADES.contains(gravidade)) throw new IllegalArgumentException("Informe a gravidade da intercorrência.");
      g = gravidade;
    }
    String mot = Saude.limpar(motivo, 300);
    if (tp.equals("CORRECAO") && mot == null) throw new IllegalArgumentException("Diga o motivo da correção.");
    UUID f = (UUID) ficha.get("id");
    jdbc.update("insert into adendos_da_ficha (tenant_id, ficha_id, tipo, texto, gravidade, motivo, created_by)"
        + " values (:t, :f, :tipo, :x, :g, :mo, :u)", m("t", t, "f", f, "tipo", tp, "x", x, "g", g, "mo", mot, "u", acesso.usuarioId()));
    if ("GRAVE".equals(g) && gerarAlerta) {
      anamnese.criarAlertaInterno(t, (UUID) ag.get("client_id"), "OBSERVACAO", "Intercorrência grave: " + Saude.limpar(x, 250), "INTERCORRENCIA", f);
    }
    acesso.auditar(t, "HEALTH_RECORD_ADDENDUM", (UUID) ag.get("client_id"), m("ficha", f, "tipo", tp), false);
    return obter(appointmentId);
  }

  // ─── Ganchos do agendamento (ServicoAgendamentos) ──────────────────────────

  /**
   * Ao iniciar o atendimento: confere as exigencias "bloquear antes de iniciar" e abre a ficha. Com pendencia que
   * bloqueia, so inicia com justificativa (fica na ficha e na auditoria).
   */
  @Transactional
  public void aoIniciar(UUID t, UUID appointmentId, String justificativa) {
    Map<String, Object> ag = agendamento(t, appointmentId);
    Map<String, Object> pend = anamnese.pendencias(t, appointmentId, false);
    String just = Saude.limpar(justificativa, 1000);
    if (Boolean.TRUE.equals(pend.get("bloqueia")) && just == null) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> itens = (List<Map<String, Object>>) pend.get("itens");
      String lista = String.join("; ", itens.stream().filter(i -> "BLOQUEAR".equals(i.get("nivel"))).map(i -> String.valueOf(i.get("texto"))).toList());
      throw new IllegalArgumentException("Antes de iniciar, resolva: " + lista + ". Ou informe a justificativa para iniciar assim mesmo.");
    }
    UUID f = abrirInterno(t, ag, just);
    jdbc.update("update fichas_de_atendimento set inicio = coalesce(inicio, now()),"
        + " justificativa_inicio = coalesce(justificativa_inicio, :j), updated_at = now() where id = :f", m("j", just, "f", f));
    if (just != null) {
      acesso.auditar(t, "HEALTH_START_WITH_JUSTIFICATION", (UUID) ag.get("client_id"), m("ficha", f, "agendamento", appointmentId), false);
    }
  }

  /** Para concluir: ficha com conteudo, ou uma nota antiga do atendimento. */
  public void exigirParaConcluir(UUID t, UUID appointmentId) {
    Map<String, Object> ficha = fichaDoAgendamento(t, appointmentId);
    if (ficha != null) {
      UUID f = (UUID) ficha.get("id");
      if (temConteudo(ficha, secoes(f), contar("intercorrencias_da_ficha", f), contar("fotos_da_ficha", f))) return;
    }
    Integer notas = jdbc.queryForObject("select count(*) from appointment_customer_notes where tenant_id = :t and appointment_id = :a",
        Map.of("t", t, "a", appointmentId), Integer.class);
    if (notas != null && notas > 0) return;
    throw new IllegalArgumentException("Antes de concluir o atendimento, preencha a ficha do atendimento.");
  }

  /** Concluido: a ficha fecha (o texto fica travado; depois, so adendo). */
  @Transactional
  public void aoConcluir(UUID t, UUID appointmentId, UUID usuario) {
    jdbc.update("""
        update fichas_de_atendimento set status = 'FECHADA', fim = coalesce(fim, now()), fechada_em = now(),
               fechada_por = :u, updated_at = now() where tenant_id = :t and appointment_id = :a and status = 'RASCUNHO'""",
        m("u", usuario, "t", t, "a", appointmentId));
  }

  /** Cancelado ou falta: a ficha com conteudo e arquivada; a vazia sai. */
  @Transactional
  public void aoCancelar(UUID t, UUID appointmentId, String motivo) {
    Map<String, Object> ficha = fichaDoAgendamento(t, appointmentId);
    if (ficha == null || "ARQUIVADA".equals(ficha.get("status"))) return;
    UUID f = (UUID) ficha.get("id");
    boolean conteudo = temConteudo(ficha, secoes(f), contar("intercorrencias_da_ficha", f), contar("fotos_da_ficha", f));
    Integer ligados = jdbc.queryForObject("select (select count(*) from adendos_da_ficha where ficha_id = :f)"
        + " + (select count(*) from assinaturas_do_cliente where ficha_id = :f) + (select count(*) from fotos_da_ficha where ficha_id = :f)",
        Map.of("f", f), Integer.class);
    if (!conteudo && (ligados == null || ligados == 0)) {
      jdbc.update("delete from secoes_da_ficha where ficha_id = :f", Map.of("f", f));
      jdbc.update("delete from intercorrencias_da_ficha where ficha_id = :f", Map.of("f", f));
      jdbc.update("delete from fichas_de_atendimento where id = :f", Map.of("f", f));
      return;
    }
    jdbc.update("update fichas_de_atendimento set status = 'ARQUIVADA', arquivada_motivo = :m, updated_at = now() where id = :f",
        m("m", Saude.limpar(motivo, 300), "f", f));
  }

  /** Excluir o agendamento: a ficha vazia sai; com conteudo, nao deixa (o caminho e cancelar). */
  @Transactional
  public void aoExcluir(UUID t, UUID appointmentId) {
    Map<String, Object> ficha = fichaDoAgendamento(t, appointmentId);
    if (ficha == null) return;
    aoCancelar(t, appointmentId, "agendamento excluído");
    if (fichaDoAgendamento(t, appointmentId) != null) {
      throw new IllegalArgumentException("Este atendimento tem ficha preenchida: cancele o agendamento em vez de excluir.");
    }
  }
}
