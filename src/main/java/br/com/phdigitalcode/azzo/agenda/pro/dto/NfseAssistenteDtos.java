package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.ArrayList;
import java.util.List;

/** Respostas do assistente de configuracao da NFS-e (06/11/2026). */
public final class NfseAssistenteDtos {

  private NfseAssistenteDtos() {}

  /** O que a consulta do CNPJ do proprio salao preenche sozinha. */
  public static class Emissor {
    public String cnpj;
    public String razaoSocial;
    public String nomeFantasia;
    public String situacaoCadastral;
    public String logradouro;
    public String numero;
    public String complemento;
    public String bairro;
    public String cep;
    public String municipio;
    public String uf;
    public String municipioCodigoIbge;
    /** 1 = nao optante, 2 = MEI, 3 = ME/EPP; nulo quando a consulta nao informou. */
    public String situacaoNoSimples;
    public String telefone;
    public String email;
  }

  public static class Problema {
    /** ERRO impede emitir; AVISO pede atencao, mas nao impede. */
    public String tipo;
    /** O campo ou a parte da configuracao (para a tela levar ate ele). */
    public String campo;
    public String mensagem;

    public Problema() {}

    public Problema(String tipo, String campo, String mensagem) {
      this.tipo = tipo;
      this.campo = campo;
      this.mensagem = mensagem;
    }
  }

  public static class Conferencia {
    /** Nenhum ERRO: a configuracao gera uma nota que passa no esquema oficial. */
    public boolean pronto;
    public List<Problema> problemas = new ArrayList<>();
  }
}
