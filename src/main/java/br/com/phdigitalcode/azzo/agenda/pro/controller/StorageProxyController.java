package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.Duration;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;

/**
 * Pentest 14/10/2026 (SEG-020): atende as URLs que {@link MinioStorageService#gerarUrlAssinadaLeitura}
 * ja emitia (logo do salao, arquivo de importacao) e que caiam em 404. Rota sem JWT — a logo vai num
 * {@code <img>} —, entao quem autoriza e a assinatura HMAC com prazo da propria URL.
 *
 * <p>Assinatura invalida, vencida ou arquivo inexistente respondem o mesmo 404, para nao revelar qual
 * chave existe. So imagem raster sai inline; o resto sai como anexo, com nosniff e CSP
 * {@code sandbox}, para um arquivo enviado nunca rodar como pagina na origem da API.
 */
@RestController
@RequestMapping("/api/v1/storage")
public class StorageProxyController {

  private static final Set<String> INLINE = Set.of("image/png", "image/jpeg", "image/webp");

  private final MinioStorageService minioStorageService;

  public StorageProxyController(MinioStorageService minioStorageService) {
    this.minioStorageService = minioStorageService;
  }

  @GetMapping("/proxy")
  public ResponseEntity<byte[]> proxy(
      @RequestParam(name = "key", required = false) String key,
      @RequestParam(name = "exp", required = false) Long exp,
      @RequestParam(name = "sig", required = false) String sig) {
    if (exp == null || !minioStorageService.assinaturaDeLeituraValida(key, exp, sig)) {
      throw naoEncontrado();
    }
    MinioStorageService.ArquivoDoProxy arquivo;
    try {
      arquivo = minioStorageService.baixarParaProxy(key);
    } catch (RuntimeException e) {
      throw naoEncontrado();
    }
    boolean inline = INLINE.contains(arquivo.contentType());
    return ResponseEntity.ok()
        .contentType(
            inline ? MediaType.parseMediaType(arquivo.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, inline ? "inline" : "attachment")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
        .body(arquivo.conteudo());
  }

  private static ApiClientErrorException naoEncontrado() {
    return new ApiClientErrorException("Arquivo nao encontrado", 404);
  }
}
