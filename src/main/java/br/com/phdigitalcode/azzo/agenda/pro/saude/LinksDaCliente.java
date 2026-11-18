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
 * Link pessoal da cliente (plano, etapa 3): preencher e confirmar a anamnese, assinar termos a distancia e ver os
 * proprios documentos. O token sai uma vez (so o hash fica no banco), vale 72 h, tem proposito unico e pede os 4
 * ultimos digitos do telefone da cliente antes de mostrar qualquer dado. 5 erros bloqueiam o link.
 */
@Service
public class LinksDaCliente {

  static final Duration VALIDADE = Duration.ofHours(72);
  static final Duration SESSAO = Duration.ofMinutes(30);
  static final int TENTATIVAS = 5;
  static final Set<String> PROPOSITOS = Set.of("PREENCHER_E_ASSINAR", "MEUS_DOCUMENTOS");
  private static final SecureRandom SORTEIO = new SecureRandom();

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;
  private final ModelosDeSaude modelos;
  private final AssinaturasDaCliente assinaturas;
  private final EncryptionService cifra;
  private final String baseUrl;

  public LinksDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese,
      ModelosDeSaude modelos, AssinaturasDaCliente assinaturas, EncryptionService cifra,
      @Value("${app.public.booking.base-url:http://localhost:3000}") String baseUrl) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
    this.modelos = modelos;
    this.assinaturas = assinaturas;
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

  // ─── Salao: criar, listar, revogar ─────────────────────────────────────────

  @Transactional
  public Map<String, Object> criar(UUID clientId, String proposito, UUID appointmentId) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    return criarInterno(t, clientId, proposito, appointmentId, acesso.usuarioId());
  }

  Map<String, Object> criarInterno(UUID t, UUID clientId, String proposito, UUID appointmentId, UUID usuario) {
    if (!PROPOSITOS.contains(proposito)) throw new IllegalArgumentException("Propósito do link inválido.");
    if (appointmentId != null) {
      Boolean ok = jdbc.queryForObject("select exists(select 1 from appointments where tenant_id = :t and id = :a and client_id = :c)",
          Map.of("t", t, "a", appointmentId, "c", clientId), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new IllegalArgumentException("Agendamento não encontrado para esta cliente.");
    }
    String telefone = jdbc.query("select phone from clients where tenant_id = :t and id = :c", Map.of("t", t, "c", clientId),
        rs -> rs.next() ? rs.getString(1) : null);
    if (ultimos4(telefone) == null) throw new IllegalArgumentException("A cliente precisa ter telefone para receber o link.");
    String token = novoToken();
    UUID id = UUID.randomUUID();
    Instant expira = Instant.now().plus(VALIDADE);
    jdbc.update("""
        insert into links_do_cliente (id, tenant_id, client_id, appointment_id, proposito, token_hash, expira_em, created_by)
        values (:id, :t, :c, :a, :p, :h, :e, :u)""", m("id", id, "t", t, "c", clientId, "a", appointmentId, "p", proposito,
        "h", Saude.sha256(token), "e", java.sql.Timestamp.from(expira), "u", usuario));
    String url = baseUrl + "/documentos/" + token;
    String nome = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", t), String.class);
    String texto = ("PREENCHER_E_ASSINAR".equals(proposito)
        ? "Olá! Para o seu atendimento no " + nome + ", preencha sua ficha e assine os termos por este link: "
        : "Olá! Seus documentos do " + nome + " estão neste link: ") + url + " (vale por 72 horas).";
    String digitos = telefone.replaceAll("\\D", "");
    if (digitos.length() <= 11) digitos = "55" + digitos;
    acesso.auditar(t, "HEALTH_LINK_CREATE", clientId, m("link", id, "proposito", proposito), false);
    return m("id", id, "url", url, "expiraEm", expira.toString(), "mensagem", texto,
        "whatsappUrl", "https://wa.me/" + digitos + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listar(UUID clientId) {
    UUID t = acesso.tenant();
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> l : jdbc.queryForList("""
        select id, proposito, appointment_id, expira_em, conferido_em, revogado_em, bloqueado_em, created_at
          from links_do_cliente where tenant_id = :t and client_id = :c order by created_at desc limit 20""", Map.of("t", t, "c", clientId))) {
      Map<String, Object> x = new LinkedHashMap<>(l);
      for (String c : List.of("expira_em", "conferido_em", "revogado_em", "bloqueado_em", "created_at")) x.put(c, Saude.instante(l.get(c)));
      x.put("situacao", l.get("revogado_em") != null ? "REVOGADO" : l.get("bloqueado_em") != null ? "BLOQUEADO"
          : ((java.sql.Timestamp) l.get("expira_em")).toInstant().isBefore(Instant.now()) ? "VENCIDO" : "VALIDO");
      r.add(x);
    }
    return r;
  }

  @Transactional
  public void revogar(UUID clientId, UUID linkId) {
    UUID t = acesso.tenant();
    int n = jdbc.update("update links_do_cliente set revogado_em = now() where tenant_id = :t and client_id = :c and id = :l and revogado_em is null",
        Map.of("t", t, "c", clientId, "l", linkId));
    if (n == 0) throw new IllegalArgumentException("Link não encontrado.");
    acesso.auditar(t, "HEALTH_LINK_REVOKE", clientId, m("link", linkId), false);
  }

  /** Agendamento online: se os servicos pedem anamnese ou termo, a tela final mostra o link para preencher. */
  public String linkParaAgendamentoOnline(UUID t, UUID clientId, UUID appointmentId) {
    Map<String, Object> pend = anamnese.pendencias(t, appointmentId, false);
    if (((List<?>) pend.get("itens")).isEmpty()) return null;
    return String.valueOf(criarInterno(t, clientId, "PREENCHER_E_ASSINAR", appointmentId, null).get("url"));
  }

  // ─── Publico (sem login) ───────────────────────────────────────────────────

  record Link(UUID id, UUID tenantId, UUID clientId, UUID appointmentId, String proposito) {}

  Link link(String token) {
    if (token == null || token.length() < 20 || token.length() > 100) throw new ApiClientErrorException("Link inválido.", 404);
    List<Map<String, Object>> r = jdbc.queryForList("select * from links_do_cliente where token_hash = :h", Map.of("h", Saude.sha256(token)));
    if (r.isEmpty()) throw new ApiClientErrorException("Link inválido.", 404);
    Map<String, Object> l = r.get(0);
    if (l.get("revogado_em") != null || l.get("bloqueado_em") != null
        || ((java.sql.Timestamp) l.get("expira_em")).toInstant().isBefore(Instant.now())) {
      throw new ApiClientErrorException("Este link não vale mais. Peça um novo ao salão.", 410);
    }
    return new Link((UUID) l.get("id"), (UUID) l.get("tenant_id"), (UUID) l.get("client_id"), (UUID) l.get("appointment_id"),
        String.valueOf(l.get("proposito")));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> abertura(String token) {
    Link l = link(token);
    String salao = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", l.tenantId()), String.class);
    String nome = jdbc.queryForObject("select name from clients where id = :c", Map.of("c", l.clientId()), String.class);
    return m("salao", salao, "primeiroNome", primeiroNome(nome), "proposito", l.proposito());
  }

  /** Confere os 4 ultimos digitos do telefone. Devolve a sessao (30 min) e o conteudo. */
  @Transactional(noRollbackFor = ApiClientErrorException.class)
  public Map<String, Object> conferir(String token, String digitos) {
    Link l = link(token);
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
    String sessao = cifra.encrypt(l.id() + "|" + Instant.now().plus(SESSAO).getEpochSecond());
    Map<String, Object> r = new LinkedHashMap<>(conteudo(l));
    r.put("sessao", sessao);
    return r;
  }

  /** Link valido e sessao do mesmo link, ainda no prazo. */
  Link sessao(String token, String sessao) {
    Link l = link(token);
    try {
      String[] partes = cifra.decrypt(sessao).split("\\|");
      if (partes.length == 2 && partes[0].equals(l.id().toString()) && Long.parseLong(partes[1]) >= Instant.now().getEpochSecond()) return l;
    } catch (Exception ignored) {
      // cai no erro abaixo
    }
    // 400, e nao 401: o cliente HTTP do app trata 401 como "sem login" e mandaria a cliente para o login do salao.
    throw new ApiClientErrorException("Sua sessão expirou. Abra o link de novo.", 400);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> conteudo(String token, String sessao) {
    return conteudo(sessao(token, sessao));
  }

  /** As anamneses para preencher e os termos para assinar (ou os documentos assinados, no "Meus documentos"). */
  Map<String, Object> conteudo(Link l) {
    UUID t = l.tenantId();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("proposito", l.proposito());
    r.put("salao", jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", t), String.class));
    Map<String, Object> cli = jdbc.queryForMap("select name, birth_date from clients where id = :c", Map.of("c", l.clientId()));
    r.put("cliente", cli.get("name"));
    r.put("menor", AnamneseDaCliente.menor(cli.get("birth_date") == null ? null : ((java.sql.Date) cli.get("birth_date")).toLocalDate(),
        java.time.LocalDate.now(AnamneseDaCliente.ZONA_BR)));
    if ("MEUS_DOCUMENTOS".equals(l.proposito())) {
      List<Map<String, Object>> docs = new ArrayList<>();
      for (Map<String, Object> a : assinaturas.historico(t, l.clientId())) {
        if (!"ASSINATURA".equals(a.get("evento"))) continue;
        docs.add(m("id", a.get("id"), "documento", a.get("documento") != null ? a.get("documento")
            : AssinaturasDaCliente.rotulo(String.valueOf(a.get("tipo_documento"))), "tipo", a.get("tipo_documento"),
            "assinadoEm", a.get("created_at"), "hash", a.get("hash_conteudo")));
      }
      r.put("documentos", docs);
      return r;
    }
    r.put("anamneses", anamnesesOferecidas(l));
    r.put("termos", termosOferecidos(l));
    return r;
  }

  Set<UUID> modelosOferecidos(Link l) {
    UUID t = l.tenantId();
    Set<UUID> ids = new LinkedHashSet<>();
    if (l.appointmentId() != null) {
      List<UUID> servicos = jdbc.queryForList("select service_id from appointment_items where appointment_id = :a and service_id is not null",
          Map.of("a", l.appointmentId()), UUID.class);
      for (Map<String, Object> e : modelos.exigenciasDosServicos(t, servicos)) {
        if (e.get("modelo_anamnese_id") != null) ids.add((UUID) e.get("modelo_anamnese_id"));
      }
    }
    if (ids.isEmpty()) {
      jdbc.query("select id from modelos_de_anamnese where tenant_id = :t and ativo and versao_atual_id is not null"
          + " order by (segmento = 'GERAL') desc, created_at limit 1", Map.of("t", t), rs -> {
            ids.add((UUID) rs.getObject(1));
          });
    }
    return ids;
  }

  List<Map<String, Object>> anamnesesOferecidas(Link l) {
    UUID t = l.tenantId();
    List<Map<String, Object>> r = new ArrayList<>();
    for (UUID modeloId : modelosOferecidos(l)) {
      Map<String, Object> mod = jdbc.queryForMap("select m.nome, v.perguntas from modelos_de_anamnese m"
          + " join versoes_de_modelo_de_anamnese v on v.id = m.versao_atual_id where m.id = :m", Map.of("m", modeloId));
      Map<String, Object> atual = anamnese.versaoAtual(t, l.clientId(), modeloId);
      r.add(m("modeloId", modeloId, "nome", mod.get("nome"), "perguntas", Saude.lista(mod.get("perguntas")),
          "respostas", atual == null ? Map.of() : atual.get("respostas"),
          "versaoId", atual == null ? null : atual.get("id"),
          "completa", atual != null && Boolean.TRUE.equals(atual.get("completa")),
          "confirmada", atual != null && Boolean.TRUE.equals(atual.get("confirmada"))));
    }
    return r;
  }

  List<Map<String, Object>> termosOferecidos(Link l) {
    UUID t = l.tenantId();
    List<Map<String, Object>> r = new ArrayList<>();
    Set<UUID> vistos = new LinkedHashSet<>();
    boolean fotos = false;
    if (l.appointmentId() != null) {
      List<UUID> servicos = jdbc.queryForList("select service_id from appointment_items where appointment_id = :a and service_id is not null",
          Map.of("a", l.appointmentId()), UUID.class);
      for (Map<String, Object> e : modelos.exigenciasDosServicos(t, servicos)) {
        UUID termo = (UUID) e.get("termo_id");
        if (termo != null && vistos.add(termo)) r.add(termo(t, l.clientId(), termo, true));
        if (Boolean.TRUE.equals(e.get("exige_autorizacao_fotos"))) fotos = true;
      }
    }
    for (String tipo : List.of("AUTORIZACAO_FOTOS", "AUTORIZACAO_MARKETING")) {
      Map<String, Object> doc = modelos.documentoAtivoDoTipo(t, tipo);
      if (doc == null || !vistos.add((UUID) doc.get("id"))) continue;
      Map<String, Object> x = termo(t, l.clientId(), (UUID) doc.get("id"), tipo.equals("AUTORIZACAO_FOTOS") && fotos);
      r.add(x);
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

  @Transactional
  public Map<String, Object> salvarAnamnese(String token, String sessao, UUID modeloId, Map<String, Object> respostas) {
    Link l = sessao(token, sessao);
    if (!"PREENCHER_E_ASSINAR".equals(l.proposito()) || !modelosOferecidos(l).contains(modeloId)) {
      throw new ApiClientErrorException("Esta anamnese não faz parte do link.", 403);
    }
    UUID v = anamnese.salvarVersao(l.tenantId(), l.clientId(), modeloId, respostas, "LINK", null, l.id());
    return m("versaoId", v, "conteudo", conteudo(l));
  }

  @Transactional
  public Map<String, Object> assinar(String token, String sessao, AssinaturasDaCliente.Pedido pedido, String ip, String userAgent) {
    Link l = sessao(token, sessao);
    if (!"PREENCHER_E_ASSINAR".equals(l.proposito())) throw new ApiClientErrorException("Este link é só para consulta.", 403);
    if ("ANAMNESE".equals(pedido.tipoDocumento())) {
      Boolean ok = jdbc.queryForObject("select exists(select 1 from versoes_de_anamnese v join anamneses a on a.id = v.anamnese_id"
          + " where v.id = :v and v.client_id = :c and a.modelo_id in (:ms))", Map.of("v", pedido.versaoAnamneseId(), "c", l.clientId(),
          "ms", new ArrayList<>(modelosOferecidos(l))), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new ApiClientErrorException("Esta anamnese não faz parte do link.", 403);
    } else if (!"FICHA".equals(pedido.tipoDocumento())) {
      boolean oferecido = termosOferecidos(l).stream().anyMatch(x -> String.valueOf(x.get("documentoId")).equals(String.valueOf(pedido.documentoId())));
      if (!oferecido) throw new ApiClientErrorException("Este termo não faz parte do link.", 403);
    } else {
      throw new ApiClientErrorException("A ficha do atendimento é assinada no salão.", 403);
    }
    AssinaturasDaCliente.Pedido comLink = new AssinaturasDaCliente.Pedido(pedido.evento(), pedido.tipoDocumento(), pedido.documentoId(),
        pedido.versaoAnamneseId(), l.appointmentId(), pedido.assinanteNome(), pedido.assinanteDocumento(), pedido.responsavelLegal(),
        pedido.parentesco(), pedido.imagemPng(), pedido.motivo(), pedido.aceite());
    Map<String, Object> r = assinaturas.registrar(l.tenantId(), l.clientId(), comLink,
        new AssinaturasDaCliente.Origem("LINK", ip, userAgent, null, l.id()));
    return m("assinatura", r, "conteudo", conteudo(l));
  }

  @Transactional(readOnly = true)
  public byte[] pdf(String token, String sessao, UUID assinaturaId) {
    Link l = sessao(token, sessao);
    Map<String, Object> a = assinaturas.linha(l.tenantId(), l.clientId(), assinaturaId);
    if (!"ASSINATURA".equals(a.get("evento"))) throw new IllegalArgumentException("Documento não encontrado.");
    return assinaturas.pdf(l.tenantId(), a);
  }
}
