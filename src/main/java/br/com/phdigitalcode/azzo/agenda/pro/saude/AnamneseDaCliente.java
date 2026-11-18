package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anamnese da cliente com versoes (plano, 3.2), alertas calculados das respostas, alertas manuais, resumo de saude e
 * pendencias do agendamento (3.6). Editar = criar versao nova; a anterior fica.
 */
@Service
public class AnamneseDaCliente {

  static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final ModelosDeSaude modelos;

  public AnamneseDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, ModelosDeSaude modelos) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.modelos = modelos;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** A pergunta aparece? Sem condicao, sempre; com condicao, so se a resposta da outra bate. */
  static boolean visivel(Map<String, Object> pergunta, Map<String, Object> respostas) {
    if (!(pergunta.get("condicao") instanceof Map<?, ?> c)) return true;
    Object outra = respostas.get(String.valueOf(c.get("pergunta")));
    String valor = Saude.texto(c.get("valor"));
    if (valor == null) return preenchida(outra);
    if (outra instanceof Collection<?> lista) return lista.stream().anyMatch(x -> valor.equalsIgnoreCase(String.valueOf(x)));
    return outra != null && valor.equalsIgnoreCase(outra.toString().trim());
  }

  static boolean preenchida(Object resposta) {
    if (resposta == null) return false;
    if (resposta instanceof Collection<?> c) return !c.isEmpty();
    return !resposta.toString().isBlank();
  }

  /** So as respostas de perguntas do modelo e visiveis, no formato do tipo. */
  static Map<String, Object> respostasLimpas(List<Map<String, Object>> perguntas, Map<String, Object> respostas) {
    Map<String, Object> entrada = respostas == null ? Map.of() : respostas;
    Map<String, Object> limpas = new LinkedHashMap<>();
    for (Map<String, Object> p : perguntas) {
      String id = String.valueOf(p.get("id"));
      if (!visivel(p, limpas) || !preenchida(entrada.get(id))) continue;
      Object r = entrada.get(id);
      String tipo = String.valueOf(p.get("tipo"));
      switch (tipo) {
        case "SIM_NAO" -> {
          String v = r.toString().trim().toUpperCase(java.util.Locale.ROOT);
          if (!v.equals("SIM") && !v.equals("NAO")) throw new IllegalArgumentException("Responda sim ou não em \"" + p.get("texto") + "\".");
          limpas.put(id, v);
        }
        case "MULTIPLA" -> {
          List<String> opcoes = opcoes(p);
          List<String> escolhidas = new ArrayList<>();
          if (r instanceof Collection<?> c) for (Object x : c) if (opcoes.contains(String.valueOf(x))) escolhidas.add(String.valueOf(x));
          if (!escolhidas.isEmpty()) limpas.put(id, escolhidas);
        }
        case "ESCOLHA" -> {
          if (!opcoes(p).contains(r.toString())) throw new IllegalArgumentException("Opção inválida em \"" + p.get("texto") + "\".");
          limpas.put(id, r.toString());
        }
        case "NUMERO" -> {
          try {
            limpas.put(id, new java.math.BigDecimal(r.toString().trim().replace(",", ".")));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Número inválido em \"" + p.get("texto") + "\".");
          }
        }
        case "DATA" -> {
          try {
            limpas.put(id, LocalDate.parse(r.toString().trim()).toString());
          } catch (Exception e) {
            throw new IllegalArgumentException("Data inválida em \"" + p.get("texto") + "\".");
          }
        }
        default -> limpas.put(id, Saude.limpar(r.toString(), "TEXTO".equals(tipo) ? 300 : 3000));
      }
    }
    return limpas;
  }

  @SuppressWarnings("unchecked")
  private static List<String> opcoes(Map<String, Object> p) {
    return p.get("opcoes") instanceof List<?> l ? (List<String>) l : List.of();
  }

  /** Os textos das perguntas obrigatorias e visiveis sem resposta. */
  static List<String> faltando(List<Map<String, Object>> perguntas, Map<String, Object> respostas) {
    List<String> f = new ArrayList<>();
    for (Map<String, Object> p : perguntas) {
      if (Boolean.TRUE.equals(p.get("obrigatoria")) && visivel(p, respostas) && !preenchida(respostas.get(String.valueOf(p.get("id"))))) {
        f.add(String.valueOf(p.get("texto")));
      }
    }
    return f;
  }

  /** Alertas que saem das respostas: {tipo, texto}. "Alergia: dipirona", "Gestante ou amamentando". */
  static List<Map<String, Object>> alertasDasRespostas(List<Map<String, Object>> perguntas, Map<String, Object> respostas) {
    List<Map<String, Object>> alertas = new ArrayList<>();
    for (Map<String, Object> p : perguntas) {
      if (!(p.get("alerta") instanceof Map<?, ?> a) || !visivel(p, respostas)) continue;
      Object r = respostas.get(String.valueOf(p.get("id")));
      if (!preenchida(r)) continue;
      String quando = Saude.texto(a.get("quando"));
      String rotulo = Saude.texto(a.get("rotulo"));
      boolean dispara;
      if (quando == null || quando.equals("PREENCHIDO")) dispara = !"NAO".equalsIgnoreCase(r.toString());
      else if (r instanceof Collection<?> c) dispara = c.stream().anyMatch(x -> quando.equalsIgnoreCase(String.valueOf(x)));
      else dispara = quando.equalsIgnoreCase(r.toString().trim());
      if (!dispara) continue;
      String valor = r instanceof Collection<?> c ? String.join(", ", c.stream().map(String::valueOf).toList()) : r.toString();
      String texto;
      if ("SIM_NAO".equals(p.get("tipo"))) texto = rotulo != null ? rotulo : String.valueOf(p.get("texto"));
      else texto = (rotulo != null ? rotulo : String.valueOf(p.get("texto"))) + ": " + valor;
      alertas.add(m("tipo", String.valueOf(a.get("tipo")), "texto", texto, "origem", "ANAMNESE"));
    }
    return alertas;
  }

  static boolean vencida(Object criadaEm, Integer validadeMeses, LocalDate hoje) {
    if (validadeMeses == null || criadaEm == null) return false;
    LocalDate criada = java.time.Instant.parse(Saude.instante(criadaEm)).atZone(ZONA_BR).toLocalDate();
    return criada.plusMonths(validadeMeses).isBefore(hoje);
  }

  static boolean menor(LocalDate nascimento, LocalDate hoje) {
    return nascimento != null && Period.between(nascimento, hoje).getYears() < 18;
  }

  // ─── Leitura ───────────────────────────────────────────────────────────────

  void exigirCliente(UUID t, UUID clientId) {
    Boolean existe = jdbc.queryForObject("select exists(select 1 from clients where tenant_id = :t and id = :c)",
        Map.of("t", t, "c", clientId), Boolean.class);
    if (!Boolean.TRUE.equals(existe)) throw new IllegalArgumentException("Cliente não encontrada.");
  }

  /** Versao atual da anamnese de um modelo, com as perguntas da versao usada. Nulo se nunca preenchida. */
  Map<String, Object> versaoAtual(UUID t, UUID clientId, UUID modeloId) {
    List<Map<String, Object>> r = jdbc.queryForList("""
        select v.id, v.numero, v.respostas, v.completa, v.faltando, v.canal, v.created_at, v.versao_do_modelo_id,
               v.preenchida_por, a.modelo_id
          from anamneses a join versoes_de_anamnese v on v.id = a.versao_atual_id
         where a.tenant_id = :t and a.client_id = :c and a.modelo_id = :m""", Map.of("t", t, "c", clientId, "m", modeloId));
    if (r.isEmpty()) return null;
    Map<String, Object> v = new LinkedHashMap<>(r.get(0));
    v.put("respostas", Saude.mapa(v.get("respostas")));
    v.put("faltando", Saude.textos(v.get("faltando")));
    v.put("perguntas", modelos.perguntasDaVersao(t, (UUID) v.get("versao_do_modelo_id")));
    v.put("confirmada", confirmada(t, (UUID) v.get("id")));
    return v;
  }

  boolean confirmada(UUID t, UUID versaoAnamneseId) {
    Boolean c = jdbc.queryForObject("select exists(select 1 from assinaturas_do_cliente where tenant_id = :t"
        + " and versao_anamnese_id = :v and evento = 'ASSINATURA')", Map.of("t", t, "v", versaoAnamneseId), Boolean.class);
    return Boolean.TRUE.equals(c);
  }

  /** Alertas calculados (de todas as anamneses atuais) e manuais ativos. */
  List<Map<String, Object>> alertas(UUID t, UUID clientId) {
    List<Map<String, Object>> todos = new ArrayList<>();
    for (Map<String, Object> a : jdbc.queryForList("""
        select v.respostas, mv.perguntas from anamneses a
          join versoes_de_anamnese v on v.id = a.versao_atual_id
          join versoes_de_modelo_de_anamnese mv on mv.id = v.versao_do_modelo_id
         where a.tenant_id = :t and a.client_id = :c""", Map.of("t", t, "c", clientId))) {
      todos.addAll(alertasDasRespostas(Saude.lista(a.get("perguntas")), Saude.mapa(a.get("respostas"))));
    }
    for (Map<String, Object> a : jdbc.queryForList("select id, tipo, texto, origem, created_at from alertas_do_cliente"
        + " where tenant_id = :t and client_id = :c and ativo order by created_at", Map.of("t", t, "c", clientId))) {
      todos.add(m("id", a.get("id"), "tipo", a.get("tipo"), "texto", a.get("texto"), "origem", a.get("origem"),
          "criadoEm", Saude.instante(a.get("created_at"))));
    }
    // Mesmo alerta vindo de dois modelos aparece uma vez.
    Set<String> vistos = new LinkedHashSet<>();
    List<Map<String, Object>> unicos = new ArrayList<>();
    for (Map<String, Object> a : todos) if (vistos.add(a.get("tipo") + "|" + a.get("texto"))) unicos.add(a);
    return unicos;
  }

  /** Situacao das autorizacoes de fotos e marketing: ATIVA, RECUSADA, REVOGADA ou NENHUMA. */
  Map<String, Object> autorizacoes(UUID t, UUID clientId) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (String tipo : List.of("AUTORIZACAO_FOTOS", "AUTORIZACAO_MARKETING")) {
      String ultimo = jdbc.query("select evento from assinaturas_do_cliente where tenant_id = :t and client_id = :c"
          + " and tipo_documento = :tipo order by created_at desc limit 1", Map.of("t", t, "c", clientId, "tipo", tipo),
          rs -> rs.next() ? rs.getString(1) : null);
      String situacao = ultimo == null ? "NENHUMA" : switch (ultimo) {
        case "ASSINATURA" -> "ATIVA";
        case "RECUSA" -> "RECUSADA";
        default -> "REVOGADA";
      };
      r.put(tipo.equals("AUTORIZACAO_FOTOS") ? "fotos" : "marketing", situacao);
    }
    return r;
  }

  boolean autorizacaoAtiva(UUID t, UUID clientId, String tipo) {
    return "ATIVA".equals(autorizacoes(t, clientId).get(tipo.equals("AUTORIZACAO_FOTOS") ? "fotos" : "marketing"));
  }

  /** O termo (versao atual) ja foi assinado por esta cliente, sem recusa nem revogacao depois. */
  boolean termoAssinado(UUID t, UUID clientId, UUID termoId) {
    String ultimo = jdbc.query("""
        select a.evento from assinaturas_do_cliente a join modelos_de_documento d on d.id = a.modelo_documento_id
         where a.tenant_id = :t and a.client_id = :c and a.modelo_documento_id = :d
           and (a.evento <> 'ASSINATURA' or a.versao_documento_id = d.versao_atual_id)
         order by a.created_at desc limit 1""", Map.of("t", t, "c", clientId, "d", termoId),
        rs -> rs.next() ? rs.getString(1) : null);
    return "ASSINATURA".equals(ultimo);
  }

  boolean termoRecusado(UUID t, UUID clientId, UUID termoId) {
    String ultimo = jdbc.query("select evento from assinaturas_do_cliente where tenant_id = :t and client_id = :c"
        + " and modelo_documento_id = :d order by created_at desc limit 1", Map.of("t", t, "c", clientId, "d", termoId),
        rs -> rs.next() ? rs.getString(1) : null);
    return "RECUSA".equals(ultimo);
  }

  /** Resumo de saude da cliente para a ficha, a agenda e o painel. Recepcao recebe so as pendencias. */
  @Transactional(readOnly = true)
  public Map<String, Object> resumo(UUID clientId) {
    UUID t = acesso.tenant();
    exigirCliente(t, clientId);
    boolean ver = acesso.podeVerSaude(t, clientId);
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("podeVerSaude", ver);
    r.put("podeEditarSaude", acesso.podeEditarSaude(t, clientId));
    r.put("podeVerFotos", acesso.tem(AcessoDeSaude.FOTOS_VER));
    r.put("alertas", acesso.podeVerAlertas(t, clientId) ? alertas(t, clientId) : List.of());
    LocalDate nascimento = jdbc.query("select birth_date from clients where tenant_id = :t and id = :c",
        Map.of("t", t, "c", clientId), rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);
    LocalDate hoje = LocalDate.now(ZONA_BR);
    r.put("menor", menor(nascimento, hoje));
    List<Map<String, Object>> anamneses = new ArrayList<>();
    for (Map<String, Object> mod : jdbc.queryForList("""
        select m.id, m.nome, m.validade_meses, a.id as anamnese_id, v.id as versao_id, v.numero, v.completa, v.faltando,
               v.created_at, v.canal
          from modelos_de_anamnese m
          left join anamneses a on a.modelo_id = m.id and a.client_id = :c and a.tenant_id = :t
          left join versoes_de_anamnese v on v.id = a.versao_atual_id
         where m.tenant_id = :t and (m.ativo or a.id is not null) order by m.nome""", Map.of("t", t, "c", clientId))) {
      Map<String, Object> a = new LinkedHashMap<>();
      a.put("modeloId", mod.get("id"));
      a.put("nome", mod.get("nome"));
      if (mod.get("versao_id") == null) {
        a.put("situacao", "NAO_PREENCHIDA");
      } else {
        boolean conf = confirmada(t, (UUID) mod.get("versao_id"));
        boolean venc = vencida(mod.get("created_at"), (Integer) mod.get("validade_meses"), hoje);
        a.put("versaoId", mod.get("versao_id"));
        a.put("numero", mod.get("numero"));
        a.put("atualizadaEm", Saude.instante(mod.get("created_at")));
        a.put("canal", mod.get("canal"));
        a.put("confirmada", conf);
        a.put("vencida", venc);
        a.put("faltando", ver ? Saude.textos(mod.get("faltando")) : List.of());
        a.put("situacao", !Boolean.TRUE.equals(mod.get("completa")) ? "INCOMPLETA" : venc ? "VENCIDA" : conf ? "CONFIRMADA" : "PENDENTE_DE_CONFIRMACAO");
      }
      anamneses.add(a);
    }
    r.put("anamneses", anamneses);
    r.put("autorizacoes", autorizacoes(t, clientId));
    if (ver) acesso.registrarAcesso(t, "RESUMO", clientId, null);
    return r;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> anamnese(UUID clientId, UUID modeloId) {
    UUID t = acesso.tenant();
    exigirCliente(t, clientId);
    acesso.exigirSaudeVer(t, clientId);
    Map<String, Object> modelo = modelos.modelo(modeloId);
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("modelo", modelo);
    r.put("atual", versaoAtual(t, clientId, modeloId));
    r.put("versoes", jdbc.queryForList("""
        select v.id, v.numero, v.completa, v.canal, v.created_at, u.name as preenchida_por_nome,
               exists(select 1 from assinaturas_do_cliente s where s.versao_anamnese_id = v.id and s.evento = 'ASSINATURA') as confirmada
          from anamneses a join versoes_de_anamnese v on v.anamnese_id = a.id
          left join users u on u.id = v.preenchida_por
         where a.tenant_id = :t and a.client_id = :c and a.modelo_id = :m order by v.numero desc""",
        Map.of("t", t, "c", clientId, "m", modeloId)));
    acesso.registrarAcesso(t, "ANAMNESE", clientId, modeloId);
    return r;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> versao(UUID clientId, UUID versaoId) {
    UUID t = acesso.tenant();
    acesso.exigirSaudeVer(t, clientId);
    List<Map<String, Object>> r = jdbc.queryForList("select id, numero, respostas, completa, faltando, canal, created_at,"
        + " versao_do_modelo_id from versoes_de_anamnese where tenant_id = :t and client_id = :c and id = :v",
        Map.of("t", t, "c", clientId, "v", versaoId));
    if (r.isEmpty()) throw new IllegalArgumentException("Versão não encontrada.");
    Map<String, Object> v = new LinkedHashMap<>(r.get(0));
    v.put("respostas", Saude.mapa(v.get("respostas")));
    v.put("faltando", Saude.textos(v.get("faltando")));
    v.put("perguntas", modelos.perguntasDaVersao(t, (UUID) v.get("versao_do_modelo_id")));
    v.put("confirmada", confirmada(t, versaoId));
    acesso.registrarAcesso(t, "ANAMNESE_VERSAO", clientId, versaoId);
    return v;
  }

  // ─── Escrita ───────────────────────────────────────────────────────────────

  @Transactional
  public Map<String, Object> salvar(UUID clientId, UUID modeloId, Map<String, Object> respostas) {
    UUID t = acesso.tenant();
    exigirCliente(t, clientId);
    acesso.exigirSaudeEditar(t, clientId);
    UUID versao = salvarVersao(t, clientId, modeloId, respostas, "SALAO", acesso.usuarioId(), null);
    return Saude.m("versaoId", versao, "anamnese", anamnese(clientId, modeloId));
  }

  /** Cria a versao nova (salao ou link). Respostas iguais a versao atual nao criam versao. */
  UUID salvarVersao(UUID t, UUID clientId, UUID modeloId, Map<String, Object> respostas, String canal, UUID usuario, UUID linkId) {
    List<Map<String, Object>> mod = jdbc.queryForList("select versao_atual_id, ativo from modelos_de_anamnese where tenant_id = :t and id = :m",
        Map.of("t", t, "m", modeloId));
    if (mod.isEmpty() || mod.get(0).get("versao_atual_id") == null) throw new IllegalArgumentException("Modelo de anamnese não encontrado.");
    UUID versaoDoModelo = (UUID) mod.get(0).get("versao_atual_id");
    List<Map<String, Object>> perguntas = modelos.perguntasDaVersao(t, versaoDoModelo);
    Map<String, Object> limpas = respostasLimpas(perguntas, respostas);
    List<String> falta = faltando(perguntas, limpas);
    UUID anamneseId = jdbc.query("select id from anamneses where tenant_id = :t and client_id = :c and modelo_id = :m",
        Map.of("t", t, "c", clientId, "m", modeloId), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    if (anamneseId == null) {
      anamneseId = UUID.randomUUID();
      jdbc.update("insert into anamneses (id, tenant_id, client_id, modelo_id) values (:id, :t, :c, :m)",
          Map.of("id", anamneseId, "t", t, "c", clientId, "m", modeloId));
    } else {
      Map<String, Object> atual = versaoAtual(t, clientId, modeloId);
      if (atual != null && versaoDoModelo.equals(atual.get("versao_do_modelo_id"))
          && atual.get("respostas").equals(Saude.mapa(Saude.json(limpas)))) {
        return (UUID) atual.get("id");
      }
    }
    Integer numero = jdbc.queryForObject("select coalesce(max(numero), 0) + 1 from versoes_de_anamnese where anamnese_id = :a",
        Map.of("a", anamneseId), Integer.class);
    UUID v = UUID.randomUUID();
    jdbc.update("""
        insert into versoes_de_anamnese (id, tenant_id, anamnese_id, client_id, numero, versao_do_modelo_id, respostas,
                                         completa, faltando, canal, preenchida_por, link_id)
        values (:id, :t, :a, :c, :n, :vm, cast(:r as jsonb), :comp, cast(:f as jsonb), :canal, :u, :l)""",
        m("id", v, "t", t, "a", anamneseId, "c", clientId, "n", numero, "vm", versaoDoModelo, "r", Saude.json(limpas),
            "comp", falta.isEmpty(), "f", Saude.json(falta), "canal", canal, "u", usuario, "l", linkId));
    jdbc.update("update anamneses set versao_atual_id = :v, updated_at = now() where id = :a", Map.of("v", v, "a", anamneseId));
    acesso.auditar(t, "HEALTH_ANAMNESIS_VERSION", clientId, m("modelo", modeloId, "versao", v, "numero", numero,
        "completa", falta.isEmpty(), "canal", canal), false);
    return v;
  }

  @Transactional
  public List<Map<String, Object>> criarAlerta(UUID clientId, String tipo, String texto) {
    UUID t = acesso.tenant();
    exigirCliente(t, clientId);
    if (!acesso.podeEditarSaude(t, clientId) && !(acesso.profissional() && acesso.atende(t, clientId))) {
      acesso.negar(t, "HEALTH_ALERT_DENIED", clientId);
    }
    criarAlertaInterno(t, clientId, tipo, texto, "MANUAL", null);
    return alertas(t, clientId);
  }

  void criarAlertaInterno(UUID t, UUID clientId, String tipo, String texto, String origem, UUID fichaId) {
    if (!Saude.TIPOS_DE_ALERTA.contains(tipo)) throw new IllegalArgumentException("Tipo de alerta inválido.");
    String x = Saude.limpar(texto, 300);
    if (x == null) throw new IllegalArgumentException("Escreva o alerta.");
    UUID id = UUID.randomUUID();
    jdbc.update("insert into alertas_do_cliente (id, tenant_id, client_id, tipo, texto, origem, ficha_id, created_by)"
        + " values (:id, :t, :c, :tipo, :x, :o, :f, :u)", m("id", id, "t", t, "c", clientId, "tipo", tipo, "x", x,
        "o", origem, "f", fichaId, "u", acesso.usuarioId()));
    acesso.auditar(t, "HEALTH_ALERT_CREATE", clientId, m("alerta", id, "tipo", tipo, "origem", origem), false);
  }

  @Transactional
  public List<Map<String, Object>> desativarAlerta(UUID clientId, UUID alertaId) {
    UUID t = acesso.tenant();
    acesso.exigirSaudeEditar(t, clientId);
    int n = jdbc.update("update alertas_do_cliente set ativo = false, desativado_por = :u, desativado_em = now()"
        + " where tenant_id = :t and client_id = :c and id = :a and ativo", m("u", acesso.usuarioId(), "t", t, "c", clientId, "a", alertaId));
    if (n == 0) throw new IllegalArgumentException("Alerta não encontrado.");
    acesso.auditar(t, "HEALTH_ALERT_DISABLE", clientId, m("alerta", alertaId), false);
    return alertas(t, clientId);
  }

  // ─── Pendencias do agendamento ─────────────────────────────────────────────

  /**
   * O que falta para os servicos do agendamento: anamnese (nao preenchida, incompleta, vencida, sem confirmacao),
   * termo do procedimento e autorizacao de fotos. Cada item tem o nivel da exigencia (ALERTAR ou BLOQUEAR).
   */
  @Transactional(readOnly = true)
  public Map<String, Object> pendenciasDoAgendamento(UUID appointmentId) {
    UUID t = acesso.tenant();
    return pendencias(t, appointmentId, true);
  }

  Map<String, Object> pendencias(UUID t, UUID appointmentId, boolean comAlertas) {
    List<Map<String, Object>> ag = jdbc.queryForList("select client_id, professional_id from appointments where tenant_id = :t and id = :a",
        Map.of("t", t, "a", appointmentId));
    if (ag.isEmpty()) throw new IllegalArgumentException("Agendamento não encontrado.");
    UUID clientId = (UUID) ag.get(0).get("client_id");
    List<UUID> servicos = jdbc.queryForList("select service_id from appointment_items where appointment_id = :a and service_id is not null",
        Map.of("a", appointmentId), UUID.class);
    List<Map<String, Object>> itens = new ArrayList<>();
    LocalDate hoje = LocalDate.now(ZONA_BR);
    Set<String> vistos = new LinkedHashSet<>();
    boolean fichaAssinada = false;
    boolean fotos = false;
    for (Map<String, Object> e : modelos.exigenciasDosServicos(t, servicos)) {
      String nivel = String.valueOf(e.get("nivel"));
      String servico = String.valueOf(e.get("servico"));
      if (Boolean.TRUE.equals(e.get("ficha_assinada"))) fichaAssinada = true;
      UUID modelo = (UUID) e.get("modelo_anamnese_id");
      if (modelo != null && vistos.add("A" + modelo)) {
        Map<String, Object> mod = jdbc.queryForMap("select nome, validade_meses from modelos_de_anamnese where id = :m", Map.of("m", modelo));
        Map<String, Object> atual = clientId == null ? null : versaoAtual(t, clientId, modelo);
        String problema = null;
        if (atual == null) problema = "SEM_ANAMNESE";
        else if (!Boolean.TRUE.equals(atual.get("completa"))) problema = "ANAMNESE_INCOMPLETA";
        else if (vencida(atual.get("created_at"), (Integer) mod.get("validade_meses"), hoje)) problema = "ANAMNESE_VENCIDA";
        else if (!Boolean.TRUE.equals(atual.get("confirmada"))) problema = "ANAMNESE_SEM_CONFIRMACAO";
        if (problema != null) {
          itens.add(m("tipo", problema, "nivel", nivel, "servico", servico, "modeloId", modelo, "nome", mod.get("nome"),
              "texto", textoDaPendencia(problema, String.valueOf(mod.get("nome")))));
        }
      }
      UUID termo = (UUID) e.get("termo_id");
      if (termo != null && vistos.add("T" + termo) && (clientId == null || !termoAssinado(t, clientId, termo))) {
        String nome = jdbc.queryForObject("select nome from modelos_de_documento where id = :d", Map.of("d", termo), String.class);
        boolean recusado = clientId != null && termoRecusado(t, clientId, termo);
        itens.add(m("tipo", recusado ? "TERMO_RECUSADO" : "TERMO_PENDENTE", "nivel", nivel, "servico", servico, "documentoId", termo,
            "nome", nome, "texto", (recusado ? "A cliente recusou: " : "Falta assinar: ") + nome));
      }
      if (Boolean.TRUE.equals(e.get("exige_autorizacao_fotos"))) fotos = true;
    }
    if (fotos && (clientId == null || !autorizacaoAtiva(t, clientId, "AUTORIZACAO_FOTOS")) && vistos.add("F")) {
      itens.add(m("tipo", "AUTORIZACAO_FOTOS_PENDENTE", "nivel", "ALERTAR", "texto", "Falta a autorização de fotos"));
    }
    boolean bloqueia = itens.stream().anyMatch(i -> "BLOQUEAR".equals(i.get("nivel")));
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("clientId", clientId);
    r.put("itens", itens);
    r.put("bloqueia", bloqueia);
    r.put("fichaAssinada", fichaAssinada);
    r.put("alertas", comAlertas && clientId != null && acesso.podeVerAlertas(t, clientId) ? alertas(t, clientId) : List.of());
    return r;
  }

  static String textoDaPendencia(String tipo, String nome) {
    return switch (tipo) {
      case "SEM_ANAMNESE" -> "Sem anamnese: " + nome;
      case "ANAMNESE_INCOMPLETA" -> "Anamnese incompleta: " + nome;
      case "ANAMNESE_VENCIDA" -> "Anamnese para revisar: " + nome;
      default -> "Anamnese sem confirmação da cliente: " + nome;
    };
  }
}
