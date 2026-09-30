package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

/**
 * Resposta da consulta publica de CNPJ ({@code GET /api/v1/public/cnpj/{cnpj}}), usada para
 * preencher o cadastro do salao antes de a conta existir.
 *
 * <p>LGPD: <b>sem e-mail nem telefone</b>. Em MEI esses contatos costumam ser da pessoa fisica, e
 * este endpoint nao exige login — entregaria contato de qualquer CNPJ a quem chamasse. Os contatos
 * so saem na consulta autenticada ({@code /api/v1/cnpj/{cnpj}}).
 */
public class PublicCnpjResponse {

  public String cnpj;
  public String razaoSocial;
  public String nomeFantasia;
  public String situacaoCadastral;
  public CnpjConsultaResponse.EnderecoDto endereco;

  public static PublicCnpjResponse de(CnpjConsultaResponse origem) {
    if (origem == null) return null;
    PublicCnpjResponse resposta = new PublicCnpjResponse();
    resposta.cnpj = origem.cnpj;
    resposta.razaoSocial = origem.razaoSocial;
    resposta.nomeFantasia = origem.nomeFantasia;
    resposta.situacaoCadastral = origem.situacaoCadastral;
    resposta.endereco = origem.endereco;
    return resposta;
  }
}
