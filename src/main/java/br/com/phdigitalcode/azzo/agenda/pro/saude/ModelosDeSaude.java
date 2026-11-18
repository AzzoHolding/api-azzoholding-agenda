package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelos de anamnese (perguntas com versoes), termos e autorizacoes (texto com versoes e hash) e o que cada servico
 * exige (plano, 3.1, 3.5 e 3.6). So o dono (ou quem tem documentos:gerenciar) muda; todos leem.
 */
@Service
public class ModelosDeSaude {

  static final Set<String> TIPOS_DE_PERGUNTA = Set.of("SIM_NAO", "TEXTO", "TEXTO_LONGO", "ESCOLHA", "MULTIPLA", "NUMERO", "DATA");
  static final Set<String> TIPOS_DE_DOCUMENTO = Set.of("TERMO_PROCEDIMENTO", "AUTORIZACAO_FOTOS", "AUTORIZACAO_MARKETING");

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;

  public ModelosDeSaude(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso) {
    this.jdbc = jdbc;
    this.acesso = acesso;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** Confere e normaliza as perguntas de uma versao nova do modelo. Devolve a lista limpa. */
  static List<Map<String, Object>> validarPerguntas(List<Map<String, Object>> perguntas) {
    if (perguntas == null || perguntas.isEmpty()) throw new IllegalArgumentException("O modelo precisa de ao menos uma pergunta.");
    if (perguntas.size() > 80) throw new IllegalArgumentException("O modelo aceita até 80 perguntas.");
    List<Map<String, Object>> limpas = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (Map<String, Object> p : perguntas) {
      String id = Saude.limpar(Saude.texto(p.get("id")), 40);
      String texto = Saude.limpar(Saude.texto(p.get("texto")), 300);
      String tipo = Saude.texto(p.get("tipo"));
      if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Pergunta com identificador inválido.");
      if (!ids.add(id)) throw new IllegalArgumentException("Duas perguntas com o mesmo identificador: " + id + ".");
      if (texto == null) throw new IllegalArgumentException("Pergunta sem texto.");
      if (tipo == null || !TIPOS_DE_PERGUNTA.contains(tipo)) throw new IllegalArgumentException("Tipo de pergunta inválido: " + tipo + ".");
      Map<String, Object> l = new LinkedHashMap<>();
      l.put("id", id);
      l.put("texto", texto);
      l.put("tipo", tipo);
      l.put("obrigatoria", Boolean.TRUE.equals(p.get("obrigatoria")));
      String ajuda = Saude.limpar(Saude.texto(p.get("ajuda")), 300);
      if (ajuda != null) l.put("ajuda", ajuda);
      if (tipo.equals("ESCOLHA") || tipo.equals("MULTIPLA")) {
        Object o = p.get("opcoes");
        List<String> opcoes = new ArrayList<>();
        if (o instanceof List<?> lo) for (Object x : lo) if (Saude.limpar(Saude.texto(x), 100) != null) opcoes.add(Saude.limpar(Saude.texto(x), 100));
        if (opcoes.size() < 2) throw new IllegalArgumentException("A pergunta \"" + texto + "\" precisa de ao menos duas opções.");
        l.put("opcoes", opcoes);
      }
      if (p.get("condicao") instanceof Map<?, ?> c) {
        String alvo = Saude.texto(c.get("pergunta"));
        if (alvo == null || !ids.contains(alvo) || alvo.equals(id)) {
          throw new IllegalArgumentException("A condição da pergunta \"" + texto + "\" precisa apontar para uma pergunta anterior.");
        }
        l.put("condicao", m("pergunta", alvo, "valor", Saude.texto(c.get("valor"))));
      }
      if (p.get("alerta") instanceof Map<?, ?> a) {
        String tipoAlerta = Saude.texto(a.get("tipo"));
        if (!Saude.TIPOS_DE_ALERTA.contains(tipoAlerta)) throw new IllegalArgumentException("Tipo de alerta inválido.");
        String quando = Saude.texto(a.get("quando"));
        l.put("alerta", m("tipo", tipoAlerta, "quando", quando == null ? "PREENCHIDO" : quando,
            "rotulo", Saude.limpar(Saude.texto(a.get("rotulo")), 60)));
      }
      limpas.add(l);
    }
    return limpas;
  }

  // ─── Modelos de anamnese ───────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listarModelos() {
    UUID t = acesso.tenant();
    return jdbc.queryForList("""
        select m.id, m.nome, m.segmento, m.validade_meses, m.ativo, m.versao_atual_id, v.numero as versao,
               jsonb_array_length(v.perguntas) as perguntas
          from modelos_de_anamnese m left join versoes_de_modelo_de_anamnese v on v.id = m.versao_atual_id
         where m.tenant_id = :t order by m.ativo desc, m.nome""", Map.of("t", t));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> modelo(UUID id) {
    UUID t = acesso.tenant();
    List<Map<String, Object>> r = jdbc.queryForList("""
        select m.id, m.nome, m.segmento, m.validade_meses, m.ativo, m.versao_atual_id, v.numero as versao, v.perguntas
          from modelos_de_anamnese m left join versoes_de_modelo_de_anamnese v on v.id = m.versao_atual_id
         where m.tenant_id = :t and m.id = :id""", Map.of("t", t, "id", id));
    if (r.isEmpty()) throw new IllegalArgumentException("Modelo de anamnese não encontrado.");
    Map<String, Object> modelo = new LinkedHashMap<>(r.get(0));
    modelo.put("perguntas", Saude.lista(modelo.get("perguntas")));
    return modelo;
  }

  /** Perguntas de uma versao do modelo (para mostrar respostas antigas como foram perguntadas). */
  List<Map<String, Object>> perguntasDaVersao(UUID tenantId, UUID versaoId) {
    return jdbc.query("select perguntas from versoes_de_modelo_de_anamnese where tenant_id = :t and id = :id",
        Map.of("t", tenantId, "id", versaoId), rs -> rs.next() ? Saude.lista(rs.getString(1)) : List.of());
  }

  @Transactional
  public Map<String, Object> criarModelo(String nome, String segmento, Integer validadeMeses, List<Map<String, Object>> perguntas) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    String n = Saude.limpar(nome, 120);
    if (n == null) throw new IllegalArgumentException("Informe o nome do modelo.");
    UUID id = UUID.randomUUID();
    jdbc.update("insert into modelos_de_anamnese (id, tenant_id, nome, segmento, validade_meses, created_by)"
        + " values (:id, :t, :n, :s, :v, :u)", m("id", id, "t", t, "n", n, "s", Saude.limpar(segmento, 30),
        "v", validade(validadeMeses), "u", acesso.usuarioId()));
    novaVersao(t, id, perguntas);
    acesso.auditar(t, "HEALTH_TEMPLATE_CREATE", null, m("modelo", id), false);
    return modelo(id);
  }

