package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * De onde vem o salao da requisicao. O header {@code X-Tenant-Id} so vale em chamada entre
 * sistemas autenticada pela chave interna; de qualquer outra origem, quem chama nao escolhe o
 * salao (2026-09-18).
 */
class ContextoTenantTest {

  private final ContextoTenant contexto = new ContextoTenant();

  @AfterEach
  void limpar() {
    RequestContextHolder.resetRequestAttributes();
  }

  private MockHttpServletRequest requisicaoComHeader(UUID tenantId) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/qualquer");
    request.addHeader("X-Tenant-Id", tenantId.toString());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return request;
  }

  @Test
  @DisplayName("sem chave interna, o header X-Tenant-Id e ignorado")
  void headerSemChaveInternaEhIgnorado() {
    requisicaoComHeader(UUID.randomUUID());

    assertThatThrownBy(contexto::obterTenantIdOuFalhar)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TenantId ausente");
  }

  @Test
  @DisplayName("com chave interna validada, o header X-Tenant-Id vale")
  void headerComChaveInternaVale() {
    UUID tenantId = UUID.randomUUID();
    MockHttpServletRequest request = requisicaoComHeader(tenantId);
    request.setAttribute(InternalApiKeyFilter.ATRIBUTO_CHAMADA_INTERNA, Boolean.TRUE);

    assertThat(contexto.obterTenantIdOuFalhar()).isEqualTo(tenantId);
  }

  @Test
  @DisplayName("o salao fixado pelo servidor vence o header")
  void salaoFixadoPeloServidorVence() {
    UUID fixado = UUID.randomUUID();
    MockHttpServletRequest request = requisicaoComHeader(UUID.randomUUID());
    request.setAttribute(InternalApiKeyFilter.ATRIBUTO_CHAMADA_INTERNA, Boolean.TRUE);
    contexto.definirTenantId(fixado);

    assertThat(contexto.obterTenantIdOuFalhar()).isEqualTo(fixado);
  }
}
