package br.com.phdigitalcode.azzo.agenda.pro.saude;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Rotas do modulo de anamnese e ficha de atendimento (plano PLANO-ANAMNESE-E-FICHA.md). As regras finas de quem ve e
 * quem escreve estao em {@link AcessoDeSaude}; aqui fica so o piso (ler agenda / escrever agenda).
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class SaudeController {

  private final ModelosDeSaude modelos;
  private final AnamneseDaCliente anamnese;
  private final FichaDeAtendimento fichas;
  private final AssinaturasDaCliente assinaturas;
  private final FotosDaFicha fotos;
  private final LinhaDoTempoDaCliente linhaDoTempo;
  private final LinksDaCliente links;
  private final RelatoriosDeSaude relatorios;
  private final ContasDaCliente contas;
  private final ExamesDaCliente exames;
  private AgendaDaCliente agendaDaCliente;

  /** Por setter: o construtor nao muda. */
  @org.springframework.beans.factory.annotation.Autowired
  void setAgendaDaCliente(AgendaDaCliente agendaDaCliente) {
    this.agendaDaCliente = agendaDaCliente;
  }

  public SaudeController(ModelosDeSaude modelos, AnamneseDaCliente anamnese, FichaDeAtendimento fichas,
      AssinaturasDaCliente assinaturas, FotosDaFicha fotos, LinhaDoTempoDaCliente linhaDoTempo, LinksDaCliente links,
      RelatoriosDeSaude relatorios, ContasDaCliente contas, ExamesDaCliente exames) {
    this.contas = contas;
    this.exames = exames;
    this.modelos = modelos;
    this.anamnese = anamnese;
    this.fichas = fichas;
    this.assinaturas = assinaturas;
    this.fotos = fotos;
    this.linhaDoTempo = linhaDoTempo;
    this.links = links;
    this.relatorios = relatorios;
  }

  // ─── Modelos, termos e exigencias (configuracao do dono) ───────────────────

  public record ModeloRequest(String nome, String segmento, Integer validadeMeses, Boolean ativo, List<Map<String, Object>> perguntas) {}

  public record DocumentoRequest(String tipo, String nome, Boolean ativo, String conteudo) {}

  public record ExigenciaRequest(UUID modeloAnamneseId, UUID termoId, Boolean exigeAutorizacaoFotos, Boolean fichaAssinada, String nivel,
      String exameExigido) {}

  @GetMapping("/anamnese/modelos")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> modelos() {
    return modelos.listarModelos();
  }

  @GetMapping("/anamnese/modelos/{id}")
  @RequiresPermission("appointment:read")
  public Map<String, Object> modelo(@PathVariable UUID id) {
    return modelos.modelo(id);
  }

  @PostMapping("/anamnese/modelos")
  public Map<String, Object> criarModelo(@RequestBody ModeloRequest r) {
    return modelos.criarModelo(r.nome(), r.segmento(), r.validadeMeses(), r.perguntas());
  }

  @PutMapping("/anamnese/modelos/{id}")
  public Map<String, Object> editarModelo(@PathVariable UUID id, @RequestBody ModeloRequest r) {
    return modelos.editarModelo(id, r.nome(), r.validadeMeses(), r.ativo());
  }

  @PostMapping("/anamnese/modelos/{id}/versoes")
  public Map<String, Object> novaVersaoDoModelo(@PathVariable UUID id, @RequestBody ModeloRequest r) {
    return modelos.salvarPerguntas(id, r.perguntas());
  }

  @PostMapping("/anamnese/modelos-prontos")
  public Map<String, Object> modelosProntos(@RequestBody(required = false) Map<String, List<String>> r) {
    return modelos.criarPadroes(r == null ? null : r.get("segmentos"));
  }

  @GetMapping("/documentos/modelos")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> documentos() {
    return modelos.listarDocumentos();
  }

  @PostMapping("/documentos/modelos")
  public Map<String, Object> criarDocumento(@RequestBody DocumentoRequest r) {
    return modelos.criarDocumento(r.tipo(), r.nome(), r.conteudo());
  }

  @PutMapping("/documentos/modelos/{id}")
  public Map<String, Object> editarDocumento(@PathVariable UUID id, @RequestBody DocumentoRequest r) {
    return modelos.editarDocumento(id, r.nome(), r.ativo());
  }

  @PostMapping("/documentos/modelos/{id}/versoes")
  public Map<String, Object> novaVersaoDoDocumento(@PathVariable UUID id, @RequestBody DocumentoRequest r) {
    return modelos.salvarTexto(id, r.conteudo());
  }

  @GetMapping("/anamnese/exigencias")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> exigencias() {
    return modelos.exigencias();
  }

  @PutMapping("/anamnese/exigencias/{serviceId}")
  public List<Map<String, Object>> salvarExigencia(@PathVariable UUID serviceId, @RequestBody ExigenciaRequest r) {
    return modelos.salvarExigencia(serviceId, r.modeloAnamneseId(), r.termoId(), Boolean.TRUE.equals(r.exigeAutorizacaoFotos()), Boolean.TRUE.equals(r.fichaAssinada()), r.nivel(), r.exameExigido());
  }

  // ─── Cliente ───────────────────────────────────────────────────────────────

  public record AlertaRequest(String tipo, String texto) {}

  public record LinkRequest(String proposito, UUID appointmentId, Boolean recuperar) {}

  @GetMapping("/clients/{id}/saude")
  @RequiresPermission("appointment:read")
  public Map<String, Object> resumo(@PathVariable UUID id) {
    return anamnese.resumo(id);
  }

  @GetMapping("/clients/{id}/anamneses/{modeloId}")
  @RequiresPermission("appointment:read")
  public Map<String, Object> anamnese(@PathVariable UUID id, @PathVariable UUID modeloId) {
    return anamnese.anamnese(id, modeloId);
  }

  @GetMapping("/clients/{id}/anamneses/versoes/{versaoId}")
  @RequiresPermission("appointment:read")
  public Map<String, Object> versao(@PathVariable UUID id, @PathVariable UUID versaoId) {
    return anamnese.versao(id, versaoId);
  }

  @PostMapping("/clients/{id}/anamneses/{modeloId}/versoes")
  @RequiresPermission("appointment:write")
  public Map<String, Object> salvarAnamnese(@PathVariable UUID id, @PathVariable UUID modeloId, @RequestBody Map<String, Object> respostas) {
    @SuppressWarnings("unchecked")
    Map<String, Object> r = respostas.get("respostas") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
    return anamnese.salvar(id, modeloId, r);
  }

  @PostMapping("/clients/{id}/alertas")
  @RequiresPermission("appointment:write")
  public List<Map<String, Object>> criarAlerta(@PathVariable UUID id, @RequestBody AlertaRequest r) {
    return anamnese.criarAlerta(id, r.tipo(), r.texto());
  }

  @PostMapping("/clients/{id}/alertas/{alertaId}/desativar")
  @RequiresPermission("appointment:write")
  public List<Map<String, Object>> desativarAlerta(@PathVariable UUID id, @PathVariable UUID alertaId) {
    return anamnese.desativarAlerta(id, alertaId);
  }

  @GetMapping("/clients/{id}/assinaturas")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> assinaturas(@PathVariable UUID id) {
    return assinaturas.historico(id);
  }

  @PostMapping("/clients/{id}/assinaturas")
  @RequiresPermission("appointment:write")
  public Map<String, Object> assinar(@PathVariable UUID id, @RequestBody AssinaturasDaCliente.Pedido pedido, HttpServletRequest req) {
    return assinaturas.registrarNoSalao(id, pedido, ip(req), req.getHeader("User-Agent"));
  }

  @GetMapping("/clients/{id}/assinaturas/{assinaturaId}/pdf")
  @RequiresPermission("appointment:read")
  public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @PathVariable UUID assinaturaId) {
    return arquivo(assinaturas.pdf(id, assinaturaId), MediaType.APPLICATION_PDF_VALUE, "documento-" + assinaturaId + ".pdf");
  }

  @GetMapping("/clients/{id}/assinaturas/{assinaturaId}/imagem")
  @RequiresPermission("appointment:read")
  public ResponseEntity<byte[]> imagemDaAssinatura(@PathVariable UUID id, @PathVariable UUID assinaturaId) {
    return arquivo(assinaturas.imagem(id, assinaturaId), MediaType.IMAGE_PNG_VALUE, null);
  }

  @GetMapping("/clients/{id}/linha-do-tempo")
  @RequiresPermission("appointment:read")
  public Map<String, Object> linhaDoTempo(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "60") int limite) {
    return linhaDoTempo.linhaDoTempo(id, limite);
  }

  @GetMapping("/clients/{id}/fotos")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> fotosDaCliente(@PathVariable UUID id) {
    return fotos.daCliente(id);
  }

  @GetMapping("/clients/{id}/links")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> links(@PathVariable UUID id) {
    return links.listar(id);
  }

  /** Convite para a area da cliente (com conta, devolve so o endereco da area; recuperar: convite para senha nova). */
  @PostMapping("/clients/{id}/links")
  @RequiresPermission("appointment:write")
  public Map<String, Object> criarLink(@PathVariable UUID id, @RequestBody LinkRequest r) {
    return contas.convidar(id, Boolean.TRUE.equals(r.recuperar()), r.appointmentId());
  }

  @GetMapping("/anamnese/area-da-cliente/busca")
  public Map<String, Object> aparecerNaBusca() {
    return agendaDaCliente.visibilidade();
  }

  @PutMapping("/anamnese/area-da-cliente/busca")
  public Map<String, Object> mudarAparecerNaBusca(@RequestBody Map<String, Boolean> r) {
    return agendaDaCliente.mudarVisibilidade(r == null || !Boolean.FALSE.equals(r.get("aparecerNaBusca")));
  }

  @GetMapping("/anamnese/relatorios/adesao")
  public Map<String, Object> adesao(@RequestParam("de") LocalDate de, @RequestParam("ate") LocalDate ate) {
    return relatorios.adesao(de, ate);
  }

  @GetMapping("/clients/{id}/conta")
  @RequiresPermission("appointment:read")
  public Map<String, Object> contaDaCliente(@PathVariable UUID id) {
    return contas.situacao(id);
  }

  @PostMapping("/clients/{id}/conta/desligar")
  @RequiresPermission("appointment:write")
  public Map<String, Object> desligarConta(@PathVariable UUID id, @RequestBody Map<String, String> r) {
    contas.desligar(id, r == null ? null : r.get("motivo"));
    return contas.situacao(id);
  }

  // ─── Exames ────────────────────────────────────────────────────────────────

  @GetMapping("/clients/{id}/exames")
  @RequiresPermission("appointment:read")
  public Map<String, Object> exames(@PathVariable UUID id) {
    return exames.doSalao(id);
  }

  @PostMapping(value = "/clients/{id}/exames", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresPermission("appointment:write")
  public Map<String, Object> enviarExame(@PathVariable UUID id, @RequestParam("arquivo") MultipartFile arquivo,
      @RequestParam("titulo") String titulo, @RequestParam(name = "tipo", required = false) String tipo,
      @RequestParam(name = "dataExame", required = false) String dataExame,
      @RequestParam(name = "observacao", required = false) String observacao,
      @RequestParam(name = "pedidoId", required = false) UUID pedidoId) throws java.io.IOException {
    return exames.enviarPeloSalao(id, arquivo.getBytes(),
        new ExamesDaCliente.Envio(titulo, tipo, dataExame, observacao, pedidoId, arquivo.getOriginalFilename()));
  }

  @GetMapping("/clients/{id}/exames/{exameId}/arquivo")
  @RequiresPermission("appointment:read")
  public ResponseEntity<byte[]> arquivoDoExame(@PathVariable UUID id, @PathVariable UUID exameId) {
    ArquivosProtegidos.Arquivo a = exames.arquivoParaOSalao(id, exameId);
    return arquivo(a.bytes(), a.contentType(), "exame-" + exameId + (a.contentType().contains("pdf") ? ".pdf" : ".jpg"));
  }

  @PostMapping("/clients/{id}/exames/{exameId}/arquivar")
  @RequiresPermission("appointment:write")
  public Map<String, Object> arquivarExame(@PathVariable UUID id, @PathVariable UUID exameId, @RequestBody Map<String, Boolean> r) {
    return exames.arquivar(id, exameId, r == null || !Boolean.FALSE.equals(r.get("arquivado")));
  }

  public record PedidoDeExameRequest(String descricao, UUID appointmentId) {}

  @PostMapping("/clients/{id}/exames/pedidos")
  @RequiresPermission("appointment:write")
  public Map<String, Object> pedirExame(@PathVariable UUID id, @RequestBody PedidoDeExameRequest r) {
    return exames.pedir(id, r.descricao(), r.appointmentId());
  }

  @PostMapping("/clients/{id}/exames/pedidos/{pedidoId}/cancelar")
  @RequiresPermission("appointment:write")
  public Map<String, Object> cancelarPedidoDeExame(@PathVariable UUID id, @PathVariable UUID pedidoId) {
    return exames.cancelarPedido(id, pedidoId);
  }

  @PostMapping("/clients/{id}/links/{linkId}/revogar")
  @RequiresPermission("appointment:write")
  public void revogarLink(@PathVariable UUID id, @PathVariable UUID linkId) {
    links.revogar(id, linkId);
  }

  // ─── Atendimento ───────────────────────────────────────────────────────────

  public record AdendoRequest(String tipo, String texto, String gravidade, String motivo, Boolean gerarAlerta) {}

  @GetMapping("/appointments/{id}/pendencias")
  @RequiresPermission("appointment:read")
  public Map<String, Object> pendencias(@PathVariable UUID id) {
    return anamnese.pendenciasDoAgendamento(id);
  }

  @GetMapping("/appointments/{id}/ficha")
  @RequiresPermission("appointment:read")
  public Map<String, Object> ficha(@PathVariable UUID id) {
    return fichas.obter(id);
  }

  @PostMapping("/appointments/{id}/ficha")
  @RequiresPermission("appointment:write")
  public Map<String, Object> abrirFicha(@PathVariable UUID id) {
    return fichas.abrir(id);
  }

  @PutMapping("/appointments/{id}/ficha")
  @RequiresPermission("appointment:write")
  public Map<String, Object> salvarFicha(@PathVariable UUID id, @RequestBody Map<String, Object> dados) {
    return fichas.salvar(id, dados);
  }

  @PostMapping("/appointments/{id}/ficha/adendos")
  @RequiresPermission("appointment:write")
  public Map<String, Object> adendo(@PathVariable UUID id, @RequestBody AdendoRequest r) {
    return fichas.adendo(id, r.tipo(), r.texto(), r.gravidade(), r.motivo(), Boolean.TRUE.equals(r.gerarAlerta()));
  }

  @GetMapping("/appointments/{id}/ficha/fotos")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> fotosDoAtendimento(@PathVariable UUID id) {
    return fotos.doAtendimento(id);
  }

  @PostMapping(value = "/appointments/{id}/ficha/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresPermission("appointment:write")
  public Map<String, Object> enviarFoto(@PathVariable UUID id, @RequestParam("arquivo") MultipartFile arquivo,
      @RequestParam("etapa") String etapa, @RequestParam(name = "secaoId", required = false) UUID secaoId,
      @RequestParam(name = "descricao", required = false) String descricao,
      @RequestParam(name = "origem", required = false) String origem) throws java.io.IOException {
    return fotos.enviar(id, arquivo.getBytes(), etapa, secaoId, descricao, origem);
  }

  @GetMapping("/fotos/{fotoId}/arquivo")
  @RequiresPermission("appointment:read")
  public ResponseEntity<byte[]> arquivoDaFoto(@PathVariable UUID fotoId) {
    FotosDaFicha.Arquivo a = fotos.arquivo(fotoId);
    return arquivo(a.bytes(), a.contentType(), null);
  }

  @PatchMapping("/fotos/{fotoId}/marketing")
  @RequiresPermission("appointment:write")
  public Map<String, Object> marketing(@PathVariable UUID fotoId, @RequestBody Map<String, Boolean> r) {
    return fotos.marketing(fotoId, Boolean.TRUE.equals(r.get("liberada")));
  }

  @DeleteMapping("/fotos/{fotoId}")
  @RequiresPermission("appointment:write")
  public void removerFoto(@PathVariable UUID fotoId, @RequestParam("motivo") String motivo) {
    fotos.remover(fotoId, motivo);
  }

  // ─── Relatorios ────────────────────────────────────────────────────────────

  @GetMapping("/anamnese/relatorios/pendencias")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> pendenciasDoDia(@RequestParam(name = "data", required = false) String data) {
    return relatorios.pendenciasDoDia(data == null ? LocalDate.now(AnamneseDaCliente.ZONA_BR) : LocalDate.parse(data));
  }

  @GetMapping("/anamnese/relatorios/intercorrencias")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> intercorrencias(@RequestParam("de") String de, @RequestParam("ate") String ate) {
    return relatorios.intercorrencias(LocalDate.parse(de), LocalDate.parse(ate));
  }

  @GetMapping("/anamnese/relatorios/marketing")
  @RequiresPermission("appointment:read")
  public List<Map<String, Object>> marketing() {
    return relatorios.marketing();
  }

  // ─── Apoio ─────────────────────────────────────────────────────────────────

  static ResponseEntity<byte[]> arquivo(byte[] bytes, String tipo, String nomeParaBaixar) {
    ResponseEntity.BodyBuilder b = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(tipo))
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox");
    if (nomeParaBaixar != null) b.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeParaBaixar + "\"");
    return b.body(bytes);
  }

  static String ip(HttpServletRequest req) {
    String f = req.getHeader("X-Forwarded-For");
    if (f != null && !f.isBlank()) return f.split(",")[0].trim();
    String r = req.getHeader("X-Real-IP");
    return r != null && !r.isBlank() ? r.trim() : req.getRemoteAddr();
  }
}