  @Transactional
  public Map<String, Object> salvarPerguntas(UUID id, List<Map<String, Object>> perguntas) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    modelo(id);
    UUID v = novaVersao(t, id, perguntas);
    acesso.auditar(t, "HEALTH_TEMPLATE_VERSION", null, m("modelo", id, "versao", v), false);
    return modelo(id);
  }

  @Transactional
  public Map<String, Object> editarModelo(UUID id, String nome, Integer validadeMeses, Boolean ativo) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    modelo(id);
    String n = Saude.limpar(nome, 120);
    if (n == null) throw new IllegalArgumentException("Informe o nome do modelo.");
    jdbc.update("update modelos_de_anamnese set nome = :n, validade_meses = :v, ativo = :a, updated_at = now()"
        + " where tenant_id = :t and id = :id", m("n", n, "v", validade(validadeMeses), "a", ativo == null || ativo, "t", t, "id", id));
    return modelo(id);
  }

  private static Integer validade(Integer meses) {
    if (meses == null || meses == 0) return null;
    if (meses < 1 || meses > 120) throw new IllegalArgumentException("A validade vai de 1 a 120 meses.");
    return meses;
  }

  private UUID novaVersao(UUID t, UUID modeloId, List<Map<String, Object>> perguntas) {
    List<Map<String, Object>> limpas = validarPerguntas(perguntas);
    Integer numero = jdbc.queryForObject("select coalesce(max(numero), 0) + 1 from versoes_de_modelo_de_anamnese where modelo_id = :m",
        Map.of("m", modeloId), Integer.class);
    UUID v = UUID.randomUUID();
    jdbc.update("insert into versoes_de_modelo_de_anamnese (id, tenant_id, modelo_id, numero, perguntas, created_by)"
        + " values (:id, :t, :m, :n, cast(:p as jsonb), :u)", m("id", v, "t", t, "m", modeloId, "n", numero,
        "p", Saude.json(limpas), "u", acesso.usuarioId()));
    jdbc.update("update modelos_de_anamnese set versao_atual_id = :v, updated_at = now() where id = :m", Map.of("v", v, "m", modeloId));
    return v;
  }

  // ─── Termos e autorizacoes ─────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listarDocumentos() {
    UUID t = acesso.tenant();
    List<Map<String, Object>> r = jdbc.queryForList("""
        select d.id, d.tipo, d.nome, d.ativo, d.versao_atual_id, v.numero as versao, v.conteudo, v.hash_conteudo
          from modelos_de_documento d left join versoes_de_documento v on v.id = d.versao_atual_id
         where d.tenant_id = :t order by d.ativo desc, d.tipo, d.nome""", Map.of("t", t));
    return r;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> documento(UUID id) {
    UUID t = acesso.tenant();
    List<Map<String, Object>> r = jdbc.queryForList("""
        select d.id, d.tipo, d.nome, d.ativo, d.versao_atual_id, v.numero as versao, v.conteudo, v.hash_conteudo
          from modelos_de_documento d left join versoes_de_documento v on v.id = d.versao_atual_id
         where d.tenant_id = :t and d.id = :id""", Map.of("t", t, "id", id));
    if (r.isEmpty()) throw new IllegalArgumentException("Termo não encontrado.");
    return r.get(0);
  }

  /** O termo pelo id, num salao dado (o link publico nao tem usuario logado). */
  Map<String, Object> documentoDoSalao(UUID t, UUID id) {
    List<Map<String, Object>> r = jdbc.queryForList("""
        select d.id, d.tipo, d.nome, d.ativo, d.versao_atual_id, v.numero as versao, v.conteudo, v.hash_conteudo
          from modelos_de_documento d left join versoes_de_documento v on v.id = d.versao_atual_id
         where d.tenant_id = :t and d.id = :id""", Map.of("t", t, "id", id));
    return r.isEmpty() ? null : r.get(0);
  }

  /** O termo ativo de um tipo (a autorizacao de fotos e a de marketing sao unicas por salao). */
  Map<String, Object> documentoAtivoDoTipo(UUID t, String tipo) {
    List<Map<String, Object>> r = jdbc.queryForList("""
        select d.id, d.tipo, d.nome, d.versao_atual_id, v.numero as versao, v.conteudo, v.hash_conteudo
          from modelos_de_documento d join versoes_de_documento v on v.id = d.versao_atual_id
         where d.tenant_id = :t and d.tipo = :tipo and d.ativo order by d.created_at limit 1""", Map.of("t", t, "tipo", tipo));
    return r.isEmpty() ? null : r.get(0);
  }

  @Transactional
  public Map<String, Object> criarDocumento(String tipo, String nome, String conteudo) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    if (!TIPOS_DE_DOCUMENTO.contains(tipo)) throw new IllegalArgumentException("Tipo de termo inválido.");
    String n = Saude.limpar(nome, 120);
    if (n == null) throw new IllegalArgumentException("Informe o nome do termo.");
    UUID id = UUID.randomUUID();
    jdbc.update("insert into modelos_de_documento (id, tenant_id, tipo, nome, created_by) values (:id, :t, :tipo, :n, :u)",
        m("id", id, "t", t, "tipo", tipo, "n", n, "u", acesso.usuarioId()));
    novaVersaoDoDocumento(t, id, conteudo);
    acesso.auditar(t, "HEALTH_DOCUMENT_CREATE", null, m("documento", id, "tipo", tipo), false);
    return documento(id);
  }

  @Transactional
  public Map<String, Object> salvarTexto(UUID id, String conteudo) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    documento(id);
    UUID v = novaVersaoDoDocumento(t, id, conteudo);
    acesso.auditar(t, "HEALTH_DOCUMENT_VERSION", null, m("documento", id, "versao", v), false);
    return documento(id);
  }

  @Transactional
  public Map<String, Object> editarDocumento(UUID id, String nome, Boolean ativo) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    documento(id);
    String n = Saude.limpar(nome, 120);
    if (n == null) throw new IllegalArgumentException("Informe o nome do termo.");
    jdbc.update("update modelos_de_documento set nome = :n, ativo = :a, updated_at = now() where tenant_id = :t and id = :id",
        m("n", n, "a", ativo == null || ativo, "t", t, "id", id));
    return documento(id);
  }

  private UUID novaVersaoDoDocumento(UUID t, UUID modeloId, String conteudo) {
    String c = conteudo == null ? "" : conteudo.trim();
    if (c.length() < 20) throw new IllegalArgumentException("O texto do termo é curto demais.");
    if (c.length() > 20000) throw new IllegalArgumentException("O texto do termo aceita até 20.000 caracteres.");
    Integer numero = jdbc.queryForObject("select coalesce(max(numero), 0) + 1 from versoes_de_documento where modelo_id = :m",
        Map.of("m", modeloId), Integer.class);
    UUID v = UUID.randomUUID();
    jdbc.update("insert into versoes_de_documento (id, tenant_id, modelo_id, numero, conteudo, hash_conteudo, created_by)"
        + " values (:id, :t, :m, :n, :c, :h, :u)", m("id", v, "t", t, "m", modeloId, "n", numero, "c", c,
        "h", Saude.sha256(c), "u", acesso.usuarioId()));
    jdbc.update("update modelos_de_documento set versao_atual_id = :v, updated_at = now() where id = :m", Map.of("v", v, "m", modeloId));
    return v;
  }

  // ─── Exigencias por servico ────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exigencias() {
    UUID t = acesso.tenant();
    return jdbc.queryForList("""
        select s.id as service_id, s.name as servico, s.category_id, s.is_active,
               e.modelo_anamnese_id, e.termo_id, coalesce(e.exige_autorizacao_fotos, false) as exige_autorizacao_fotos,
               coalesce(e.ficha_assinada, false) as ficha_assinada, coalesce(e.nivel, 'ALERTAR') as nivel,
               e.id is not null as configurado
          from services s left join exigencias_do_servico e on e.tenant_id = s.tenant_id and e.service_id = s.id
         where s.tenant_id = :t order by s.is_active desc, s.name""", Map.of("t", t));
  }

  @Transactional
  public List<Map<String, Object>> salvarExigencia(UUID serviceId, UUID modeloAnamneseId, UUID termoId,
      boolean exigeFotos, boolean fichaAssinada, String nivel) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    Boolean servico = jdbc.queryForObject("select exists(select 1 from services where tenant_id = :t and id = :s)",
        Map.of("t", t, "s", serviceId), Boolean.class);
    if (!Boolean.TRUE.equals(servico)) throw new IllegalArgumentException("Serviço não encontrado.");
    if (modeloAnamneseId != null) modelo(modeloAnamneseId);
    if (termoId != null && !"TERMO_PROCEDIMENTO".equals(documento(termoId).get("tipo"))) {
      throw new IllegalArgumentException("O termo do serviço precisa ser um termo de procedimento.");
    }
    String n = nivel == null ? "ALERTAR" : nivel;
    if (!n.equals("ALERTAR") && !n.equals("BLOQUEAR")) throw new IllegalArgumentException("Nível inválido.");
    boolean vazio = modeloAnamneseId == null && termoId == null && !exigeFotos && !fichaAssinada;
    if (vazio) {
      jdbc.update("delete from exigencias_do_servico where tenant_id = :t and service_id = :s", Map.of("t", t, "s", serviceId));
    } else {
      jdbc.update("""
          insert into exigencias_do_servico (tenant_id, service_id, modelo_anamnese_id, termo_id, exige_autorizacao_fotos,
                                             ficha_assinada, nivel)
          values (:t, :s, :ma, :te, :f, :fa, :n)
          on conflict (tenant_id, service_id) where service_id is not null do update
             set modelo_anamnese_id = excluded.modelo_anamnese_id, termo_id = excluded.termo_id,
                 exige_autorizacao_fotos = excluded.exige_autorizacao_fotos, ficha_assinada = excluded.ficha_assinada,
                 nivel = excluded.nivel, updated_at = now()""",
          m("t", t, "s", serviceId, "ma", modeloAnamneseId, "te", termoId, "f", exigeFotos, "fa", fichaAssinada, "n", n));
    }
    acesso.auditar(t, "HEALTH_REQUIREMENT_UPDATE", null, m("servico", serviceId, "nivel", n), false);
    return exigencias();
  }

  /** As exigencias que valem para estes servicos (a do servico; na falta, a da categoria). */
  List<Map<String, Object>> exigenciasDosServicos(UUID t, List<UUID> servicos) {
    if (servicos == null || servicos.isEmpty()) return List.of();
    return jdbc.queryForList("""
        select s.id as service_id, s.name as servico, coalesce(e.modelo_anamnese_id, ec.modelo_anamnese_id) as modelo_anamnese_id,
               coalesce(e.termo_id, ec.termo_id) as termo_id,
               coalesce(e.exige_autorizacao_fotos, ec.exige_autorizacao_fotos, false) as exige_autorizacao_fotos,
               coalesce(e.ficha_assinada, ec.ficha_assinada, false) as ficha_assinada,
               coalesce(e.nivel, ec.nivel, 'ALERTAR') as nivel
          from services s
          left join exigencias_do_servico e on e.tenant_id = s.tenant_id and e.service_id = s.id
          left join exigencias_do_servico ec on ec.tenant_id = s.tenant_id and ec.category_id = s.category_id
         where s.tenant_id = :t and s.id in (:ids) and (e.id is not null or ec.id is not null)""",
        Map.of("t", t, "ids", servicos));
  }

  // ─── Modelos prontos por segmento ──────────────────────────────────────────

  /** Cria os modelos prontos que o salao ainda nao tem (pelo nome). Devolve quantos criou. */
  @Transactional
  public Map<String, Object> criarPadroes(List<String> segmentos) {
    acesso.exigirGerenciarDocumentos();
    UUID t = acesso.tenant();
    Set<String> existentes = new HashSet<>(jdbc.queryForList("select nome from modelos_de_anamnese where tenant_id = :t",
        Map.of("t", t), String.class));
    int criados = 0;
    for (ModelosProntos.Modelo pronto : ModelosProntos.modelos()) {
      boolean pedido = segmentos == null || segmentos.isEmpty() || segmentos.contains(pronto.segmento()) || "GERAL".equals(pronto.segmento());
      if (!pedido || existentes.contains(pronto.nome())) continue;
      criarModelo(pronto.nome(), pronto.segmento(), pronto.validadeMeses(), pronto.perguntas());
      criados++;
    }
    Set<String> tipos = new HashSet<>(jdbc.queryForList("select tipo from modelos_de_documento where tenant_id = :t",
        Map.of("t", t), String.class));
    for (ModelosProntos.Termo termo : ModelosProntos.termos()) {
      if (tipos.contains(termo.tipo())) continue;
      criarDocumento(termo.tipo(), termo.nome(), termo.texto());
      criados++;
    }
    return m("criados", criados);
  }
}
