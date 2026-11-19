package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.util.DocumentoValidator;

/**
 * Conta da cliente (PLANO-AREA-DA-CLIENTE.md): uma pessoa, um login, varios salões. A conta nao pertence a um salao; cada
 * salao entra por um vinculo criado pelo convite dele. Entra por telefone, e-mail confirmado ou CPF, sempre com a senha.
 *
 * <p>A sessao e opaca: o token so existe no cookie (ou no app) e o banco guarda o hash. Nenhuma rota do salao aceita
 * esse token: ele nao e um JWT e so e lido em {@code /api/v1/public/area-da-cliente/**}.
 */
@Service
public class ContasDaCliente {

  static final Duration SESSAO = Duration.ofDays(30);
  static final Duration BLOQUEIO = Duration.ofMinutes(15);
  static final int ERROS = 5;
  static final Duration LINK_DE_EMAIL = Duration.ofHours(24);
  static final String ERRO_DE_ACESSO = "Dados de acesso incorretos.";
  /** Hash de uma senha qualquer: o login sem conta gasta o mesmo tempo do login com conta. */
  private static final String HASH_FALSO = BCrypt.withDefaults().hashToString(12, "conta-que-nao-existe".toCharArray());

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final LinksDaCliente links;
  private final AnamneseDaCliente anamnese;
  private final EncryptionService cifra;
  private final PasswordPolicyValidator politica;
  private final EmailJobService emails;

