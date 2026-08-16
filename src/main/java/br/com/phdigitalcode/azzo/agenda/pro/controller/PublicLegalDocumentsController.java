package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicLegalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.service.TermsService;

/**
 * Espelha {@code modules/audit/api/publicapi/PublicLegalDocumentsResource.java}
 * ({@code @Path("/api/v1/public/legal")}, sem autenticacao). A rota ja cai na allowlist
 * {@code /api/v1/public/**} de {@code SecurityConfig} — nenhuma alteracao de seguranca necessaria
 * aqui.
 *
 * <p>{@code submeterSolicitacaoTitular} apenas registra a solicitacao em log estruturado (nunca
 * grava PII em texto claro — so hash) e devolve um protocolo, exatamente como o original: a
 * persistencia de verdade fica a cargo de {@code ServicoLgpdTitular}, do modulo {@code lgpd}
 * (ainda nao portado nesta sessao).
 */
@RestController
@RequestMapping("/api/v1/public/legal")
public class PublicLegalDocumentsController {

  private static final Logger LOG = LoggerFactory.getLogger(PublicLegalDocumentsController.class);
  private static final String LGPD_CONFIG_ERROR_MESSAGE =
      "LGPD contact configuration missing. Configure LGPD_CONTACT_EMAIL, LGPD_CONTACT_CHANNEL and LGPD_CONTACT_RESPONSE_SLA.";
  private static final String PLACEHOLDER_EMAIL_CHANNEL = "[PREENCHER EMAIL/CANAL]";
  private static final String PLACEHOLDER_GENERIC = "[PREENCHER]";
  private static final String PLACEHOLDER_CANAL_DADOS = "Canal de privacidade e dados pessoais: [PREENCHER]";
  private static final String PLACEHOLDER_CANAL_PRIVACIDADE = "Canal de privacidade: [PREENCHER]";

  private final TermsService termsService;

  @Value("${app.lgpd.contact.email:__unset__}")
  private String lgpdContactEmail;

  @Value("${app.lgpd.contact.channel:__unset__}")
  private String lgpdContactChannel;

  @Value("${app.lgpd.contact.response-sla:confirmacao ate 2 dias uteis; resposta inicial ate 15 dias corridos; conclusao ate 30 dias corridos}")
  private String lgpdResponseSla;

  public PublicLegalDocumentsController(TermsService termsService) {
    this.termsService = termsService;
  }

