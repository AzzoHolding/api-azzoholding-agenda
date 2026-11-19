package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Convite da cliente para a area dela (PLANO-AREA-DA-CLIENTE.md). O token sai uma vez (so o hash fica no banco), vale
 * 7 dias, serve uma vez so e pede os 4 ultimos digitos do telefone da cliente antes de mostrar qualquer dado. 5 erros
 * bloqueiam o convite.
 *
 * <p>Decisao do dono (19/11/2026, caminho A): o que a cliente faz fora do salao exige conta. O link sem conta da etapa 3
 * da anamnese (preencher e assinar por 72 h) deixou de existir; o que ele oferecia agora fica na area da cliente, com as
 * regras de escopo daqui ({@link #anamnesesOferecidas}, {@link #termosOferecidos}).
 */
@Service
public class LinksDaCliente {

  static final String CONVITE = "CONVITE_DA_CONTA";
  static final Duration VALIDADE = Duration.ofDays(7);
  static final Duration SESSAO = Duration.ofMinutes(30);
  static final int TENTATIVAS = 5;
  private static final SecureRandom SORTEIO = new SecureRandom();

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;
  private final ModelosDeSaude modelos;
  private final EncryptionService cifra;
  private final String baseUrl;

  public LinksDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese,
      ModelosDeSaude modelos, EncryptionService cifra,
      @Value("${app.public.booking.base-url:http://localhost:3000}") String baseUrl) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
    this.modelos = modelos;
    this.cifra = cifra;
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  static String novoToken() {
    byte[] b = new byte[32];
    SORTEIO.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String ultimos4(String telefone) {
    String d = telefone == null ? "" : telefone.replaceAll("\\D", "");
    return d.length() < 4 ? null : d.substring(d.length() - 4);
  }

  static String primeiroNome(String nome) {
    if (nome == null || nome.isBlank()) return "";
    return nome.trim().split("\\s+")[0];
  }

  static String whatsapp(String telefone, String texto) {
    String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
    if (digitos.length() <= 11) digitos = "55" + digitos;
    return "https://wa.me/" + digitos + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
  }

  String urlDaArea() {
    return baseUrl + "/minha-area";
  }

  // ─── Salao: criar, listar, revogar ─────────────────────────────────────────

  /** Novo convite. Quem chama ({@link ContasDaCliente#convidar}) ja decidiu se a cliente precisa de um. */
  Map<String, Object> criarConvite(UUID t, UUID clientId, UUID appointmentId, UUID usuario) {
    if (appointmentId != null) {
      Boolean ok = jdbc.queryForObject("select exists(select 1 from appointments where tenant_id = :t and id = :a and client_id = :c)",
          Map.of("t", t, "a", appointmentId, "c", clientId), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new IllegalArgumentException("Agendamento não encontrado para esta cliente.");
    }
    String telefone = jdbc.query("select phone from clients where tenant_id = :t and id = :c", Map.of("t", t, "c", clientId),
        rs -> rs.next() ? rs.getString(1) : null);
    if (ultimos4(telefone) == null) throw new IllegalArgumentException("A cliente precisa ter telefone para receber o convite.");
    // Um convite valido por vez: o anterior, ainda nao usado, deixa de valer.
    jdbc.update("update links_do_cliente set revogado_em = now() where tenant_id = :t and client_id = :c and proposito = :p"
        + " and usado_em is null and revogado_em is null", Map.of("t", t, "c", clientId, "p", CONVITE));
    String token = novoToken();
    UUID id = UUID.randomUUID();
    Instant expira = Instant.now().plus(VALIDADE);
    jdbc.update("""
        insert into links_do_cliente (id, tenant_id, client_id, appointment_id, proposito, token_hash, expira_em, created_by)
        values (:id, :t, :c, :a, :p, :h, :e, :u)""", m("id", id, "t", t, "c", clientId, "a", appointmentId, "p", CONVITE,
        "h", Saude.sha256(token), "e", java.sql.Timestamp.from(expira), "u", usuario));
    String url = baseUrl + "/minha-area/convite/" + token;
    String nome = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", t), String.class);
    String texto = "Olá! O " + nome + " convidou você para a sua área de cliente: lá ficam seus atendimentos, sua ficha,"
        + " seus exames e seus documentos. Crie seu acesso por este link: " + url + " (vale por 7 dias).";
    acesso.auditar(t, "CLIENT_AREA_INVITE_CREATE", clientId, m("link", id), false);
    return m("id", id, "url", url, "expiraEm", expira.toString(), "mensagem", texto, "whatsappUrl", whatsapp(telefone, texto));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listar(UUID clientId) {
    UUID t = acesso.tenant();
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> l : jdbc.queryForList("""
        select id, proposito, appointment_id, expira_em, conferido_em, revogado_em, bloqueado_em, usado_em, created_at
          from links_do_cliente where tenant_id = :t and client_id = :c order by created_at desc limit 20""", Map.of("t", t, "c", clientId))) {
      Map<String, Object> x = new LinkedHashMap<>(l);
      for (String c : List.of("expira_em", "conferido_em", "revogado_em", "bloqueado_em", "usado_em", "created_at")) x.put(c, Saude.instante(l.get(c)));
      x.put("situacao", l.get("usado_em") != null ? "USADO" : l.get("revogado_em") != null ? "REVOGADO"
          : l.get("bloqueado_em") != null ? "BLOQUEADO"
          : ((java.sql.Timestamp) l.get("expira_em")).toInstant().isBefore(Instant.now()) ? "VENCIDO" : "VALIDO");
      r.add(x);
    }
    return r;
  }

  @Transactional
  public void revogar(UUID clientId, UUID linkId) {
    UUID t = acesso.tenant();
    int n = jdbc.update("update links_do_cliente set revogado_em = now() where tenant_id = :t and client_id = :c and id = :l"
        + " and revogado_em is null and usado_em is null", Map.of("t", t, "c", clientId, "l", linkId));
    if (n == 0) throw new IllegalArgumentException("Convite não encontrado.");
    acesso.auditar(t, "CLIENT_AREA_INVITE_REVOKE", clientId, m("link", linkId), false);
  }

  // ─── Publico (sem login): abrir e conferir o convite ─────────────────────────

  record Link(UUID id, UUID tenantId, UUID clientId, UUID appointmentId, String proposito) {}

  Link link(String token) {
    if (token == null || token.length() < 20 || token.length() > 100) throw new ApiClientErrorException("Convite inválido.", 404);
    List<Map<String, Object>> r = jdbc.queryForList("select * from links_do_cliente where token_hash = :h", Map.of("h", Saude.sha256(token)));
    if (r.isEmpty() || !CONVITE.equals(r.get(0).get("proposito"))) throw new ApiClientErrorException("Convite inválido.", 404);
    Map<String, Object> l = r.get(0);
    if (l.get("usado_em") != null) {
      throw new ApiClientErrorException("Este convite já foi usado. Entre na sua área com seu telefone, e-mail ou CPF e a sua senha.", 410);
    }
    if (l.get("revogado_em") != null || l.get("bloqueado_em") != null
        || ((java.sql.Timestamp) l.get("expira_em")).toInstant().isBefore(Instant.now())) {
      throw new ApiClientErrorException("Este convite não vale mais. Peça um novo ao salão.", 410);
    }
    return new Link((UUID) l.get("id"), (UUID) l.get("tenant_id"), (UUID) l.get("client_id"), (UUID) l.get("appointment_id"),
        String.valueOf(l.get("proposito")));
  }

  Map<String, Object> abertura(Link l) {
    String salao = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", l.tenantId()), String.class);
    String nome = jdbc.queryForObject("select name from clients where id = :c", Map.of("c", l.clientId()), String.class);
    return m("salao", salao, "primeiroNome", primeiroNome(nome));
  }

  /** Confere os 4 ultimos digitos do telefone. O 5o erro bloqueia o convite. */
  void conferirDigitos(Link l, String digitos) {
    String telefone = jdbc.queryForObject("select phone from clients where id = :c", Map.of("c", l.clientId()), String.class);
    String esperado = ultimos4(telefone);
    String dado = digitos == null ? "" : digitos.replaceAll("\\D", "");
    if (esperado == null || !esperado.equals(dado)) {
      jdbc.update("update links_do_cliente set tentativas = tentativas + 1,"
          + " bloqueado_em = case when tentativas + 1 >= :max then now() else bloqueado_em end where id = :l",
          Map.of("max", TENTATIVAS, "l", l.id()));
      throw new ApiClientErrorException("Os números não conferem. Confira os 4 últimos dígitos do seu telefone.", 400);
    }
    jdbc.update("update links_do_cliente set conferido_em = coalesce(conferido_em, now()), tentativas = 0 where id = :l", Map.of("l", l.id()));
  }

  String novaSessao(Link l) {
    return cifra.encrypt(l.id() + "|" + Instant.now().plus(SESSAO).getEpochSecond());
  }

  /** Convite valido e sessao (dos 4 digitos) do mesmo convite, ainda no prazo. */
  Link sessao(String token, String sessao) {
    Link l = link(token);
    try {
      String[] partes = cifra.decrypt(sessao).split("\\|");
      if (partes.length == 2 && partes[0].equals(l.id().toString()) && Long.parseLong(partes[1]) >= Instant.now().getEpochSecond()) return l;
    } catch (Exception ignored) {
      // cai no erro abaixo
    }
    // 400, e nao 401: a sessao do convite nao e a sessao da area.
    throw new ApiClientErrorException("O tempo para criar o acesso acabou. Abra o convite de novo.", 400);
  }

  /** Marca o convite como usado. Falha se outro pedido usou o mesmo convite no meio do caminho. */
  void usar(Link l, UUID contaId) {
    int n = jdbc.update("update links_do_cliente set usado_em = now(), conta_id = :conta where id = :l and usado_em is null",
        Map.of("l", l.id(), "conta", contaId));
    if (n == 0) throw new ApiClientErrorException("Este convite já foi usado.", 410);
  }

  // ─── O que a cliente preenche e assina (area da cliente) ─────────────────────

  /** Modelos de anamnese para a cliente: os exigidos pelos servicos dos agendamentos; sem exigencia, o modelo "Geral". */
  Set<UUID> modelosOferecidos(UUID t, List<UUID> agendamentos) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (UUID servico : servicos(agendamentos)) {
      for (Map<String, Object> e : modelos.exigenciasDosServicos(t, List.of(servico))) {
        if (e.get("modelo_anamnese_id") != null) ids.add((UUID) e.get("modelo_anamnese_id"));
      }
    }
    jdbc.query("select id from modelos_de_anamnese where tenant_id = :t and ativo and versao_atual_id is not null"
        + " order by (segmento = 'GERAL') desc, created_at limit 1", Map.of("t", t), rs -> {
          ids.add((UUID) rs.getObject(1));
        });
    return ids;
  }

  private List<UUID> servicos(List<UUID> agendamentos) {
    if (agendamentos == null || agendamentos.isEmpty()) return List.of();
    return jdbc.queryForList("select distinct service_id from appointment_items where appointment_id in (:a) and service_id is not null",
        Map.of("a", agendamentos), UUID.class);
  }

  List<Map<String, Object>> anamnesesOferecidas(UUID t, UUID clientId, List<UUID> agendamentos) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (UUID modeloId : modelosOferecidos(t, agendamentos)) {
      Map<String, Object> mod = jdbc.queryForMap("select m.nome, v.perguntas from modelos_de_anamnese m"
          + " join versoes_de_modelo_de_anamnese v on v.id = m.versao_atual_id where m.id = :m", Map.of("m", modeloId));
      Map<String, Object> atual = anamnese.versaoAtual(t, clientId, modeloId);
      r.add(m("modeloId", modeloId, "nome", mod.get("nome"), "perguntas", Saude.lista(mod.get("perguntas")),
          "respostas", atual == null ? Map.of() : atual.get("respostas"),
          "versaoId", atual == null ? null : atual.get("id"),
          "atualizadaEm", atual == null ? null : Saude.instante(atual.get("created_at")),
          "completa", atual != null && Boolean.TRUE.equals(atual.get("completa")),
          "confirmada", atual != null && Boolean.TRUE.equals(atual.get("confirmada"))));
    }
    return r;
  }

  /** Termos dos servicos dos agendamentos, mais as autorizacoes de fotos e de marketing do salao. */
  List<Map<String, Object>> termosOferecidos(UUID t, UUID clientId, List<UUID> agendamentos) {
    List<Map<String, Object>> r = new ArrayList<>();
    Set<UUID> vistos = new LinkedHashSet<>();
    boolean fotos = false;
    List<UUID> servicos = servicos(agendamentos);
    if (!servicos.isEmpty()) {
      for (Map<String, Object> e : modelos.exigenciasDosServicos(t, servicos)) {
        UUID termo = (UUID) e.get("termo_id");
        if (termo != null && vistos.add(termo)) r.add(termo(t, clientId, termo, true));
        if (Boolean.TRUE.equals(e.get("exige_autorizacao_fotos"))) fotos = true;
      }
    }
    for (String tipo : List.of("AUTORIZACAO_FOTOS", "AUTORIZACAO_MARKETING")) {
      Map<String, Object> doc = modelos.documentoAtivoDoTipo(t, tipo);
      if (doc == null || !vistos.add((UUID) doc.get("id"))) continue;
      r.add(termo(t, clientId, (UUID) doc.get("id"), tipo.equals("AUTORIZACAO_FOTOS") && fotos));
    }
    return r;
  }

  private Map<String, Object> termo(UUID t, UUID clientId, UUID documentoId, boolean exigido) {
    Map<String, Object> d = jdbc.queryForMap("""
        select d.id, d.tipo, d.nome, v.numero, v.conteudo from modelos_de_documento d
          join versoes_de_documento v on v.id = d.versao_atual_id where d.id = :d""", Map.of("d", documentoId));
    String tipo = String.valueOf(d.get("tipo"));
    boolean assinado = tipo.startsWith("AUTORIZACAO_") ? anamnese.autorizacaoAtiva(t, clientId, tipo) : anamnese.termoAssinado(t, clientId, documentoId);
    return m("documentoId", d.get("id"), "tipo", tipo, "nome", d.get("nome"), "versao", d.get("numero"), "conteudo", d.get("conteudo"),
        "exigido", exigido, "assinado", assinado, "opcional", "AUTORIZACAO_MARKETING".equals(tipo));
  }
}
