package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.util.List;

/** Espelha {@code modules/company/application/dto/CnpjConsultaResponse.java}. */
public class CnpjConsultaResponse {

  public String cnpj;
  public String razaoSocial;
  public String nomeFantasia;
  public String situacaoCadastral;
  public String dataInicioAtividade;
  public String naturezaJuridica;
  public boolean isMei;
  public String porte;

  public CnaePrincipalDto cnaePrincipal;
  public List<CnaePrincipalDto> cnaesSecundarios;
  public EnderecoDto endereco;

  /** LGPD: retornado apenas na consulta direta (nunca cacheado). */
  public String emailSugestao;

  /** LGPD: retornado apenas na consulta direta (nunca cacheado). */
  public String telefoneSugestao;

  public static class CnaePrincipalDto {
    public String codigo;
    public String descricao;
  }

  public static class EnderecoDto {
    public String logradouro;
    public String numero;
    public String complemento;
    public String bairro;
    public String municipio;
    public String uf;
    public String cep;
  }
}
