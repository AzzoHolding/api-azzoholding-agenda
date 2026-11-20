package br.com.phdigitalcode.azzo.agenda.pro.saude;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Area da cliente (PLANO-AREA-DA-CLIENTE.md). Fica sob {@code /api/v1/public/**} para o Spring Security nao exigir o
 * JWT do salao: quem autentica aqui e a sessao da conta da cliente, lida do cookie {@code AZZO_CLIENTE_SESSAO} (ou do
 * cabecalho {@code X-Sessao-Cliente}, para o app). O JWT do salao nao abre nada aqui, e a sessao da cliente nao abre
 * nada do salao.
 */
@RestController
@RequestMapping("/api/v1/public/area-da-cliente")
public class AreaDaClienteController {

  static final String COOKIE = "AZZO_CLIENTE_SESSAO";
  static final String CABECALHO = "X-Sessao-Cliente";
  static final String CAMINHO = "/api/v1/public/area-da-cliente";

  private final ContasDaCliente contas;
  private final AreaDaCliente area;
  private final boolean cookieSeguro;
  private final String sameSite;

  public AreaDaClienteController(ContasDaCliente contas, AreaDaCliente area,
      @Value("${app.auth.cookie.secure:true}") boolean cookieSeguro,
      @Value("${app.auth.cookie.same-site:STRICT}") String sameSite) {
    this.contas = contas;
    this.area = area;
    this.cookieSeguro = cookieSeguro;
    this.sameSite = "NONE".equalsIgnoreCase(sameSite) ? "None" : "LAX".equalsIgnoreCase(sameSite) ? "Lax" : "Strict";
  }

  // ─── Sessao ────────────────────────────────────────────────────────────────

  private static String token(String cookie, String cabecalho) {
    return cabecalho != null && !cabecalho.isBlank() ? cabecalho.trim() : cookie;
  }

  private UUID conta(String cookie, String cabecalho) {
    return contas.contaDaSessao(token(cookie, cabecalho));
  }

  private ResponseCookie cookie(String valor, long segundos) {
    return ResponseCookie.from(COOKIE, valor).httpOnly(true).secure(cookieSeguro).sameSite(sameSite).path(CAMINHO)
        .maxAge(segundos).build();
  }

  private ResponseEntity<Map<String, Object>> comSessao(String token) {
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(token, ContasDaCliente.SESSAO.toSeconds()).toString())
        .body(Map.of("ok", true));
  }

  private static String ua(HttpServletRequest req) {
    return req.getHeader("User-Agent");
  }

  // ─── Convite ───────────────────────────────────────────────────────────────

  @GetMapping("/convite/{token}")
  public Map<String, Object> abrirConvite(@PathVariable String token) {
    return contas.abrirConvite(token);
  }

  @PostMapping("/convite/{token}/conferir")
  public Map<String, Object> conferirConvite(@PathVariable String token, @RequestBody Map<String, String> r) {
    return contas.conferirConvite(token, r == null ? null : r.get("digitos"));
  }

  public record CriarConta(String sessao, String nome, String email, String senha, Boolean aceite) {}

  @PostMapping("/convite/{token}/criar-conta")
  public ResponseEntity<Map<String, Object>> criarConta(@PathVariable String token, @RequestBody CriarConta r, HttpServletRequest req) {
    return comSessao(contas.criarPeloConvite(token, r.sessao(), new ContasDaCliente.NovaConta(r.nome(), r.email(), r.senha(), r.aceite()),
        SaudeController.ip(req), ua(req)));
  }

  public record EntrarPeloConvite(String sessao, String tipo, String identificador, String senha) {}

  @PostMapping("/convite/{token}/entrar")
  public ResponseEntity<Map<String, Object>> entrarPeloConvite(@PathVariable String token, @RequestBody EntrarPeloConvite r,
      HttpServletRequest req) {
    return comSessao(contas.entrarPeloConvite(token, r.sessao(), new ContasDaCliente.Entrada(r.tipo(), r.identificador(), r.senha()),
        SaudeController.ip(req), ua(req)));
  }

  public record NovaSenhaPeloConvite(String sessao, String senha, String email) {}

  @PostMapping("/convite/{token}/nova-senha")
  public ResponseEntity<Map<String, Object>> novaSenhaPeloConvite(@PathVariable String token, @RequestBody NovaSenhaPeloConvite r,
      HttpServletRequest req) {
    return comSessao(contas.novaSenhaPeloConvite(token, r.sessao(), r.senha(), r.email(), SaudeController.ip(req), ua(req)));
  }

  // ─── Entrar, sair, recuperar ───────────────────────────────────────────────

  @PostMapping("/entrar")
  public ResponseEntity<Map<String, Object>> entrar(@RequestBody ContasDaCliente.Entrada r, HttpServletRequest req) {
    return comSessao(contas.entrar(r, SaudeController.ip(req), ua(req)));
  }

  @PostMapping("/sair")
  public ResponseEntity<Map<String, Object>> sair(@CookieValue(name = COOKIE, required = false) String cookie,
      @RequestHeader(name = CABECALHO, required = false) String cabecalho) {
    contas.sair(token(cookie, cabecalho));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).body(Map.of("ok", true));
  }

  @PostMapping("/esqueci-a-senha")
  public Map<String, Object> esqueciSenha(@RequestBody Map<String, String> r) {
    contas.esqueciSenha(r == null ? null : r.get("email"));
    return Map.of("mensagem", "Se este e-mail estiver confirmado em uma conta, enviamos um link para criar uma senha nova.");
  }

  @PostMapping("/nova-senha")
  public Map<String, Object> novaSenha(@RequestBody Map<String, String> r) {
    contas.novaSenha(r.get("token"), r.get("senha"));
    return Map.of("ok", true);
  }

  @PostMapping("/confirmar-email")
  public Map<String, Object> confirmarEmail(@RequestBody Map<String, String> r) {
    contas.confirmarEmail(r.get("token"));
    return Map.of("ok", true);
  }

  // ─── Minha conta ───────────────────────────────────────────────────────────

  @GetMapping("/eu")
  public Map<String, Object> eu(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h) {
    return contas.minhaConta(conta(c, h), token(c, h));
  }

  @PostMapping("/eu/sair-dos-outros-aparelhos")
  public Map<String, Object> sairDosOutros(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h) {
    return contas.sairDosOutrosAparelhos(conta(c, h), token(c, h));
  }

  @GetMapping("/eu/dados")
  public ResponseEntity<Map<String, Object>> dadosDaConta(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h) {
    return baixar(contas.dadosDaConta(conta(c, h)), "minha-conta.json");
  }

  private static ResponseEntity<Map<String, Object>> baixar(Map<String, Object> dados, String nome) {
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store").body(dados);
  }

  @PutMapping("/eu/nome")
  public Map<String, Object> nome(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h,
      @RequestBody Map<String, String> r) {
    return contas.atualizarNome(conta(c, h), r.get("nome"));
  }

  @PutMapping("/eu/email")
  public Map<String, Object> email(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h,
      @RequestBody Map<String, String> r) {
    return contas.definirEmail(conta(c, h), r.get("email"));
  }

  @PostMapping("/eu/email/reenviar")
  public Map<String, Object> reenviar(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h) {
    return contas.reenviarConfirmacao(conta(c, h));
  }

  @PutMapping("/eu/cpf")
  public Map<String, Object> cpf(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h,
      @RequestBody Map<String, String> r) {
    return contas.definirCpf(conta(c, h), r.get("cpf"));
  }

  @PostMapping("/eu/senha")
  public Map<String, Object> senha(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h,
      @RequestBody Map<String, String> r) {
    contas.trocarSenha(conta(c, h), r.get("atual"), r.get("nova"), token(c, h));
    return Map.of("ok", true);
  }

  @PostMapping("/eu/excluir")
  public ResponseEntity<Map<String, Object>> excluir(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @RequestBody Map<String, String> r) {
    contas.excluir(conta(c, h), r.get("senha"));
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).body(Map.of("ok", true));
  }

  // ─── Area ──────────────────────────────────────────────────────────────────

  @GetMapping("/inicio")
  public Map<String, Object> inicio(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h) {
    return area.inicio(conta(c, h));
  }

  @GetMapping("/tudo")
  public Map<String, Object> tudo(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h,
      @RequestParam(name = "salao", required = false) UUID salao) {
    return area.linhaDoTempo(conta(c, h), salao);
  }

  @GetMapping("/saloes/{salao}")
  public Map<String, Object> salao(@CookieValue(name = COOKIE, required = false) String c, @RequestHeader(name = CABECALHO, required = false) String h,
      @PathVariable UUID salao) {
    return area.doSalao(conta(c, h), salao);
  }

  @PostMapping("/saloes/{salao}/horarios/{agendamentoId}/desmarcar")
  public Map<String, Object> desmarcar(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao, @PathVariable UUID agendamentoId) {
    return area.desmarcar(conta(c, h), salao, agendamentoId);
  }

  @GetMapping("/saloes/{salao}/meus-dados")
  public ResponseEntity<Map<String, Object>> meusDados(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao) {
    return baixar(area.meusDados(conta(c, h), salao), "meus-dados.json");
  }

  @PostMapping("/saloes/{salao}/sair")
  public Map<String, Object> sairDoSalao(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao) {
    return contas.sairDoSalao(conta(c, h), salao);
  }

  @PostMapping("/saloes/{salao}/anamnese/{modeloId}")
  public Map<String, Object> anamnese(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao, @PathVariable UUID modeloId,
      @RequestBody Map<String, Object> r) {
    @SuppressWarnings("unchecked")
    Map<String, Object> respostas = r != null && r.get("respostas") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
    return area.salvarAnamnese(conta(c, h), salao, modeloId, respostas);
  }

  @PostMapping("/saloes/{salao}/assinar")
  public Map<String, Object> assinar(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao,
      @RequestBody AssinaturasDaCliente.Pedido pedido, HttpServletRequest req) {
    return area.assinar(conta(c, h), salao, pedido, SaudeController.ip(req), ua(req));
  }

  @GetMapping("/saloes/{salao}/documentos/{assinaturaId}/pdf")
  public ResponseEntity<byte[]> pdf(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao, @PathVariable UUID assinaturaId) {
    return SaudeController.arquivo(area.pdf(conta(c, h), salao, assinaturaId), MediaType.APPLICATION_PDF_VALUE,
        "documento-" + assinaturaId + ".pdf");
  }

  @GetMapping("/saloes/{salao}/fotos/{fotoId}")
  public ResponseEntity<byte[]> foto(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao, @PathVariable UUID fotoId) {
    ArquivosProtegidos.Arquivo a = area.foto(conta(c, h), salao, fotoId);
    return SaudeController.arquivo(a.bytes(), a.contentType(), null);
  }

  @PostMapping(value = "/saloes/{salao}/exames", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> enviarExame(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao,
      @RequestParam("arquivo") MultipartFile arquivo, @RequestParam("titulo") String titulo,
      @RequestParam(name = "tipo", required = false) String tipo, @RequestParam(name = "dataExame", required = false) String dataExame,
      @RequestParam(name = "observacao", required = false) String observacao,
      @RequestParam(name = "pedidoId", required = false) UUID pedidoId) throws java.io.IOException {
    UUID conta = conta(c, h);
    return area.enviarExame(conta, salao, arquivo.getBytes(),
        new ExamesDaCliente.Envio(titulo, tipo, dataExame, observacao, pedidoId, arquivo.getOriginalFilename()));
  }

  @GetMapping("/saloes/{salao}/exames/{exameId}")
  public ResponseEntity<byte[]> exame(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao, @PathVariable UUID exameId) {
    ArquivosProtegidos.Arquivo a = area.exame(conta(c, h), salao, exameId);
    return SaudeController.arquivo(a.bytes(), a.contentType(), "exame-" + exameId + (a.contentType().contains("pdf") ? ".pdf" : ".jpg"));
  }

  @DeleteMapping("/saloes/{salao}/exames/{exameId}")
  public Map<String, Object> apagarExame(@CookieValue(name = COOKIE, required = false) String c,
      @RequestHeader(name = CABECALHO, required = false) String h, @PathVariable UUID salao, @PathVariable UUID exameId) {
    return area.apagarExame(conta(c, h), salao, exameId);
  }
}