  @GetMapping
  public PublicLegalDtos.PublicLegalResponse currentDocuments() {
    PublicLegalDtos.PublicLegalResponse response = new PublicLegalDtos.PublicLegalResponse();
    response.termsOfUse = termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE)
        .map(this::toResponse)
        .orElse(null);
    response.privacyPolicy = termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY)
        .map(this::toResponse)
        .orElse(null);
    response.lgpdContact = toLgpdContactResponse();
    return response;
  }

  @GetMapping("/terms-of-use")
  public PublicLegalDtos.LegalDocumentResponse termsOfUse() {
    TermsVersion version = termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Termos de uso nao publicados"));
    return toResponse(version);
  }

  @GetMapping("/privacy-policy")
  public PublicLegalDtos.LegalDocumentResponse privacyPolicy() {
    TermsVersion version = termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Politica de privacidade nao publicada"));
    return toResponse(version);
  }

  @GetMapping("/contact")
  public PublicLegalDtos.LgpdContactResponse lgpdContact() {
    return toLgpdContactResponse();
  }

  /**
   * GET /api/v1/public/legal/lgpd-contact
   * Retorna informacoes de contato LGPD e lista de direitos do titular (art. 18 LGPD).
   * Endpoint publico — sem autenticacao.
   */
  @GetMapping("/lgpd-contact")
  public ResponseEntity<Map<String, Object>> obterContatoLgpdDetalhado() {
    PublicLegalDtos.LgpdContactResponse contact = toLgpdContactResponse();
    Map<String, Object> response = Map.of(
        "contactEmail", contact.email,
        "contactChannel", contact.channel,
        "responseSla", contact.responseSla,
        "rights", List.of(
            "Confirmacao da existencia de tratamento",
            "Acesso aos dados",
            "Correcao de dados incompletos, inexatos ou desatualizados",
            "Anonimizacao, bloqueio ou eliminacao de dados desnecessarios",
            "Portabilidade dos dados",
            "Eliminacao dos dados tratados com consentimento",
            "Informacao sobre compartilhamento com terceiros",
            "Revogacao do consentimento",
            "Peticao a ANPD"));
    return ResponseEntity.ok(response);
  }

  /**
   * POST /api/v1/public/legal/data-rights-request
   * Submete solicitacao de exercicio de direitos do titular (art. 18 LGPD).
   * Endpoint publico — sem autenticacao.
   * A solicitacao e persistida pelo ServicoLgpdTitular e pode ser gerenciada pelo DPO
   * em /api/v1/lgpd/requests (autenticado, role OWNER) — modulo lgpd ainda nao portado.
   */
  @PostMapping("/data-rights-request")
  public ResponseEntity<Map<String, Object>> submeterSolicitacaoTitular(@RequestBody(required = false) DataRightsRequest request) {
    if (request == null
        || request.requestType == null || request.requestType.isBlank()
        || request.requesterEmail == null || request.requesterEmail.isBlank()
        || request.requesterName == null || request.requesterName.isBlank()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "requestType, requesterName e requesterEmail sao obrigatorios"));
    }
    String protocol = "LGPD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    // Registra a solicitacao em log estruturado para processamento pelo DPO.
    // Email nunca e logado diretamente — apenas hash para correlacao sem exposicao de PII.
    LOG.info(
        "LGPD_PUBLIC_REQUEST protocol={} type={} name_hash={} email_hash={}",
        protocol,
        request.requestType.trim().toUpperCase(),
        Integer.toHexString(request.requesterName.hashCode()),
        Integer.toHexString(request.requesterEmail.hashCode()));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(Map.of(
            "message", "Sua solicitacao foi recebida. Responderemos conforme o SLA informado.",
            "protocol", protocol));
  }

  private PublicLegalDtos.LegalDocumentResponse toResponse(TermsVersion version) {
    PublicLegalDtos.LegalDocumentResponse response = new PublicLegalDtos.LegalDocumentResponse();
    response.documentType = version.getDocumentType();
    response.version = version.getVersion();
    response.title = version.getTitle();
    response.content = applyLgpdContactPlaceholders(version.getContent());
    response.contentHash = version.getContentHash();
    response.createdAt = version.getCreatedAt() != null ? version.getCreatedAt().toString() : null;
    return response;
  }

  private PublicLegalDtos.LgpdContactResponse toLgpdContactResponse() {
    PublicLegalDtos.LgpdContactResponse response = new PublicLegalDtos.LgpdContactResponse();
    response.email = requiredConfiguredValue(lgpdContactEmail);
    response.channel = requiredConfiguredValue(lgpdContactChannel);
    response.responseSla = requiredConfiguredValue(lgpdResponseSla);
    return response;
  }

  private String normalizeConfiguredValue(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if (trimmed.isBlank()) return null;
    if ("__unset__".equalsIgnoreCase(trimmed)) return null;
    return trimmed;
  }

  private String requiredConfiguredValue(String value) {
    String normalized = normalizeConfiguredValue(value);
    if (normalized == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, LGPD_CONFIG_ERROR_MESSAGE);
    }
    return normalized;
  }

  private String applyLgpdContactPlaceholders(String content) {
    if (content == null || content.isBlank()) return content;
    String email = normalizeConfiguredValue(lgpdContactEmail);
    String channel = normalizeConfiguredValue(lgpdContactChannel);
    String output = content;

    if (email != null) {
      output = output.replace(PLACEHOLDER_EMAIL_CHANNEL, email);
    }
    if (channel != null) {
      output = output
          .replace(PLACEHOLDER_CANAL_DADOS, "Canal de privacidade e dados pessoais: " + channel)
          .replace(PLACEHOLDER_CANAL_PRIVACIDADE, "Canal de privacidade: " + channel);
      if (email == null) {
        output = output.replace(PLACEHOLDER_GENERIC, channel);
      }
    }
    return output;
  }

  public static class DataRightsRequest {
    public String requestType;   // ACCESS, CORRECTION, DELETION, PORTABILITY, REVOKE_CONSENT, INFO
    public String requesterName;
    public String requesterEmail;
    public String requesterDocument;
    public String description;
  }
}
