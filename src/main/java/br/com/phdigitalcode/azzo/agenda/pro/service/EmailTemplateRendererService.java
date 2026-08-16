package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailTemplateConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailTemplateConfigRepository;

/**
 * Espelha {@code modules/email/application/EmailTemplateRendererService.java}.
 *
 * <p>Portado junto com {@code settings} porque os tres endpoints
 * {@code /api/v1/settings/email-templates} dependem de {@link #definitions()},
 * {@link #definition(EmailTemplateType)} e {@link #sanitizeHtml(String)}. O restante do modulo
 * {@code email} (fila, worker, envio SMTP) continua fora — ver
 * {@code integration/EmailJobService}.
 *
 * <p>{@link #renderPasswordReset(String, String)} e {@link #preview} nao tem chamador hoje (quem
 * chamaria e o {@code EmailJobProcessor}, do modulo nao migrado), mas foram mantidos porque sao a
 * razao de existir do renderer e o contrato precisa estar pronto quando {@code email} for portado.
 */
@Service
public class EmailTemplateRendererService {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
          .withZone(ZoneId.of("America/Sao_Paulo"));

  private final EmailTemplateConfigRepository emailTemplateConfigRepository;
  private final String globalFrom;
  private final String productName;

  public EmailTemplateRendererService(
      EmailTemplateConfigRepository emailTemplateConfigRepository,
      @Value("${app.mail.from:no-reply@azzoholding.com.br}") String globalFrom,
      @Value("${app.email.product-name:Azzo Agenda Pro}") String productName) {
    this.emailTemplateConfigRepository = emailTemplateConfigRepository;
    this.globalFrom = globalFrom;
    this.productName = productName;
  }

  public List<TemplateDefinition> definitions() {
    return List.of(passwordResetDefinition());
  }

  public TemplateDefinition definition(EmailTemplateType type) {
    return switch (type) {
      case PASSWORD_RESET -> passwordResetDefinition();
    };
  }

  public RenderedTemplate renderPasswordReset(String userName, String resetLink) {
    Map<String, String> variables = sampleVariables();
    variables.put("userName", normalize(userName, "usuario"));
    variables.put("resetLink", normalize(resetLink, "#"));
    return render(EmailTemplateType.PASSWORD_RESET, variables);
  }

  public RenderedTemplate preview(EmailTemplateType type, TemplateInput input) {
    TemplateDefinition definition = definition(type);
    Map<String, String> variables = sampleVariables();
    return render(definition, input, variables);
  }

  public RenderedTemplate render(EmailTemplateType type, Map<String, String> variables) {
    TemplateDefinition definition = definition(type);
    EmailTemplateConfig config =
        emailTemplateConfigRepository.findActiveGlobalByType(type).orElse(null);
    TemplateInput input =
        config == null
            ? new TemplateInput(
                null, null, null, definition.defaultSubjectTemplate(), definition.defaultHtmlTemplate())
            : new TemplateInput(
                config.getFromEmail(),
                config.getFromName(),
                config.getReplyTo(),
                config.getSubjectTemplate(),
                config.getHtmlTemplate());
    return render(definition, input, variables);
  }

