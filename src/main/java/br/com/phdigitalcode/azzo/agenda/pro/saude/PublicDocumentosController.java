package br.com.phdigitalcode.azzo.agenda.pro.saude;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Link pessoal da cliente, sem login (plano, etapa 3). Tudo depois da abertura pede a sessao que a conferencia dos 4
 * ultimos digitos do telefone devolve (cabecalho X-Sessao-Documentos).
 */
@RestController
@RequestMapping("/api/v1/public/documentos/{token}")
public class PublicDocumentosController {

  static final String SESSAO = "X-Sessao-Documentos";

  private final LinksDaCliente links;

  public PublicDocumentosController(LinksDaCliente links) {
    this.links = links;
  }

  @GetMapping
  public Map<String, Object> abertura(@PathVariable String token) {
    return links.abertura(token);
  }

  @PostMapping("/conferir")
  public Map<String, Object> conferir(@PathVariable String token, @RequestBody Map<String, String> r) {
    return links.conferir(token, r == null ? null : r.get("digitos"));
  }

  @GetMapping("/conteudo")
  public Map<String, Object> conteudo(@PathVariable String token, @RequestHeader(SESSAO) String sessao) {
    return links.conteudo(token, sessao);
  }

  @PostMapping("/anamnese/{modeloId}")
  public Map<String, Object> anamnese(@PathVariable String token, @PathVariable UUID modeloId,
      @RequestHeader(SESSAO) String sessao, @RequestBody Map<String, Object> r) {
    @SuppressWarnings("unchecked")
    Map<String, Object> respostas = r.get("respostas") instanceof Map<?, ?> x ? (Map<String, Object>) x : Map.of();
    return links.salvarAnamnese(token, sessao, modeloId, respostas);
  }

  @PostMapping("/assinar")
  public Map<String, Object> assinar(@PathVariable String token, @RequestHeader(SESSAO) String sessao,
      @RequestBody AssinaturasDaCliente.Pedido pedido, HttpServletRequest req) {
    return links.assinar(token, sessao, pedido, SaudeController.ip(req), req.getHeader("User-Agent"));
  }

  @GetMapping("/documentos/{assinaturaId}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable String token, @PathVariable UUID assinaturaId,
      @RequestHeader(SESSAO) String sessao) {
    return SaudeController.arquivo(links.pdf(token, sessao, assinaturaId), MediaType.APPLICATION_PDF_VALUE,
        "documento-" + assinaturaId + ".pdf");
  }
}
