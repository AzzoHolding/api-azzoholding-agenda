package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.Map;

/** O contrato entre o Azzo e o Azzo Conector (TEF no balcao, Fase 6). */
public final class ConectorTefDtos {

  private ConectorTefDtos() {}

  public static class PedidoDeConector {
    /** Nome do caixa no Azzo (ex.: "Recepção"). */
    public String apelido;
  }

  /** O codigo aparece so nesta resposta: o dono digita no Conector. */
  public static class CodigoDePareamento {
    public String terminalId;
    public String apelido;
    /** "ABCD-2345". */
    public String codigo;
    public String expiraEm;
  }

  public static class PedidoDePareamento {
    public String codigo;
    public String nomeDoComputador;
    /** Versao do Conector, para o suporte. */
    public String versao;
  }

  public static class Configuracao {
    public String terminalId;
    public String apelido;
    /** PAYGO ou SITEF. */
    public String sistema;
    /** SANDBOX ou PROD (o Conector usa o servidor de teste da PayGo/SiTef no SANDBOX). */
    public String ambiente;
    /** servidorSitef, empresaSitef, pdcPaygo — so os preenchidos. */
    public Map<String, String> parametros;
  }

  public static class Pareado {
    /** Guardado pelo Conector (protegido pelo Windows); o Azzo so guarda o hash. */
    public String token;
    public Configuracao configuracao;
  }

  public static class Pedido {
    public String cobrancaId;
    /** VENDA (o cancelamento e iniciado pelo operador no proprio Conector). */
    public String tipo;
    /** CREDITO ou DEBITO. */
    public String meio;
    public int parcelas;
    public BigDecimal valor;
    public long valorEmCentavos;
    public String criadaEm;
  }

  public static class Situacao {
    public String status;
    /** O caixa cancelou (ou a venda expirou): o Conector aborta no pinpad. */
    public boolean cancelada;
  }

  public static class Resultado {
    public boolean aprovado;
    public String nsu;
    public String autorizacao;
    public String bandeira;
    /** So os 4 ultimos digitos. */
    public String cartaoFinal4;
    public String motivo;
    public String comprovanteCliente;
    public String comprovanteLoja;
  }

  public static class RespostaAoResultado {
    /** true: o Conector CONFIRMA a transacao TEF. false: DESFAZ. */
    public boolean confirmar;
    public String motivo;
  }

  public static class CancelamentoNoPinpad {
    public String cobrancaId;
    public String nsu;
  }
}