  public ContasDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, LinksDaCliente links, AnamneseDaCliente anamnese,
      EncryptionService cifra, PasswordPolicyValidator politica, EmailJobService emails) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.links = links;
    this.anamnese = anamnese;
    this.cifra = cifra;
    this.politica = politica;
    this.emails = emails;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** 55 + DDD + numero. Com 10 ou 11 digitos, e brasileiro sem o 55; o resto fica como chegou (estrangeiro). */
  static String telefoneCanonico(String telefone) {
    String d = telefone == null ? "" : telefone.replaceAll("\\D", "");
    if (d.isEmpty()) return null;
    if (d.length() == 10 || d.length() == 11) return "55" + d;
    return d;
  }

  static String cpfMascarado(String cpf) {
    if (cpf == null || cpf.length() != 11) return null;
    return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
  }

  static String emailLimpo(String email) {
    if (email == null || email.isBlank()) return null;
    String e = email.trim().toLowerCase(Locale.ROOT);
    if (e.length() > 255 || !e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("E-mail inválido.");
    return e;
  }

  // ─── Salao ─────────────────────────────────────────────────────────────────

  UUID contaDoCadastro(UUID t, UUID clientId) {
    return jdbc.query("select conta_id from vinculos_da_conta where tenant_id = :t and client_id = :c and desfeito_em is null",
        Map.of("t", t, "c", clientId), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
  }

  /** A cliente tem conta? Convite pendente? (Sem dados da conta: o salao nao ve e-mail, CPF nem outros salões.) */
  @Transactional(readOnly = true)
  public Map<String, Object> situacao(UUID clientId) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    Map<String, Object> v = jdbc.query("select created_at from vinculos_da_conta where tenant_id = :t and client_id = :c and desfeito_em is null",
        Map.of("t", t, "c", clientId), rs -> rs.next() ? m("desde", Saude.instante(rs.getTimestamp(1))) : null);
    Map<String, Object> convite = jdbc.query("""
        select id, expira_em from links_do_cliente where tenant_id = :t and client_id = :c and proposito = 'CONVITE_DA_CONTA'
           and usado_em is null and revogado_em is null and bloqueado_em is null and expira_em > now()
         order by created_at desc limit 1""", Map.of("t", t, "c", clientId),
        rs -> rs.next() ? m("id", rs.getObject(1), "expiraEm", Saude.instante(rs.getTimestamp(2))) : null);
    return m("temConta", v != null, "desde", v == null ? null : v.get("desde"), "convitePendente", convite);
  }

  /**
   * Convidar a cliente. Com conta, nao sai token: a mensagem leva so o endereco da area. {@code recuperar}: a cliente
   * perdeu o acesso; sai um convite que, depois dos 4 digitos, deixa criar uma senha nova.
   */
  @Transactional
  public Map<String, Object> convidar(UUID clientId, boolean recuperar, UUID appointmentId) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    if (contaDoCadastro(t, clientId) != null && !recuperar) {
      String telefone = jdbc.queryForObject("select phone from clients where id = :c", Map.of("c", clientId), String.class);
      String nome = jdbc.queryForObject("select name from tenants where id = :t", Map.of("t", t), String.class);
      String texto = "Olá! Você tem novidades no " + nome + ". Entre na sua área de cliente: " + links.urlDaArea();
      return m("temConta", true, "url", links.urlDaArea(), "mensagem", texto,
          "whatsappUrl", telefone == null ? null : LinksDaCliente.whatsapp(telefone, texto));
    }
    Map<String, Object> r = new LinkedHashMap<>(links.criarConvite(t, clientId, appointmentId, acesso.usuarioId()));
    r.put("temConta", contaDoCadastro(t, clientId) != null);
    return r;
  }

  /** O salao desliga a conta do cadastro (ex.: convite foi para o telefone errado). Os dados do salao nao mudam. */
  @Transactional
  public void desligar(UUID clientId, String motivo) {
    UUID t = acesso.tenant();
    int n = jdbc.update("update vinculos_da_conta set desfeito_em = now(), desfeito_por = 'SALAO', motivo = :m"
        + " where tenant_id = :t and client_id = :c and desfeito_em is null", m("t", t, "c", clientId, "m", Saude.limpar(motivo, 300)));
    if (n == 0) throw new IllegalArgumentException("A cliente não tem conta ligada a este cadastro.");
    acesso.auditar(t, "CLIENT_AREA_UNLINK", clientId, m("por", "SALAO"), false);
  }

  /** Agendamento online: se os servicos pedem anamnese ou termo, a tela final leva a cliente a area (ou ao convite). */
  public String linkParaAgendamentoOnline(UUID t, UUID clientId, UUID appointmentId) {
    Map<String, Object> pend = anamnese.pendencias(t, appointmentId, false);
    if (((List<?>) pend.get("itens")).isEmpty()) return null;
    if (contaDoCadastro(t, clientId) != null) return links.urlDaArea();
    return String.valueOf(links.criarConvite(t, clientId, appointmentId, null).get("url"));
  }

  // ─── Convite (publico) ─────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Map<String, Object> abrirConvite(String token) {
    return links.abertura(links.link(token));
  }

  /** Depois dos 4 digitos: a sessao do convite (30 min) e o que a cliente pode fazer com ele. */
  @Transactional(noRollbackFor = ApiClientErrorException.class)
  public Map<String, Object> conferirConvite(String token, String digitos) {
    LinksDaCliente.Link l = links.link(token);
    links.conferirDigitos(l, digitos);
    Map<String, Object> cli = jdbc.queryForMap("select name, email, phone from clients where id = :c", Map.of("c", l.clientId()));
    String telefone = telefoneCanonico((String) cli.get("phone"));
    UUID contaDoTelefone = contaPor("TELEFONE", telefone);
    UUID ligada = contaDoCadastro(l.tenantId(), l.clientId());
    Map<String, Object> r = new LinkedHashMap<>(links.abertura(l));
    r.put("sessao", links.novaSessao(l));
    r.put("nome", cli.get("name"));
    r.put("email", cli.get("email"));
    // Recuperar o acesso: o cadastro ja tem conta; o convite deixa criar uma senha nova.
    r.put("contaLigada", ligada != null);
    r.put("telefoneTemConta", contaDoTelefone != null);
    r.put("termos", termosVigentes());
    return r;
  }

  public record NovaConta(String nome, String email, String senha, Boolean aceite) {}

  public record Entrada(String tipo, String identificador, String senha) {}

  /** Cria a conta e liga ao cadastro do convite. Devolve o token da sessao. */
  @Transactional
  public String criarPeloConvite(String token, String sessao, NovaConta n, String ip, String userAgent) {
    LinksDaCliente.Link l = links.sessao(token, sessao);
    if (contaDoCadastro(l.tenantId(), l.clientId()) != null) {
      throw new ApiClientErrorException("Este cadastro já tem conta. Entre com os seus dados ou crie uma senha nova.", 409);
    }
    if (n == null || !Boolean.TRUE.equals(n.aceite())) throw new IllegalArgumentException("Para criar a conta, aceite os termos de uso.");
    String nome = Saude.limpar(n.nome(), 160);
    if (nome == null) throw new IllegalArgumentException("Informe o seu nome.");
    politica.validateOrThrow(n.senha());
    String email = emailLimpo(n.email());
    if (email != null && contaPor("EMAIL_QUALQUER", email) != null) {
      throw new IllegalArgumentException("Este e-mail já está em uma conta. Entre por ela, com a opção \"Já tenho conta\".");
    }
    String telefone = telefoneCanonico(jdbc.queryForObject("select phone from clients where id = :c", Map.of("c", l.clientId()), String.class));
    // Numero que mudou de dono: quem recebeu o convite e sabe os 4 digitos fica com o numero; a conta antiga perde o
    // telefone, guarda os vinculos e so entra pelo e-mail, pelo CPF ou por um novo convite.
    UUID antiga = contaPor("TELEFONE", telefone);
    if (antiga != null) {
      jdbc.update("update contas_de_cliente set telefone = null, updated_at = now() where id = :id", Map.of("id", antiga));
      acesso.auditar(l.tenantId(), "CLIENT_ACCOUNT_PHONE_MOVED", l.clientId(), m("contaAntiga", antiga), false);
    }
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into contas_de_cliente (id, nome, telefone, email, senha_hash) values (:id, :n, :tel, :e, :h)""",
        m("id", id, "n", nome, "tel", telefone, "e", email, "h", BCrypt.withDefaults().hashToString(12, n.senha().toCharArray())));
    aceitar(id, ip, userAgent);
    ligar(id, l);
    if (email != null) enviarConfirmacao(id, l.tenantId());
    acesso.auditar(l.tenantId(), "CLIENT_ACCOUNT_CREATE", l.clientId(), m("conta", id), false);
    return criarSessao(id, ip, userAgent);
  }

  /** Ja tem conta: entra e liga a conta ao cadastro do convite. */
  @Transactional(noRollbackFor = ApiClientErrorException.class)
  public String entrarPeloConvite(String token, String sessao, Entrada e, String ip, String userAgent) {
    LinksDaCliente.Link l = links.sessao(token, sessao);
    UUID conta = autenticar(e);
    UUID ligada = contaDoCadastro(l.tenantId(), l.clientId());
    if (ligada != null && !ligada.equals(conta)) {
      throw new ApiClientErrorException("Este cadastro já está ligado a outra conta. Fale com o salão.", 409);
    }
    if (ligada == null) {
      Boolean outro = jdbc.queryForObject("select exists(select 1 from vinculos_da_conta where conta_id = :conta and tenant_id = :t"
          + " and desfeito_em is null)", Map.of("conta", conta, "t", l.tenantId()), Boolean.class);
      if (Boolean.TRUE.equals(outro)) {
        throw new ApiClientErrorException("Sua conta já está ligada a outro cadastro neste salão. Fale com o salão.", 409);
      }
      ligar(conta, l);
    } else {
      links.usar(l, conta);
    }
    return criarSessao(conta, ip, userAgent);
  }

  /** Recuperar o acesso pelo convite: o cadastro ja tem conta; a cliente cria uma senha nova e entra. */
  @Transactional
  public String novaSenhaPeloConvite(String token, String sessao, String senha, String ip, String userAgent) {
    LinksDaCliente.Link l = links.sessao(token, sessao);
    UUID conta = contaDoCadastro(l.tenantId(), l.clientId());
    if (conta == null) throw new ApiClientErrorException("Este cadastro ainda não tem conta. Crie a sua conta.", 409);
    politica.validateOrThrow(senha);
    trocarHash(conta, senha);
    links.usar(l, conta);
    acesso.auditar(l.tenantId(), "CLIENT_ACCOUNT_RECOVER", l.clientId(), m("conta", conta), false);
    return criarSessao(conta, ip, userAgent);
  }

  private void ligar(UUID conta, LinksDaCliente.Link l) {
    links.usar(l, conta);
    jdbc.update("insert into vinculos_da_conta (conta_id, tenant_id, client_id, link_id) values (:conta, :t, :c, :l)",
        Map.of("conta", conta, "t", l.tenantId(), "c", l.clientId(), "l", l.id()));
    acesso.auditar(l.tenantId(), "CLIENT_AREA_LINK", l.clientId(), m("conta", conta), false);
  }

  // ─── Login e sessao ────────────────────────────────────────────────────────

  /** A conta ativa de um telefone, e-mail ou CPF. EMAIL so acha e-mail confirmado; EMAIL_QUALQUER, qualquer um. */
  UUID contaPor(String tipo, String valor) {
    if (valor == null || valor.isBlank()) return null;
    String sql = switch (tipo) {
      case "TELEFONE" -> "select id from contas_de_cliente where telefone = :v and excluida_em is null";
      case "EMAIL" -> "select id from contas_de_cliente where lower(email) = :v and email_confirmado_em is not null and excluida_em is null";
      case "EMAIL_QUALQUER" -> "select id from contas_de_cliente where lower(email) = :v and excluida_em is null";
      case "CPF" -> "select id from contas_de_cliente where cpf_busca = :v and excluida_em is null";
      default -> throw new IllegalArgumentException("Escolha telefone, e-mail ou CPF.");
    };
    return jdbc.query(sql, Map.of("v", valor), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
  }

  UUID acharParaEntrar(Entrada e) {
    String tipo = e.tipo() == null ? "" : e.tipo().trim().toUpperCase(Locale.ROOT);
    String v = e.identificador() == null ? "" : e.identificador().trim();
    return switch (tipo) {
      case "TELEFONE" -> contaPor("TELEFONE", telefoneCanonico(v));
      case "EMAIL" -> contaPor("EMAIL", v.toLowerCase(Locale.ROOT));
      case "CPF" -> {
        String d = DocumentoValidator.digitos(v);
        yield d == null || d.length() != 11 ? null : contaPor("CPF", cifra.hmac(d));
      }
      default -> throw new IllegalArgumentException("Escolha telefone, e-mail ou CPF.");
    };
  }

  /** Confere a senha. 5 erros, por qualquer meio de entrada, bloqueiam a conta por 15 minutos. */
  UUID autenticar(Entrada e) {
    if (e == null) throw new ApiClientErrorException(ERRO_DE_ACESSO, 400);
    UUID id = acharParaEntrar(e);
    char[] senha = (e.senha() == null ? "" : e.senha()).toCharArray();
    if (id == null) {
      BCrypt.verifyer().verify(senha, HASH_FALSO);
      throw new ApiClientErrorException(ERRO_DE_ACESSO, 400);
    }
    Map<String, Object> c = jdbc.queryForMap("select senha_hash, bloqueada_ate from contas_de_cliente where id = :id", Map.of("id", id));
    java.sql.Timestamp ate = (java.sql.Timestamp) c.get("bloqueada_ate");
    if (ate != null && ate.toInstant().isAfter(Instant.now())) {
      throw new ApiClientErrorException("Muitas tentativas erradas. Tente de novo em 15 minutos.", 400);
    }
    if (!BCrypt.verifyer().verify(senha, String.valueOf(c.get("senha_hash"))).verified) {
      jdbc.update("""
          update contas_de_cliente set tentativas = case when tentativas + 1 >= :max then 0 else tentativas + 1 end,
                 bloqueada_ate = case when tentativas + 1 >= :max then :ate else bloqueada_ate end where id = :id""",
          Map.of("max", ERROS, "ate", java.sql.Timestamp.from(Instant.now().plus(BLOQUEIO)), "id", id));
      throw new ApiClientErrorException(ERRO_DE_ACESSO, 400);
    }
    jdbc.update("update contas_de_cliente set tentativas = 0, bloqueada_ate = null where id = :id", Map.of("id", id));
    return id;
  }

  @Transactional(noRollbackFor = ApiClientErrorException.class)
  public String entrar(Entrada e, String ip, String userAgent) {
    return criarSessao(autenticar(e), ip, userAgent);
  }

  String criarSessao(UUID conta, String ip, String userAgent) {
    String token = LinksDaCliente.novoToken();
    jdbc.update("""
        insert into sessoes_da_conta (conta_id, token_hash, expira_em, ip, navegador) values (:c, :h, :e, :ip, :ua)""",
        m("c", conta, "h", Saude.sha256(token), "e", java.sql.Timestamp.from(Instant.now().plus(SESSAO)),
            "ip", Saude.limpar(ip, 60), "ua", Saude.limpar(userAgent, 300)));
    return token;
  }

  /** A conta da sessao, ou 401. A sessao vale 30 dias e se renova no uso (no maximo uma escrita por hora). */
  @Transactional
  public UUID contaDaSessao(String token) {
    if (token == null || token.length() < 20 || token.length() > 100) throw new ApiClientErrorException("Entre na sua área.", 401);
    List<Map<String, Object>> r = jdbc.queryForList("""
        select s.id, s.conta_id, s.ultimo_uso_em from sessoes_da_conta s join contas_de_cliente c on c.id = s.conta_id
         where s.token_hash = :h and s.revogada_em is null and s.expira_em > now() and c.excluida_em is null
           and (c.sessoes_revogadas_antes is null or s.created_at >= c.sessoes_revogadas_antes)""", Map.of("h", Saude.sha256(token)));
    if (r.isEmpty()) throw new ApiClientErrorException("Entre na sua área.", 401);
    java.sql.Timestamp uso = (java.sql.Timestamp) r.get(0).get("ultimo_uso_em");
    if (uso.toInstant().isBefore(Instant.now().minus(Duration.ofHours(1)))) {
      jdbc.update("update sessoes_da_conta set ultimo_uso_em = now(), expira_em = :e where id = :id",
          Map.of("e", java.sql.Timestamp.from(Instant.now().plus(SESSAO)), "id", r.get(0).get("id")));
    }
    return (UUID) r.get(0).get("conta_id");
  }

  @Transactional
  public void sair(String token) {
    if (token == null) return;
    jdbc.update("update sessoes_da_conta set revogada_em = now() where token_hash = :h and revogada_em is null", Map.of("h", Saude.sha256(token)));
  }

  // ─── Termos da area ────────────────────────────────────────────────────────

  Map<String, Object> termosVigentes() {
    return jdbc.query("select id, versao, conteudo, hash_conteudo, rascunho from termos_da_area_da_cliente where vigente",
        Map.of(), rs -> rs.next() ? m("id", rs.getObject(1), "versao", rs.getString(2), "conteudo", rs.getString(3),
            "hash", rs.getString(4), "rascunho", rs.getBoolean(5)) : null);
  }

  private void aceitar(UUID conta, String ip, String userAgent) {
    Map<String, Object> t = termosVigentes();
    if (t == null) return;
    jdbc.update("insert into aceites_da_conta (conta_id, termo_id, hash_conteudo, ip, navegador) values (:c, :t, :h, :ip, :ua)",
        m("c", conta, "t", t.get("id"), "h", t.get("hash"), "ip", Saude.limpar(ip, 60), "ua", Saude.limpar(userAgent, 300)));
  }

  // ─── Minha conta ───────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Map<String, Object> minhaConta(UUID conta) {
    Map<String, Object> c = jdbc.queryForMap("select nome, telefone, email, email_confirmado_em, cpf_cifrado, created_at"
        + " from contas_de_cliente where id = :id", Map.of("id", conta));
    String cpf = c.get("cpf_cifrado") == null ? null : cifra.decrypt(String.valueOf(c.get("cpf_cifrado")));
    return m("nome", c.get("nome"), "telefone", c.get("telefone"), "email", c.get("email"),
        "emailConfirmado", c.get("email_confirmado_em") != null, "cpf", cpfMascarado(cpf), "desde", Saude.instante(c.get("created_at")),
        "saloes", vinculos(conta), "termos", termosVigentes());
  }

  /** Os salões da conta (vinculos ativos). */
  List<Map<String, Object>> vinculos(UUID conta) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> v : jdbc.queryForList("""
        select v.tenant_id, v.client_id, v.created_at, t.name from vinculos_da_conta v join tenants t on t.id = v.tenant_id
         where v.conta_id = :c and v.desfeito_em is null order by t.name""", Map.of("c", conta))) {
      r.add(m("salaoId", v.get("tenant_id"), "salao", v.get("name"), "desde", Saude.instante(v.get("created_at"))));
    }
    return r;
  }

  /** O cadastro da cliente naquele salao. Sem vinculo, 404: nao revela nada sobre o salao. */
  UUID cadastroNoSalao(UUID conta, UUID tenantId) {
    UUID c = tenantId == null ? null : jdbc.query("select client_id from vinculos_da_conta where conta_id = :conta and tenant_id = :t"
        + " and desfeito_em is null", Map.of("conta", conta, "t", tenantId), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    if (c == null) throw new ApiClientErrorException("Salão não encontrado.", 404);
    return c;
  }

  @Transactional
  public Map<String, Object> atualizarNome(UUID conta, String nome) {
    String n = Saude.limpar(nome, 160);
    if (n == null) throw new IllegalArgumentException("Informe o seu nome.");
    jdbc.update("update contas_de_cliente set nome = :n, updated_at = now() where id = :id", Map.of("n", n, "id", conta));
    return minhaConta(conta);
  }

  @Transactional
  public Map<String, Object> definirEmail(UUID conta, String email) {
    String e = emailLimpo(email);
    UUID outra = e == null ? null : contaPor("EMAIL_QUALQUER", e);
    if (outra != null && !outra.equals(conta)) throw new IllegalArgumentException("Este e-mail já está em outra conta.");
    jdbc.update("update contas_de_cliente set email = :e, email_confirmado_em = null, updated_at = now() where id = :id",
        m("e", e, "id", conta));
    if (e != null) enviarConfirmacao(conta, null);
    return minhaConta(conta);
  }

  @Transactional
  public Map<String, Object> reenviarConfirmacao(UUID conta) {
    enviarConfirmacao(conta, null);
    return minhaConta(conta);
  }

  @Transactional
  public Map<String, Object> definirCpf(UUID conta, String cpf) {
    String d = DocumentoValidator.digitos(cpf);
    if (d == null || d.isEmpty()) {
      jdbc.update("update contas_de_cliente set cpf_cifrado = null, cpf_busca = null, updated_at = now() where id = :id", Map.of("id", conta));
      return minhaConta(conta);
    }
    if (!DocumentoValidator.cpfValido(d)) throw new IllegalArgumentException("CPF inválido.");
    String busca = cifra.hmac(d);
    UUID outra = contaPor("CPF", busca);
    if (outra != null && !outra.equals(conta)) {
      throw new IllegalArgumentException("Este CPF já está em uma conta. Entre por ela, ou peça ajuda ao salão.");
    }
    jdbc.update("update contas_de_cliente set cpf_cifrado = :c, cpf_busca = :b, updated_at = now() where id = :id",
        Map.of("c", cifra.encrypt(d), "b", busca, "id", conta));
    return minhaConta(conta);
  }

  @Transactional(noRollbackFor = ApiClientErrorException.class)
  public void trocarSenha(UUID conta, String atual, String nova, String token) {
    String hash = jdbc.queryForObject("select senha_hash from contas_de_cliente where id = :id", Map.of("id", conta), String.class);
    if (!BCrypt.verifyer().verify((atual == null ? "" : atual).toCharArray(), hash).verified) {
      throw new ApiClientErrorException("A senha atual não confere.", 400);
    }
    politica.validateOrThrow(nova);
    trocarHash(conta, nova);
    // A sessao em uso continua; as outras (outros aparelhos) caem.
    jdbc.update("update sessoes_da_conta set revogada_em = now() where conta_id = :c and revogada_em is null and token_hash <> :h",
        Map.of("c", conta, "h", Saude.sha256(token)));
  }

  private void trocarHash(UUID conta, String senha) {
    jdbc.update("update contas_de_cliente set senha_hash = :h, tentativas = 0, bloqueada_ate = null, updated_at = now() where id = :id",
        Map.of("h", BCrypt.withDefaults().hashToString(12, senha.toCharArray()), "id", conta));
  }

  // ─── E-mail: confirmar e criar senha nova ──────────────────────────────────

  private UUID salaoParaEmail(UUID conta) {
    return jdbc.query("select tenant_id from vinculos_da_conta where conta_id = :c and desfeito_em is null order by created_at limit 1",
        Map.of("c", conta), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
  }

  private void enviarConfirmacao(UUID conta, UUID salao) {
    Map<String, Object> c = jdbc.queryForMap("select nome, email from contas_de_cliente where id = :id", Map.of("id", conta));
    if (c.get("email") == null) throw new IllegalArgumentException("Informe um e-mail antes.");
    UUID t = salao != null ? salao : salaoParaEmail(conta);
    if (t == null) return;
    String token = LinksDaCliente.novoToken();
    UUID id = UUID.randomUUID();
    jdbc.update("insert into tokens_da_conta (id, conta_id, tipo, email, token_hash, expira_em) values (:id, :c, 'CONFIRMAR_EMAIL', :e, :h, :x)",
        Map.of("id", id, "c", conta, "e", c.get("email"), "h", Saude.sha256(token), "x", java.sql.Timestamp.from(Instant.now().plus(LINK_DE_EMAIL))));
    emails.enqueueParaContaDaCliente(t, false, id, String.valueOf(c.get("email")), String.valueOf(c.get("nome")),
        links.urlDaArea() + "/confirmar-email?token=" + token);
  }

  private Map<String, Object> tokenDeEmail(String token, String tipo) {
    if (token == null || token.length() < 20 || token.length() > 100) throw new IllegalArgumentException("Link inválido ou vencido.");
    List<Map<String, Object>> r = jdbc.queryForList("select id, conta_id, email from tokens_da_conta where token_hash = :h and tipo = :tipo"
        + " and usado_em is null and expira_em > now()", Map.of("h", Saude.sha256(token), "tipo", tipo));
    if (r.isEmpty()) throw new IllegalArgumentException("Link inválido ou vencido.");
    jdbc.update("update tokens_da_conta set usado_em = now() where id = :id", Map.of("id", r.get(0).get("id")));
    return r.get(0);
  }

  @Transactional
  public void confirmarEmail(String token) {
    Map<String, Object> tk = tokenDeEmail(token, "CONFIRMAR_EMAIL");
    int n = jdbc.update("update contas_de_cliente set email_confirmado_em = now(), updated_at = now()"
        + " where id = :id and lower(email) = lower(:e) and excluida_em is null", Map.of("id", tk.get("conta_id"), "e", tk.get("email")));
    if (n == 0) throw new IllegalArgumentException("Este link é de um e-mail que não está mais na conta.");
  }

  /** Sempre a mesma resposta: nao revela se o e-mail tem conta. */
  @Transactional
  public void esqueciSenha(String email) {
    String e;
    try {
      e = emailLimpo(email);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    UUID conta = contaPor("EMAIL", e);
    if (conta == null) return;
    UUID t = salaoParaEmail(conta);
    if (t == null) return;
    String nome = jdbc.queryForObject("select nome from contas_de_cliente where id = :id", Map.of("id", conta), String.class);
    String token = LinksDaCliente.novoToken();
    UUID id = UUID.randomUUID();
    jdbc.update("insert into tokens_da_conta (id, conta_id, tipo, email, token_hash, expira_em) values (:id, :c, 'NOVA_SENHA', :e, :h, :x)",
        Map.of("id", id, "c", conta, "e", e, "h", Saude.sha256(token), "x", java.sql.Timestamp.from(Instant.now().plus(Duration.ofHours(1)))));
    emails.enqueueParaContaDaCliente(t, true, id, e, nome, links.urlDaArea() + "/nova-senha?token=" + token);
  }

  @Transactional
  public void novaSenha(String token, String senha) {
    politica.validateOrThrow(senha);
    Map<String, Object> tk = tokenDeEmail(token, "NOVA_SENHA");
    UUID conta = (UUID) tk.get("conta_id");
    trocarHash(conta, senha);
    jdbc.update("update contas_de_cliente set sessoes_revogadas_antes = now() where id = :id", Map.of("id", conta));
  }

  // ─── Sair de um salao, excluir a conta ─────────────────────────────────────

  @Transactional
  public Map<String, Object> sairDoSalao(UUID conta, UUID tenantId) {
    UUID clientId = cadastroNoSalao(conta, tenantId);
    jdbc.update("update vinculos_da_conta set desfeito_em = now(), desfeito_por = 'CLIENTE' where conta_id = :c and tenant_id = :t"
        + " and desfeito_em is null", Map.of("c", conta, "t", tenantId));
    acesso.auditar(tenantId, "CLIENT_AREA_UNLINK", clientId, m("por", "CLIENTE", "conta", conta), false);
    return minhaConta(conta);
  }

  /** Apaga a conta e os vinculos. Os registros dos salões ficam (cada salao e o responsavel por eles). */
  @Transactional(noRollbackFor = ApiClientErrorException.class)
  public void excluir(UUID conta, String senha) {
    String hash = jdbc.queryForObject("select senha_hash from contas_de_cliente where id = :id", Map.of("id", conta), String.class);
    if (!BCrypt.verifyer().verify((senha == null ? "" : senha).toCharArray(), hash).verified) {
      throw new ApiClientErrorException("A senha não confere.", 400);
    }
    for (Map<String, Object> v : jdbc.queryForList("select tenant_id, client_id from vinculos_da_conta where conta_id = :c and desfeito_em is null",
        Map.of("c", conta))) {
      acesso.auditar((UUID) v.get("tenant_id"), "CLIENT_AREA_UNLINK", (UUID) v.get("client_id"), m("por", "CONTA", "conta", conta), false);
    }
    jdbc.update("update vinculos_da_conta set desfeito_em = now(), desfeito_por = 'CONTA' where conta_id = :c and desfeito_em is null",
        Map.of("c", conta));
    jdbc.update("update sessoes_da_conta set revogada_em = now() where conta_id = :c and revogada_em is null", Map.of("c", conta));
    jdbc.update("""
        update contas_de_cliente set nome = 'Conta excluída', telefone = null, email = null, email_confirmado_em = null,
               cpf_cifrado = null, cpf_busca = null, senha_hash = :h, excluida_em = now(), updated_at = now() where id = :id""",
        Map.of("h", BCrypt.withDefaults().hashToString(12, LinksDaCliente.novoToken().toCharArray()), "id", conta));
  }
}
