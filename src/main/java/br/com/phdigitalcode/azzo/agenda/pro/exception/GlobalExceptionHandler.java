package br.com.phdigitalcode.azzo.agenda.pro.exception;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Equivalente Spring de {@code modules/common/api/ApiExceptionMapper.java}
 * ({@code ExceptionMapper<RuntimeException>} do JAX-RS -> {@code @RestControllerAdvice} +
 * {@code @ExceptionHandler}).
 *
 * <p>Preserva EXATAMENTE (ver risco 1 e 9 do inventario):
 * <ul>
 *   <li>a ordem de precedencia de excecoes: ConstraintViolationException/MethodArgumentNotValidException
 *       &gt; AsaasException &gt; FiscalProviderException &gt; CnpjApiIndisponivelException &gt;
 *       AppointmentConflictException &gt; erro HTTP explicito (ApiClientErrorException, equivalente
 *       ao {@code WebApplicationException}/{@code ClientErrorException} do JAX-RS) &gt; fallback
 *       generico;</li>
 *   <li>o mapa de ~40 codigos funcionais fiscais/NFS-e (regex {@code [A-Z0-9_]{6,}});</li>
 *   <li>a sanitizacao de mensagens sensiveis (certificado, senha, token, XML) antes de responder;</li>
 *   <li>o registro de auditoria de erro (fire-and-forget, nao bloqueia a resposta);</li>
 *   <li>o contrato JSON de erro ({@link ErrorResponse}: code/message/details/path/timestamp).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final Map<String, String> FUNCTIONAL_CODE_MESSAGES = Map.ofEntries(
      Map.entry("NFSE_CONFIG_MISSING_MUNICIPIO", "Configuracao NFS-e incompleta: municipio obrigatorio."),
      Map.entry("NFSE_CONFIG_MISSING_PROVEDOR", "Configuracao NFS-e incompleta: provedor obrigatorio."),
      Map.entry("NFSE_CONFIG_MISSING_SERIE_RPS", "Configuracao NFS-e incompleta: serie RPS obrigatoria."),
      Map.entry("NFSE_CONFIG_MISSING_ITEM_LISTA_SERVICO", "Configuracao NFS-e incompleta: item da lista de servico obrigatorio."),
      Map.entry("NFSE_CONFIG_MISSING_NATUREZA_OPERACAO", "Configuracao NFS-e incompleta: natureza de operacao obrigatoria."),
      Map.entry("NFSE_CONFIG_MISSING_ALIQUOTA_ISS", "Configuracao NFS-e incompleta: aliquota de ISS obrigatoria."),
      Map.entry("NFSE_CUSTOMER_EXTERIOR_REQUIRES_COUNTRY", "Tomador do exterior requer codigo de pais."),
      Map.entry("NFSE_CUSTOMER_DOCUMENT_REQUIRED", "Documento do tomador obrigatorio para CPF/CNPJ."),
      Map.entry("NFSE_PROVIDER_MISSING", "Provedor NFS-e obrigatorio."),
      Map.entry("NFSE_PROVIDER_NOT_SUPPORTED", "Provedor NFS-e nao suportado para a configuracao informada."),
      Map.entry("NFSE_TOMADOR_LOOKUP_DISABLED", "Consulta automatica de tomador por CNPJ esta desabilitada."),
      Map.entry("NFSE_TOMADOR_CNPJ_INVALID", "CNPJ do tomador invalido para consulta automatica."),
      Map.entry("NFSE_TOMADOR_NOT_FOUND", "Tomador nao encontrado para o CNPJ informado."),
      Map.entry("NFSE_TOMADOR_LOOKUP_UNAVAILABLE", "Consulta de CNPJ temporariamente indisponivel. Tente novamente."),
      Map.entry("NFSE_PROVIDER_CAP_MISSING_MUNICIPIO", "Capacidade de provedor requer municipio."),
      Map.entry("NFSE_PROVIDER_CAP_MISSING_PROVEDOR", "Capacidade de provedor requer nome do provedor."),
      Map.entry("NFSE_PROVIDER_CAP_MISSING_LAYOUT_VERSION", "Capacidade de provedor requer versao de layout."),
      Map.entry("NFSE_CERTIFICATE_PASSWORD_REQUIRED", "Informe a senha do certificado para continuar."),
      Map.entry("NFSE_CERTIFICATE_ACTIVE_MISSING", "Nenhum certificado fiscal ativo esta configurado."),
      Map.entry("NFSE_CERTIFICATE_PASSWORD_INVALID", "Nao foi possivel validar o certificado com os dados informados."),
      Map.entry("NFSE_PROVIDER_ABRASF_WS_URL_MISSING", "URL do webservice ABRASF nao configurada para o ambiente da nota."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_AUTHORIZE_URL_MISSING", "URL de autorizacao da Receita Nacional nao configurada para o ambiente da nota."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_URL_MISSING", "URL de cancelamento da Receita Nacional nao configurada para o ambiente da nota."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_URL_MISSING", "URL de consulta da Receita Nacional nao configurada para o ambiente da nota."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_SIGNED_XML_REQUIRED", "XML assinado da NFS-e obrigatorio para envio ao provider nacional."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_LAYOUT_NOT_SUPPORTED", "O leiaute nacional da Receita ainda nao foi implementado."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_PRODUCTION_DISABLED", "Envio para producao da Receita Nacional esta bloqueado por configuracao de seguranca."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_NOT_IMPLEMENTED", "Integracao operacional da Receita Nacional ainda nao foi concluida para esta operacao."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_CONFIG_MISSING", "Configuracao NFS-e nacional nao encontrada para o ambiente informado."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_TAX_CONFIG_MISSING", "Configuracao fiscal do emissor nao encontrada."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_ITEMS_REQUIRED", "Ao menos um item de servico e obrigatorio para a NFS-e nacional."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_APP_VERSION_REQUIRED", "Informe a versao do aplicativo na configuracao da NFS-e nacional."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_CTRIBNAC_REQUIRED", "Informe o codigo de tributacao nacional do servico."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_OP_SIMP_NAC_REQUIRED", "Informe a situacao do prestador perante o Simples Nacional."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_REG_ESP_TRIB_REQUIRED", "Informe o regime especial de tributacao do prestador."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_PRESTADOR_CNPJ_REQUIRED", "Informe o CNPJ do emissor na configuracao fiscal."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_PRESTADOR_NAME_REQUIRED", "Informe a razao social do emissor na configuracao fiscal."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_ACCESS_KEY_MISSING", "A chave de acesso da NFS-e nacional ainda nao esta disponivel para esta operacao."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_CANCEL_REASON_REQUIRED", "Informe o motivo do cancelamento da NFS-e nacional."),
      Map.entry("NFSE_PROVIDER_SEFIN_NACIONAL_QUERY_METHOD_NOT_SUPPORTED", "O metodo configurado para consulta da Receita Nacional nao e suportado por esta versao."),
      Map.entry("NFSE_MUNICIPIO_DIVERGENTE_DA_CONFIG", "O municipio informado na NFS-e diverge do municipio configurado para este ambiente."),
      Map.entry("NFSE_XML_PRESTADOR_CNPJ_INVALIDO", "CNPJ do emitente nao configurado ou invalido. Acesse Configuracoes > Impostos e informe o CNPJ antes de emitir NFS-e."),
      Map.entry("NFSE_XML_PRESTADOR_IM_AUSENTE", "Inscricao Municipal do emitente nao configurada. Acesse Configuracoes > Impostos e informe a Inscricao Municipal antes de emitir NFS-e."));

  private final AuditService auditService;
  private final ContextoTenant contextoTenant;

  public GlobalExceptionHandler(AuditService auditService, ContextoTenant contextoTenant) {
    this.auditService = auditService;
    this.contextoTenant = contextoTenant;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    List<String> details = ex.getConstraintViolations().stream().map(this::formatViolation).toList();
    ErrorResponse payload = new ErrorResponse("VALIDATION_ERROR", "Falha de validacao", details, resolvePath());
    return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), payload, ex);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(fieldError -> fieldError.getField() + ": " + safeMessage(fieldError.getDefaultMessage(), "invalido"))
        .toList();
    ErrorResponse payload = new ErrorResponse("VALIDATION_ERROR", "Falha de validacao", details, resolvePath());
    return buildErrorResponse(HttpStatus.BAD_REQUEST.value(), payload, ex);
  }

  @ExceptionHandler(AsaasException.class)
  public ResponseEntity<ErrorResponse> handleAsaas(AsaasException ex) {
    ErrorResponse payload = new ErrorResponse(
        "REGISTRATION_ERROR",
        "Nao foi possivel concluir o cadastro. Verifique os dados informados e tente novamente.",
        null,
        resolvePath());
    return buildErrorResponse(ex.getStatusCode(), payload, ex);
  }

  @ExceptionHandler(FiscalProviderException.class)
  public ResponseEntity<ErrorResponse> handleFiscalProvider(FiscalProviderException ex) {
    ErrorResponse payload = new ErrorResponse(
        "FISCAL_PROVIDER_ERROR", mensagemPublicaFiscal(ex.getStatusCode()), null, resolvePath());
    return buildErrorResponse(ex.getStatusCode(), payload, ex);
  }

  @ExceptionHandler(CnpjApiIndisponivelException.class)
  public ResponseEntity<ErrorResponse> handleCnpjIndisponivel(CnpjApiIndisponivelException ex) {
    ErrorResponse payload = new ErrorResponse(
        "CNPJ_API_INDISPONIVEL",
        safeMessage(ex.getMessage(), "Servico de consulta CNPJ temporariamente indisponivel. Tente novamente em instantes."),
        null,
        resolvePath());
    return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), payload, ex);
  }

  @ExceptionHandler(AppointmentConflictException.class)
  public ResponseEntity<ErrorResponse> handleAppointmentConflict(AppointmentConflictException ex) {
    ErrorResponse payload = new ErrorResponse(
        "APPOINTMENT_CONFLICT",
        safeMessage(ex.getMessage(), "Horario em conflito para o profissional."),
        ex.getDetails(),
        resolvePath());
    return buildErrorResponse(HttpStatus.CONFLICT.value(), payload, ex);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    ErrorResponse payload = new ErrorResponse("FORBIDDEN", "Acesso negado", null, resolvePath());
    return buildErrorResponse(HttpStatus.FORBIDDEN.value(), payload, ex);
  }

  @ExceptionHandler(ApiClientErrorException.class)
  public ResponseEntity<ErrorResponse> handleApiClientError(ApiClientErrorException ex) {
    int status = ex.getStatus();
    String message = status == 429
        ? "Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente novamente."
        : safeMessage(ex.getMessage(), "Erro na requisicao");
    ErrorResponse payload = new ErrorResponse(resolveCode(status), message, null, resolvePath());
    return buildErrorResponse(status, payload, ex);
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponse> handleGeneric(RuntimeException exception) {
    Throwable root = unwrap(exception);

    int status = 400;
    String msg = safeMessage(root.getMessage(), "Erro inesperado");
    msg = sanitizeForPublicResponse(msg);
    String functionalCode = null;

    boolean isUnexpected = !(root instanceof IllegalArgumentException) && !(root instanceof IllegalStateException);
    boolean hasNullMessage = root.getMessage() == null;

    if (isUnexpected || hasNullMessage) {
      msg = "Ocorreu um erro inesperado. Tente novamente ou contate o suporte.";
    }

    if (root instanceof IllegalArgumentException) {
      if (isFunctionalCode(msg)) {
        functionalCode = msg.trim().toUpperCase(Locale.ROOT);
        msg = resolveFunctionalMessage(functionalCode);
      } else {
        msg = appendCorrectiveAction(msg, "Revise os dados informados e tente novamente.");
      }
    }
    if (msg.toLowerCase(Locale.ROOT).contains("credenciais")) status = 401;
    if (status == 429) {
      msg = "Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente novamente.";
    }

    ErrorResponse payload = new ErrorResponse(
        functionalCode != null ? functionalCode : resolveCode(status), msg, null, resolvePath());
    return buildErrorResponse(status, payload, root);
  }

  private ResponseEntity<ErrorResponse> buildErrorResponse(int status, ErrorResponse payload, Throwable exception) {
    logRequestFailure(status, payload, exception);
    registrarErroGlobal(payload.path, payload.code, payload.message);
    return ResponseEntity.status(status).body(payload);
  }

  private String resolvePath() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) return null;
    HttpServletRequest request = attributes.getRequest();
    String raw = request.getRequestURI();
    if (raw == null || raw.isBlank()) return null;
    return raw.startsWith("/") ? raw : "/" + raw;
  }

  private String resolveMethod() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest().getMethod() : null;
  }

  private String resolveCode(int status) {
    HttpStatus mapped = HttpStatus.resolve(status);
    return mapped == null ? "HTTP_" + status : mapped.name();
  }

  private String resolveReason(int status) {
    HttpStatus mapped = HttpStatus.resolve(status);
    return mapped == null ? "HTTP " + status : mapped.getReasonPhrase();
  }

  private String safeMessage(String value, String fallback) {
    String resolved = value == null || value.isBlank() ? fallback : value;
    return sanitizeForPublicResponse(LogSanitizer.sanitizeLogMessage(resolved));
  }

  private String appendCorrectiveAction(String message, String actionHint) {
    String normalized = safeMessage(message, "Erro na requisicao");
    if (normalized.toLowerCase(Locale.ROOT).contains(actionHint.toLowerCase(Locale.ROOT))) return normalized;
    return normalized + " " + actionHint;
  }

  private String formatViolation(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
    String msg = safeMessage(violation.getMessage(), "invalido");
    if (path.isBlank()) return msg;
    return path + ": " + msg;
  }

  private String mensagemPublicaFiscal(int statusCode) {
    if (statusCode == 401 || statusCode == 403) return "Falha de autorizacao na integracao fiscal";
    if (statusCode == 408 || statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
      return "Servico fiscal temporariamente indisponivel. Tente novamente em instantes.";
    }
    return "Erro de integracao fiscal";
  }

  private String sanitizeForPublicResponse(String input) {
    if (input == null || input.isBlank()) return input;
    if (looksLikeFunctionalCode(input)) return input;
    String lower = input.toLowerCase(Locale.ROOT);
    if (lower.contains("<xml")
        || lower.contains("<?xml")
        || lower.contains("certificate")
        || lower.contains("certificado")
        || lower.contains("senha")
        || lower.contains("password")
        || lower.contains("authorization")
        || lower.contains("bearer ")
        || lower.contains("token")) {
      return "Erro na requisicao";
    }
    return input;
  }

  private boolean looksLikeFunctionalCode(String value) {
    if (value == null || value.isBlank()) return false;
    return value.trim().matches("[A-Z0-9_]{6,}");
  }

  private boolean isFunctionalCode(String value) {
    if (value == null || value.isBlank()) return false;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return normalized.matches("[A-Z0-9_]{6,}") && FUNCTIONAL_CODE_MESSAGES.containsKey(normalized);
  }

  private String resolveFunctionalMessage(String functionalCode) {
    return FUNCTIONAL_CODE_MESSAGES.getOrDefault(functionalCode, "Erro funcional de validacao.");
  }

  private Throwable unwrap(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private String resolveAppFrame(Throwable t) {
    String pkg = "br.com.phdigitalcode";
    for (StackTraceElement frame : t.getStackTrace()) {
      if (frame.getClassName().startsWith(pkg)) {
        return frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
            + "." + frame.getMethodName() + ":" + frame.getLineNumber();
      }
    }
    StackTraceElement[] frames = t.getStackTrace();
    if (frames.length > 0) return frames[0].toString();
    return t.getClass().getSimpleName();
  }

  private void logRequestFailure(int status, ErrorResponse payload, Throwable exception) {
    String context = CorrelatedLogging.context(
        "API request failed",
        "method", resolveMethod(),
        "path", payload != null ? payload.path : resolvePath(),
        "status", status,
        "reason", resolveReason(status),
        "code", payload != null ? payload.code : null,
        "tenantId", safeTenantId(),
        "userId", safeUserId(),
        "message", payload != null ? payload.message : null,
        "errorType", exception != null ? exception.getClass().getSimpleName() : null,
        "root", CorrelatedLogging.throwableSummary(exception),
        "origin", exception != null ? resolveAppFrame(exception) : null);
    if (status >= 500) {
      LOG.error(context, exception);
      return;
    }
    LOG.warn(context);
  }

  private String safeTenantId() {
    try {
      return contextoTenant.obterTenantIdOuFalhar().toString();
    } catch (Exception ignored) {
      return null;
    }
  }

  private String safeUserId() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
        return jwtPrincipal.userId() != null ? jwtPrincipal.userId().toString() : null;
      }
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void registrarErroGlobal(String path, String errorCode, String message) {
    try {
      UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.SYSTEM;
      command.action = "API_EXCEPTION";
      command.entityType = "API_PATH";
      command.entityId = path;
      command.errorCode = errorCode;
      command.errorMessage = LogSanitizer.sanitizeLogMessage(message);
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.metadata = Map.of("path", path == null ? "" : path);
      auditService.recordError(command);
    } catch (Exception ignored) {
      // sem contexto de tenant ou erro de auditoria: nao bloquear resposta da API.
    }
  }
}
