package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.DataDeletionStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MetaDtos.MetaAcknowledgeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.MetaPlatformService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Espelha {@code modules/meta/api/publicapi/MetaPlatformPublicResource.java}
 * ({@code @Path("/api/v1/public/meta")}). Rota publica ja coberta pela allowlist
 * {@code /api/v1/public/**} em {@code SecurityConfig} — nenhuma mudanca de seguranca necessaria.
 *
 * <p>{@code oauthCallback} monta HTML estatico e nao tem nenhuma regra de negocio nem persistencia
 * — por isso fica so no controller (sem contraparte no {@code MetaPlatformService}), diferente dos
 * outros tres endpoints que delegam para o service.
 */
@RestController
@RequestMapping("/api/v1/public/meta")
public class MetaPlatformPublicController {

  private final MetaPlatformService metaPlatformService;

  public MetaPlatformPublicController(MetaPlatformService metaPlatformService) {
    this.metaPlatformService = metaPlatformService;
  }

  @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
  public String oauthCallback(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state,
      @RequestParam(name = "error", required = false) String error,
      @RequestParam(name = "error_description", required = false) String errorDescription) {
    String status = isBlank(error) ? "Callback recebido com sucesso." : "Callback retornou erro da Meta.";
    String details = isBlank(error)
        ? "<p>Voce pode fechar esta janela e voltar para o sistema.</p>"
        : "<p>Erro: " + escapeHtml(error) + "</p><p>Descricao: " + escapeHtml(nullToEmpty(errorDescription)) + "</p>";

    return """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Meta OAuth Callback</title>
          <style>
            body { font-family: Arial, sans-serif; padding: 32px; background: #f6f7f9; color: #1d2129; }
            main { max-width: 680px; margin: 0 auto; background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.08); }
            code { background: #f0f2f5; padding: 2px 6px; border-radius: 6px; }
          </style>
        </head>
        <body>
          <main>
            <h1>Meta OAuth Callback</h1>
            <p>%s</p>
            %s
            <p><strong>code:</strong> <code>%s</code></p>
            <p><strong>state:</strong> <code>%s</code></p>
          </main>
        </body>
        </html>
        """
        .formatted(
            escapeHtml(status),
            details,
            escapeHtml(nullToEmpty(code)),
            escapeHtml(nullToEmpty(state)));
  }

  @PostMapping(
      value = "/deauthorize",
      consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.TEXT_PLAIN_VALUE},
      produces = MediaType.APPLICATION_JSON_VALUE)
  public MetaAcknowledgeResponse deauthorize(@RequestBody(required = false) String body) {
    return metaPlatformService.deauthorize(body);
  }

  @PostMapping(
      value = "/data-deletion",
      consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.TEXT_PLAIN_VALUE},
      produces = MediaType.APPLICATION_JSON_VALUE)
  public DataDeletionResponse dataDeletion(@RequestBody(required = false) String body, HttpServletRequest request) {
    return metaPlatformService.dataDeletion(body, buildAbsoluteUrl(request, "api/v1/public/meta/data-deletion/status/"));
  }

  @GetMapping("/data-deletion/status/{confirmationCode}")
  public DataDeletionStatusResponse dataDeletionStatus(@PathVariable("confirmationCode") String confirmationCode) {
    return metaPlatformService.dataDeletionStatus(confirmationCode);
  }

  private String buildAbsoluteUrl(HttpServletRequest request, String path) {
    StringBuilder base = new StringBuilder();
    base.append(request.getScheme()).append("://").append(request.getServerName());
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
        || ("https".equals(request.getScheme()) && port == 443);
    if (!defaultPort) {
      base.append(':').append(port);
    }
    base.append('/');
    return base + path;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String escapeHtml(String value) {
    return nullToEmpty(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
