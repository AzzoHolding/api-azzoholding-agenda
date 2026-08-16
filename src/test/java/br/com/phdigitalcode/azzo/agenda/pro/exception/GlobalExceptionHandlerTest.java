package br.com.phdigitalcode.azzo.agenda.pro.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre a ordem de precedencia de excecoes e o mapeamento de codigos funcionais do
 * {@link GlobalExceptionHandler} (equivalente ao {@code ApiExceptionMapper} do Quarkus original —
 * ver risco 1 e 9 do inventario de migracao).
 */
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    AuditService auditService = mock(AuditService.class);
    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenThrow(new IllegalStateException("sem tenant"));
    handler = new GlobalExceptionHandler(auditService, contextoTenant);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fiscal/nfse");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void asaasExceptionUsaStatusCodeDaExcecaoEMensagemGenerica() {
    AsaasException ex = new AsaasException("Falha ao criar cliente Asaas: cartao invalido", 422);
    ResponseEntity<ErrorResponse> response = handler.handleAsaas(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().code).isEqualTo("REGISTRATION_ERROR");
    assertThat(response.getBody().message)
        .isEqualTo("Nao foi possivel concluir o cadastro. Verifique os dados informados e tente novamente.");
  }

  @Test
  void fiscalProviderExceptionMapeiaMensagemPublicaPorFaixaDeStatus() {
    FiscalProviderException indisponivel = new FiscalProviderException("timeout upstream", 503);
    ResponseEntity<ErrorResponse> response = handler.handleFiscalProvider(indisponivel);

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody().message).contains("temporariamente indisponivel");
  }

  @Test
  void cnpjIndisponivelRetorna503ComMensagemSegura() {
    CnpjApiIndisponivelException ex = new CnpjApiIndisponivelException(null);
    ResponseEntity<ErrorResponse> response = handler.handleCnpjIndisponivel(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().code).isEqualTo("CNPJ_API_INDISPONIVEL");
  }

  @Test
  void appointmentConflictRetorna409ComDetails() {
    Object details = "profissional ocupado";
    AppointmentConflictException ex = new AppointmentConflictException("conflito", details);
    ResponseEntity<ErrorResponse> response = handler.handleAppointmentConflict(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().details).isEqualTo(details);
  }

  @Test
  void codigoFuncionalConhecidoEhTraduzidoParaMensagemEmPortugues() {
    IllegalArgumentException ex = new IllegalArgumentException("NFSE_CERTIFICATE_PASSWORD_REQUIRED");
    ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

    assertThat(response.getBody().code).isEqualTo("NFSE_CERTIFICATE_PASSWORD_REQUIRED");
    assertThat(response.getBody().message).isEqualTo("Informe a senha do certificado para continuar.");
  }

  @Test
  void illegalArgumentSemCodigoFuncionalRecebeSufixoCorretivo() {
    IllegalArgumentException ex = new IllegalArgumentException("Email obrigatorio");
    ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

    assertThat(response.getBody().message).isEqualTo("Email obrigatorio Revise os dados informados e tente novamente.");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void mensagemComPalavraCredenciaisResultaEmStatus401() {
    IllegalArgumentException ex = new IllegalArgumentException("Credenciais invalidas");
    ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void excecaoInesperadaNaoVazaMensagemInterna() {
    RuntimeException ex = new RuntimeException("NullPointerException at SomeInternalClass.java:42");
    ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

    assertThat(response.getBody().message).isEqualTo("Ocorreu um erro inesperado. Tente novamente ou contate o suporte.");
  }

  @Test
  void mensagemComTokenESanitizadaAntesDeResponder() {
    IllegalStateException ex = new IllegalStateException("Falha: Authorization: Bearer abc.def.ghi invalido");
    ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

    assertThat(response.getBody().message).isEqualTo("Erro na requisicao");
  }

  @Test
  void apiClientErrorExceptionPreserva429ComMensagemPadrao() {
    ApiClientErrorException ex = new ApiClientErrorException("rate limited", 429);
    ResponseEntity<ErrorResponse> response = handler.handleApiClientError(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(429);
    assertThat(response.getBody().message).contains("Muitas tentativas em pouco tempo");
  }
}
