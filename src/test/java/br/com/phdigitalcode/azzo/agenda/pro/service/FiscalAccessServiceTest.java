package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/**
 * Cobre {@code modules/fiscal/application/FiscalAccessService.java}.
 *
 * <p>O original lanca {@code jakarta.ws.rs.ForbiddenException}, que o {@code ApiExceptionMapper}
 * converte em 403 com a mensagem da excecao. A paridade aqui e {@link ApiClientErrorException} com
 * status 403 — e o teste trava tanto o status quanto o texto, porque o frontend exibe a mensagem
 * crua.
 */
@ExtendWith(MockitoExtension.class)
class FiscalAccessServiceTest {

  private static final String MENSAGEM_NEGADO =
      "Modulo fiscal disponivel apenas para empresas com CNPJ cadastrado.";

  @Mock private TenantRepository tenantRepository;

  private FiscalAccessService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FiscalAccessService(tenantRepository);
  }

  @Test
  void liberaOModuloFiscalParaTenantComCnpj() {
    when(tenantRepository.buscarDocumentoPorTenant(tenantId))
        .thenReturn(Optional.of("12.345.678/0001-90"));

    assertThat(service.podeAcessarFiscal(tenantId)).isTrue();
    assertThatCode(() -> service.validarAcessoFiscal(tenantId)).doesNotThrowAnyException();
  }

  @Test
  void negaOModuloFiscalParaTenantComCpf() {
    when(tenantRepository.buscarDocumentoPorTenant(tenantId))
        .thenReturn(Optional.of("123.456.789-00"));

    assertThat(service.podeAcessarFiscal(tenantId)).isFalse();
    assertThatThrownBy(() -> service.validarAcessoFiscal(tenantId))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage(MENSAGEM_NEGADO)
        .extracting(ex -> ((ApiClientErrorException) ex).getStatus())
        .isEqualTo(403);
  }

  @Test
  void negaQuandoOTenantNaoTemDocumentoCadastrado() {
    when(tenantRepository.buscarDocumentoPorTenant(tenantId)).thenReturn(Optional.empty());

    assertThat(service.podeAcessarFiscal(tenantId)).isFalse();
    assertThatThrownBy(() -> service.validarAcessoFiscal(tenantId))
        .isInstanceOf(ApiClientErrorException.class);
  }

  @Test
  void documentoVazioEquivaleASemDocumento() {
    when(tenantRepository.buscarDocumentoPorTenant(tenantId)).thenReturn(Optional.of("   "));

    assertThat(service.podeAcessarFiscal(tenantId)).isFalse();
  }

  /**
   * ⚠️ O criterio do original e puramente estrutural: 14 digitos depois de descartar a pontuacao.
   * Nao ha validacao de digito verificador, entao um CNPJ aritmeticamente invalido passa. Travado
   * para que a assimetria fique visivel em vez de parecer bug do porte.
   */
  @Test
  void aceitaQualquerDocumentoDeQuatorzeDigitosAindaQueInvalido() {
    when(tenantRepository.buscarDocumentoPorTenant(tenantId))
        .thenReturn(Optional.of("00.000.000/0000-00"));

    assertThat(service.podeAcessarFiscal(tenantId)).isTrue();
  }

  @Test
  void documentoDeTamanhoIntermediarioERejeitado() {
    when(tenantRepository.buscarDocumentoPorTenant(tenantId)).thenReturn(Optional.of("1234567890123"));

    assertThat(service.podeAcessarFiscal(tenantId)).isFalse();
  }
}
