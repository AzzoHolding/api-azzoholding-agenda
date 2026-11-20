package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.time.LocalDate;
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
 * Exames, laudos e receitas da cliente (PLANO-AREA-DA-CLIENTE.md, secao 4). Quem envia: o salao ({@code saude:editar}) ou
 * a propria cliente pela area. Arquivo PDF ou imagem, cifrado em {@code arquivos_protegidos}; a imagem e regravada sem
 * metadados (como as fotos). O salao pode pedir um exame; o pedido vira pendencia na area da cliente.
 */
@Service
public class ExamesDaCliente {

  static final int MAXIMO = 10 * 1024 * 1024;
  static final Set<String> TIPOS = Set.of("EXAME", "LAUDO", "RECEITA", "OUTRO");

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;
  private final ArquivosProtegidos arquivos;
  /** Aviso por e-mail a cliente (se ela tem conta). Por setter: o construtor nao muda. */
  private ContasDaCliente contas;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setContas(@org.springframework.context.annotation.Lazy ContasDaCliente contas) {
    this.contas = contas;
  }

  public ExamesDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese, ArquivosProtegidos arquivos) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
    this.arquivos = arquivos;
  }

  public record Envio(String titulo, String tipo, String dataExame, String observacao, UUID pedidoId, String nomeDoArquivo) {}

  // ─── Regras puras ──────────────────────────────────────────────────────────

  static boolean ehPdf(byte[] b) {
    return b != null && b.length > 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
  }

  /** PDF fica como veio; imagem e regravada (sem EXIF/GPS) como as fotos. */
  static ArquivosProtegidos.Imagem preparar(byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Envie o arquivo do exame.");
    if (bytes.length > MAXIMO) throw new IllegalArgumentException("O arquivo passa de 10 MB.");
    if (ehPdf(bytes)) return new ArquivosProtegidos.Imagem(bytes, 0, 0, "application/pdf");
    if (ArquivosProtegidos.tipoPeloConteudo(bytes) != null) return ArquivosProtegidos.prepararFoto(bytes);
    throw new IllegalArgumentException("O exame precisa ser PDF, JPEG ou PNG.");
  }

  static String nomeLimpo(String nome) {
    if (nome == null) return null;
    String n = nome.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
    return Saude.limpar(n, 160);
  }

  // ─── Comum ─────────────────────────────────────────────────────────────────

  UUID guardar(UUID t, UUID clientId, byte[] bytes, Envio e, String enviadoPor, UUID usuario, UUID conta) {
    String titulo = Saude.limpar(e == null ? null : e.titulo(), 160);
    if (titulo == null) throw new IllegalArgumentException("Dê um nome ao exame.");
    String tipo = e.tipo() == null || e.tipo().isBlank() ? "EXAME" : e.tipo().trim().toUpperCase(java.util.Locale.ROOT);
    if (!TIPOS.contains(tipo)) throw new IllegalArgumentException("Tipo de exame inválido.");
    LocalDate data = null;
    if (e.dataExame() != null && !e.dataExame().isBlank()) {
      try {
        data = LocalDate.parse(e.dataExame().trim());
      } catch (Exception ex) {
        throw new IllegalArgumentException("Data do exame inválida.");
      }
      if (data.isAfter(LocalDate.now(AnamneseDaCliente.ZONA_BR))) throw new IllegalArgumentException("A data do exame não pode ser no futuro.");
    }
    UUID pedido = e.pedidoId();
    if (pedido != null) {
      Boolean ok = jdbc.queryForObject("select exists(select 1 from pedidos_de_exame where tenant_id = :t and client_id = :c and id = :p"
          + " and atendido_em is null and cancelado_em is null)", Map.of("t", t, "c", clientId, "p", pedido), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new IllegalArgumentException("Pedido de exame não encontrado.");
    }
    UUID arquivo = arquivos.guardar(t, clientId, "EXAME", preparar(bytes));
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into exames_da_cliente (id, tenant_id, client_id, titulo, tipo, data_exame, observacao, enviado_por, usuario_id,
                                       conta_id, pedido_id, arquivo_id, nome_do_arquivo)
        values (:id, :t, :c, :ti, :tp, :d, :o, :ep, :u, :conta, :p, :a, :n)""",
        m("id", id, "t", t, "c", clientId, "ti", titulo, "tp", tipo, "d", data == null ? null : java.sql.Date.valueOf(data),
            "o", Saude.limpar(e.observacao(), 500), "ep", enviadoPor, "u", usuario, "conta", conta, "p", pedido, "a", arquivo,
            "n", nomeLimpo(e.nomeDoArquivo())));
    if (pedido != null) jdbc.update("update pedidos_de_exame set atendido_em = now() where id = :p", Map.of("p", pedido));
    acesso.auditar(t, "HEALTH_EXAM_UPLOAD", clientId, m("exame", id, "por", enviadoPor, "conta", conta), false);
    return id;
  }

  List<Map<String, Object>> lista(UUID t, UUID clientId, boolean comArquivados) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> x : jdbc.queryForList("""
        select e.id, e.titulo, e.tipo, e.data_exame, e.observacao, e.enviado_por, e.nome_do_arquivo, e.arquivado_em, e.created_at,
               e.conta_id, e.pedido_id, a.content_type, a.tamanho, u.name as usuario
          from exames_da_cliente e join arquivos_protegidos a on a.id = e.arquivo_id left join users u on u.id = e.usuario_id
         where e.tenant_id = :t and e.client_id = :c and (:todos or e.arquivado_em is null)
         order by coalesce(e.data_exame, e.created_at::date) desc, e.created_at desc""", Map.of("t", t, "c", clientId, "todos", comArquivados))) {
      Map<String, Object> y = new LinkedHashMap<>();
      y.put("id", x.get("id"));
      y.put("titulo", x.get("titulo"));
      y.put("tipo", x.get("tipo"));
      y.put("dataExame", x.get("data_exame") == null ? null : x.get("data_exame").toString());
      y.put("observacao", x.get("observacao"));
      y.put("enviadoPor", x.get("enviado_por"));
      y.put("usuario", x.get("usuario"));
      y.put("nomeDoArquivo", x.get("nome_do_arquivo"));
      y.put("contentType", x.get("content_type"));
      y.put("tamanho", x.get("tamanho"));
      y.put("arquivadoEm", Saude.instante(x.get("arquivado_em")));
      y.put("enviadoEm", Saude.instante(x.get("created_at")));
      y.put("pedidoId", x.get("pedido_id"));
      y.put("contaId", x.get("conta_id"));
      r.add(y);
    }
    return r;
  }

  List<Map<String, Object>> pedidosAbertos(UUID t, UUID clientId) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> p : jdbc.queryForList("""
        select p.id, p.descricao, p.appointment_id, p.created_at, u.name as pedido_por from pedidos_de_exame p
          left join users u on u.id = p.pedido_por
         where p.tenant_id = :t and p.client_id = :c and p.atendido_em is null and p.cancelado_em is null order by p.created_at desc""",
        Map.of("t", t, "c", clientId))) {
      r.add(m("id", p.get("id"), "descricao", p.get("descricao"), "appointmentId", p.get("appointment_id"),
          "pedidoPor", p.get("pedido_por"), "pedidoEm", Saude.instante(p.get("created_at"))));
    }
    return r;
  }

  ArquivosProtegidos.Arquivo arquivo(UUID t, UUID clientId, UUID exameId) {
    List<Map<String, Object>> r = jdbc.queryForList("select arquivo_id from exames_da_cliente where tenant_id = :t and client_id = :c and id = :e",
        Map.of("t", t, "c", clientId, "e", exameId));
    if (r.isEmpty()) throw new IllegalArgumentException("Exame não encontrado.");
    return arquivos.ler(t, (UUID) r.get(0).get("arquivo_id"));
  }

  // ─── Salao ─────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Map<String, Object> doSalao(UUID clientId) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    acesso.exigirSaudeVer(t, clientId);
    acesso.registrarAcesso(t, "EXAMES", clientId, null);
    return m("exames", lista(t, clientId, true), "pedidos", pedidosAbertos(t, clientId),
        "podeEditar", acesso.podeEditarSaude(t, clientId));
  }

  @Transactional
  public Map<String, Object> enviarPeloSalao(UUID clientId, byte[] bytes, Envio e) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    acesso.exigirSaudeEditar(t, clientId);
    guardar(t, clientId, bytes, e, "SALAO", acesso.usuarioId(), null);
    if (contas != null) contas.avisarCliente(t, clientId, "Novo exame na sua área", "O salão guardou um documento na sua área: "
        + Saude.limpar(e.titulo(), 160) + ".");
    return doSalao(clientId);
  }

  @Transactional(readOnly = true)
  public ArquivosProtegidos.Arquivo arquivoParaOSalao(UUID clientId, UUID exameId) {
    UUID t = acesso.tenant();
    acesso.exigirSaudeVer(t, clientId);
    ArquivosProtegidos.Arquivo a = arquivo(t, clientId, exameId);
    acesso.registrarAcesso(t, "EXAME", clientId, exameId);
    return a;
  }

  /** O salao nao apaga exame (sobretudo o que a cliente enviou): so arquiva. */
  @Transactional
  public Map<String, Object> arquivar(UUID clientId, UUID exameId, boolean arquivado) {
    UUID t = acesso.tenant();
    acesso.exigirSaudeEditar(t, clientId);
    int n = jdbc.update("update exames_da_cliente set arquivado_em = " + (arquivado ? "now()" : "null")
        + " where tenant_id = :t and client_id = :c and id = :e", Map.of("t", t, "c", clientId, "e", exameId));
    if (n == 0) throw new IllegalArgumentException("Exame não encontrado.");
    acesso.auditar(t, arquivado ? "HEALTH_EXAM_ARCHIVE" : "HEALTH_EXAM_UNARCHIVE", clientId, m("exame", exameId), false);
    return doSalao(clientId);
  }

  @Transactional
  public Map<String, Object> pedir(UUID clientId, String descricao, UUID appointmentId) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    acesso.exigirSaudeEditar(t, clientId);
    String d = Saude.limpar(descricao, 300);
    if (d == null) throw new IllegalArgumentException("Diga qual exame a cliente deve enviar.");
    if (appointmentId != null) {
      Boolean ok = jdbc.queryForObject("select exists(select 1 from appointments where tenant_id = :t and id = :a and client_id = :c)",
          Map.of("t", t, "a", appointmentId, "c", clientId), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new IllegalArgumentException("Agendamento não encontrado para esta cliente.");
    }
    UUID id = UUID.randomUUID();
    jdbc.update("insert into pedidos_de_exame (id, tenant_id, client_id, descricao, appointment_id, pedido_por) values (:id, :t, :c, :d, :a, :u)",
        m("id", id, "t", t, "c", clientId, "d", d, "a", appointmentId, "u", acesso.usuarioId()));
    acesso.auditar(t, "HEALTH_EXAM_REQUEST", clientId, m("pedido", id), false);
    if (contas != null) contas.avisarCliente(t, clientId, "O salão pediu um exame", "Envie pela sua área: " + d + ".");
    return doSalao(clientId);
  }

  @Transactional
  public Map<String, Object> cancelarPedido(UUID clientId, UUID pedidoId) {
    UUID t = acesso.tenant();
    acesso.exigirSaudeEditar(t, clientId);
    int n = jdbc.update("update pedidos_de_exame set cancelado_em = now() where tenant_id = :t and client_id = :c and id = :p"
        + " and atendido_em is null and cancelado_em is null", Map.of("t", t, "c", clientId, "p", pedidoId));
    if (n == 0) throw new IllegalArgumentException("Pedido não encontrado.");
    return doSalao(clientId);
  }

  // ─── Cliente (area) ────────────────────────────────────────────────────────

  /** A cliente apaga so o que ela mesma enviou; o que o salao enviou fica. */
  void apagarDaCliente(UUID t, UUID clientId, UUID conta, UUID exameId) {
    List<Map<String, Object>> r = jdbc.queryForList("select arquivo_id, enviado_por, conta_id from exames_da_cliente"
        + " where tenant_id = :t and client_id = :c and id = :e", Map.of("t", t, "c", clientId, "e", exameId));
    if (r.isEmpty()) throw new IllegalArgumentException("Exame não encontrado.");
    if (!"CLIENTE".equals(r.get(0).get("enviado_por"))) {
      throw new ApiClientErrorException("Este exame foi enviado pelo salão. Para tirar, fale com o salão.", 403);
    }
    jdbc.update("update pedidos_de_exame set atendido_em = null where id = (select pedido_id from exames_da_cliente where id = :e)",
        Map.of("e", exameId));
    jdbc.update("delete from exames_da_cliente where id = :e", Map.of("e", exameId));
    jdbc.update("delete from arquivos_protegidos where id = :a", Map.of("a", r.get(0).get("arquivo_id")));
    acesso.auditar(t, "HEALTH_EXAM_DELETE", clientId, m("exame", exameId, "por", "CLIENTE", "conta", conta), false);
  }
}
