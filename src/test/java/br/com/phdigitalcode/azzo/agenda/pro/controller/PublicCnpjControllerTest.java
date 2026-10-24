package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.PublicCnpjResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.PublicCnpjRateLimiter;
import br.com.phdigitalcode.azzo.agenda.pro.service.CnpjConsultaService;

class PublicCnpjControllerTest {

  private static final String CNPJ_VALIDO = "11222333000181";

  private CnpjConsultaService service;
  private PublicCnpjController controller;
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  void setUp() {
    service = mock(CnpjConsultaService.class);
    controller = new PublicCnpjController(service, new PublicCnpjRateLimiter(3, 1));
  }

  @Test
  void devolveOsDadosDoSalaoSemContato() {
    CnpjConsultaResponse origem = new CnpjConsultaResponse();
    origem.cnpj = "11.222.333/0001-81";
    origem.razaoSocial = "Salao da Ana LTDA";
    origem.nomeFantasia = "Studio Ana";
    origem.situacaoCadastral = "ATIVA";
    origem.endereco = new CnpjConsultaResponse.EnderecoDto();
    origem.endereco.logradouro = "Rua das Flores";
    origem.endereco.cep = "01001000";
    origem.emailSugestao = "ana@pessoal.com";
    origem.telefoneSugestao = "11999990000";
    when(service.consultarSemContato(CNPJ_VALIDO)).thenReturn(origem);

    PublicCnpjResponse resposta = controller.consultar("11.222.333/0001-81", request);

    assertThat(resposta.nomeFantasia).isEqualTo("Studio Ana");
    assertThat(resposta.razaoSocial).isEqualTo("Salao da Ana LTDA");
    assertThat(resposta.endereco.logradouro).isEqualTo("Rua das Flores");
    // LGPD: o tipo publico nem tem campo de contato.
    assertThat(PublicCnpjResponse.class.getFields())
        .extracting(campo -> campo.getName())
        .doesNotContain("emailSugestao", "telefoneSugestao");
    verify(service, never()).consultar(CNPJ_VALIDO);
  }

  @Test
  void cnpjInvalidoEhRecusadoSemConsultarAsApisExternas() {
    assertThatThrownBy(() -> controller.consultar("123", request))
        .isInstanceOf(IllegalArgumentException.class);
    verify(service, never()).consultarSemContato("123");
  }

  @Test
  void acimaDoLimiteDevolve429() {
    when(service.consultarSemContato(CNPJ_VALIDO)).thenReturn(new CnpjConsultaResponse());
    for (int i = 0; i < 3; i++) controller.consultar(CNPJ_VALIDO, request);

    assertThatThrownBy(() -> controller.consultar(CNPJ_VALIDO, request))
        .isInstanceOfSatisfying(
            ApiClientErrorException.class, erro -> assertThat(erro.getStatus()).isEqualTo(429));
  }

  @Test
  void ipsDiferentesTemLimitesSeparados() {
    when(service.consultarSemContato(CNPJ_VALIDO)).thenReturn(new CnpjConsultaResponse());
    MockHttpServletRequest outro = new MockHttpServletRequest();
    outro.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
    for (int i = 0; i < 3; i++) controller.consultar(CNPJ_VALIDO, request);

    assertThat(controller.consultar(CNPJ_VALIDO, outro)).isNotNull();
  }
}