  public Map<String, String> sampleVariables() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("userName", "Phelipp Nascimento Damasceno");
    variables.put("resetLink", "https://www.azzoholding.com.br/redefinir-senha?token=abc123");
    variables.put("productName", productName);
    variables.put("supportEmail", normalize(globalFrom, "support@azzoholding.com.br"));
    variables.put("requestDateTime", DATE_FORMATTER.format(Instant.now()));
    return variables;
  }

  public String sanitizeHtml(String html) {
    if (html == null) return "";
    String sanitized =
        html.replaceAll("(?is)<script.*?>.*?</script>", "")
            .replaceAll("(?i)on[a-z]+\\s*=\\s*\"[^\"]*\"", "")
            .replaceAll("(?i)on[a-z]+\\s*=\\s*'[^']*'", "")
            .replaceAll("(?i)javascript:", "");
    return sanitized.trim();
  }

  private RenderedTemplate render(
      TemplateDefinition definition, TemplateInput input, Map<String, String> variables) {
    String subjectTemplate = normalize(input.subjectTemplate(), definition.defaultSubjectTemplate());
    String htmlTemplate =
        sanitizeHtml(normalize(input.htmlTemplate(), definition.defaultHtmlTemplate()));
    String renderedSubject = applyVariables(subjectTemplate, variables);
    String renderedHtml = applyVariables(htmlTemplate, variables);
    String resolvedFromEmail = normalize(input.fromEmail(), null);
    String resolvedFromName = normalize(input.fromName(), productName);
    String resolvedReplyTo = normalize(input.replyTo(), null);
    return new RenderedTemplate(
        definition.type(),
        definition.label(),
        renderedSubject,
        renderedHtml,
        resolvedFromEmail,
        resolvedFromName,
        resolvedReplyTo,
        definition.placeholders(),
        variables);
  }

  private String applyVariables(String template, Map<String, String> variables) {
    String result = template == null ? "" : template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      result = result.replace("{{" + entry.getKey() + "}}", normalize(entry.getValue(), ""));
    }
    return result;
  }

  private String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isBlank() ? fallback : trimmed;
  }

  private TemplateDefinition passwordResetDefinition() {
    return new TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET,
        EmailTemplateType.PASSWORD_RESET.label(),
        """
        Redefinicao de senha - {{productName}}
        """
            .trim(),
        """
        <div style="margin:0;padding:32px;background:#f4efe8;font-family:Georgia,'Times New Roman',serif;color:#241f1a;">
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;margin:0 auto;background:#fffaf2;border:1px solid #e6dccd;">
            <tr>
              <td style="padding:36px 40px 20px 40px;background:#1f3a5f;color:#fffaf2;">
                <div style="font-size:12px;letter-spacing:0.24em;text-transform:uppercase;opacity:0.85;">{{productName}}</div>
                <h1 style="margin:12px 0 0 0;font-size:30px;line-height:1.2;font-weight:700;">Redefinicao de senha</h1>
              </td>
            </tr>
            <tr>
              <td style="padding:32px 40px;">
                <p style="margin:0 0 16px 0;font-size:17px;line-height:1.7;">Ola <strong>{{userName}}</strong>,</p>
                <p style="margin:0 0 16px 0;font-size:16px;line-height:1.7;">
                  Recebemos uma solicitacao para redefinir a sua senha em <strong>{{requestDateTime}}</strong>.
                </p>
                <p style="margin:0 0 28px 0;font-size:16px;line-height:1.7;">
                  Para continuar, clique no botao abaixo e cadastre uma nova senha.
                </p>
                <p style="margin:0 0 28px 0;">
                  <a href="{{resetLink}}" style="display:inline-block;background:#d88a32;color:#fffaf2;text-decoration:none;padding:14px 24px;font-size:15px;font-weight:700;border-radius:999px;">
                    Criar nova senha
                  </a>
                </p>
                <p style="margin:0 0 12px 0;font-size:14px;line-height:1.7;color:#4f4337;">
                  Se o botao nao abrir, copie e cole este link no navegador:
                </p>
                <p style="margin:0 0 28px 0;font-size:13px;line-height:1.7;word-break:break-word;color:#1f3a5f;">
                  {{resetLink}}
                </p>
                <div style="padding-top:20px;border-top:1px solid #e6dccd;">
                  <p style="margin:0 0 8px 0;font-size:14px;line-height:1.7;color:#4f4337;">
                    Se voce nao solicitou essa alteracao, ignore este e-mail.
                  </p>
                  <p style="margin:0;font-size:14px;line-height:1.7;color:#4f4337;">
                    Dvidas? Fale com nosso suporte em <strong>{{supportEmail}}</strong>.
                  </p>
                </div>
              </td>
            </tr>
          </table>
        </div>
        """
            .trim(),
        List.of(
            "{{userName}}",
            "{{resetLink}}",
            "{{productName}}",
            "{{supportEmail}}",
            "{{requestDateTime}}"));
  }

  public record TemplateDefinition(
      EmailTemplateType type,
      String label,
      String defaultSubjectTemplate,
      String defaultHtmlTemplate,
      List<String> placeholders) {}

  public record TemplateInput(
      String fromEmail, String fromName, String replyTo, String subjectTemplate, String htmlTemplate) {}

  public record RenderedTemplate(
      EmailTemplateType type,
      String label,
      String subject,
      String html,
      String fromEmail,
      String fromName,
      String replyTo,
      List<String> placeholders,
      Map<String, String> sampleValues) {}
}
