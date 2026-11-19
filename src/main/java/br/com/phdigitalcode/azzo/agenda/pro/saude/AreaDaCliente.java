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

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

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

  public AreaDaCliente(NamedParameterJdbcTemplate jdbc, ContasDaCliente contas, LinksDaCliente links, AnamneseDaCliente anamnese,
      AssinaturasDaCliente assinaturas, ArquivosProtegidos arquivos, ExamesDaCliente exames, AcessoDeSaude acesso) {
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
      r.add(m("id", a.get("id"), "salaoId", s.tenantId(), "salao", s.nome(), "data", a.get("date").toString(),
          "inicio", String.valueOf(a.get("start_time")), "fim", String.valueOf(a.get("end_time")), "status", a.get("status"),
          "profissional", a.get("profissional"), "servicos", a.get("servicos"), "total", a.get("total")));
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
