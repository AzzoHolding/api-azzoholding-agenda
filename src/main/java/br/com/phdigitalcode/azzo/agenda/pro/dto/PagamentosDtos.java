package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pagamentos integrados (V167). Credencial nunca sai daqui: so a mascara. */
public final class PagamentosDtos {

  private PagamentosDtos() {}

  /** Uma operadora do plano; {@code disponivel=false} e "em breve". */
  public static class Operadora {
    public String codigo;
    public String nome;
    public boolean disponivel;
    public List<String> capacidades = new ArrayList<>();
    public List<Campo> campos = new ArrayList<>();
  }

  public static class Campo {
    public String chave;
    public String rotulo;
    public boolean secreto;
  }

  public static class ContaRequest {
    public String provedor;
    public String apelido;
    public String ambiente;
    /** Os campos de {@link Operadora#campos}. Na edicao, so os que mudam. */
    public Map<String, String> credenciais = new LinkedHashMap<>();
    /** PIX, CARTAO_PRESENCIAL, ONLINE. {@code null} na edicao: nao muda. */
    public List<String> padraoPara;
  }

  public static class Conta {
    public String id;
    public String provedor;
    public String nomeDoProvedor;
    public String apelido;
    public String ambiente;
    public String credenciaisMascara;
    public List<String> padraoPara = new ArrayList<>();
    public List<String> capacidades = new ArrayList<>();
    public String validadaEm;
    public String ultimoErro;
    /** Para colar no painel da operadora, quando ela pede a URL de aviso. */
    public String urlDeAviso;
  }

  public static class Cobranca {
    public String id;
    public String contaId;
    public String provedor;
    public String origemTipo;
    public String origemId;
    public String meio;
    public int parcelas;
    public BigDecimal valor;
    public BigDecimal taxa;
    public BigDecimal valorLiquido;
    public String previsaoRecebimento;
    public String status;
    public String pixCopiaECola;
    public String linkDePagamento;
    public String expiraEm;
    public String nsu;
    public String autorizacao;
    public String bandeira;
    public String cartaoFinal4;
    public BigDecimal valorEstornado;
    public String motivoRecusa;
    public String pagaEm;
    public String createdAt;
  }

  /** Cobranca de teste (R$ 1,00 por Pix ou link) para o dono ver a conta funcionando. */
  public static class CobrancaDeTesteRequest {
    public String contaId;
    public String meio;
    public String pagadorNome;
    public String pagadorDocumento;
  }

  public static class EstornoRequest {
    /** Vazio: estorna o que falta. */
    public BigDecimal valor;
  }
}
