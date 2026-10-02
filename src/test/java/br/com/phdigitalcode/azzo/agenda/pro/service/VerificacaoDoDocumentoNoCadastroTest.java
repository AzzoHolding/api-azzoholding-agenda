package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.CnpjApiIndisponivelException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.CnpjNaoEncontradoException;

class VerificacaoDoDocumentoNoCadastroTest {

  private static final String CNPJ = "11222333000181";

  private CnpjConsultaService consulta;
  private VerificacaoDoDocumentoNoCadastro verificacao;

  @BeforeEach
  void setUp() {
    consulta = mock(CnpjConsultaService.class);
    verificacao = new VerificacaoDoDocumentoNoCadastro(consulta);
  }

  private CnpjConsultaResponse comSituacao(String situacao) {
    CnpjConsultaResponse resposta = new CnpjConsultaResponse();
    resposta.situacaoCadastral = situacao;
    return resposta;
  }

  @Test
  void cpfValidoNaoConsultaAReceita() {
    assertThatCode(() -> verificacao.verificarOuFalhar("529.982.247-25")).doesNotThrowAnyException();
    verify(consulta, never()).consultarSemContato(anyString());
  }

  @Test
  void documentoComDigitoErradoEhRecusadoSemConsultar() {
    assertThatThrownBy(() -> verificacao.verificarOuFalhar("52998224726"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalido");
    assertThatThrownBy(() -> verificacao.verificarOuFalhar("11222333000182"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(consulta, never()).consultarSemContato(anyString());
  }

  @Test
  void documentoVazioEhRecusado() {
    assertThatThrownBy(() -> verificacao.verificarOuFalhar(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cnpjAtivoPassa() {
    when(consulta.consultarSemContato(CNPJ)).thenReturn(comSituacao("Ativa"));
    assertThatCode(() -> verificacao.verificarOuFalhar("11.222.333/0001-81")).doesNotThrowAnyException();
  }

  @Test
  void cnpjBaixadoOuInaptoEhRecusado() {
    when(consulta.consultarSemContato(CNPJ)).thenReturn(comSituacao("Baixada"));
    assertThatThrownBy(() -> verificacao.verificarOuFalhar(CNPJ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Baixada");
  }

  @Test
  void cnpjSemSituacaoNaRespostaNaoBarra() {
    when(consulta.consultarSemContato(CNPJ)).thenReturn(comSituacao(null));
    assertThatCode(() -> verificacao.verificarOuFalhar(CNPJ)).doesNotThrowAnyException();
  }

  @Test
  void cnpjInexistenteNaReceitaEhRecusado() {
    when(consulta.consultarSemContato(CNPJ)).thenThrow(new CnpjNaoEncontradoException());
    assertThatThrownBy(() -> verificacao.verificarOuFalhar(CNPJ))
        .isInstanceOf(CnpjNaoEncontradoException.class);
  }

  /** Falha de terceiro nao barra cliente novo: sem a consulta, vale so o digito verificador. */
  @Test
  void provedoresForaDoArNaoBarramOCadastro() {
    when(consulta.consultarSemContato(CNPJ)).thenThrow(new CnpjApiIndisponivelException("fora"));
    assertThatCode(() -> verificacao.verificarOuFalhar(CNPJ)).doesNotThrowAnyException();
  }
}
