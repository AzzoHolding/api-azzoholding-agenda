package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Porte verbatim de {@code modules/pos/api/dto/ComandaDtos.java} — mesmos nomes de campo, mesmas
 * anotacoes de validacao, mesmo formato JSON.
 */
public final class ComandaDtos {

  private ComandaDtos() {}

  public static class AbrirComandaRequest {
    public String appointmentId;
    public String clientId;
  }

  public static class AdicionarItemRequest {
    @NotBlank
    @Pattern(regexp = "SERVICO|PRODUTO|PACOTE")
    public String tipo;

    @NotBlank public String referenciaId;
    public String professionalId;

    @DecimalMin(value = "0.001", inclusive = true)
    public BigDecimal quantidade = BigDecimal.ONE;

    /** Obrigatorio para PRODUTO; para SERVICO usa o preco de tabela se omitido. */
    public BigDecimal precoUnitario;

    /**
     * "Sim, e de proposito": lancar de novo um item que a comanda ja tem e RECUSADO ate alguem
     * confirmar. O caso legitimo existe (dois cortes na mesma conta), o acidente tambem — e o
     * acidente cobra o cliente duas vezes.
     */
    public Boolean confirmarDuplicado;
  }

  public static class AplicarDescontoRequest {
    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    public BigDecimal percentual;

    @NotBlank public String motivo;
  }

  public static class RegistrarGorjetaRequest {
    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    public BigDecimal valor;

    @NotBlank public String professionalId;
  }

  public static class RegistrarPagamentoRequest {
    @NotBlank
    @Pattern(
        regexp = "DINHEIRO|PIX_ASAAS|CARTAO_CREDITO_EXTERNO|CARTAO_DEBITO_EXTERNO|CREDITO_SINAL|PIX|CARTAO_CREDITO|CARTAO_DEBITO|LINK")
    public String meio;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    public BigDecimal valor;

    /** Cartao de credito na maquininha integrada: em quantas vezes (1 a 12). */
    public Integer parcelas;

    /** Maquininha integrada que recebe a cobranca (CARTAO_CREDITO / CARTAO_DEBITO). */
    public String terminalId;
  }

  public static class CancelarComandaRequest {
    @NotBlank public String motivo;

    /**
     * O que ja foi pago volta para o cliente? {@code true}: nada entra no caixa. {@code false}: o
     * salao fica com o valor, que entra como receita. Obrigatorio quando ha pagamento confirmado.
     */
    public Boolean estornarPagamento;
  }

  public static class EstornarComandaRequest {
    @NotBlank public String motivo;
  }

  /** Produto do estoque com preco de venda, para o PDV (etapa 4). */
  public static class ProdutoAVendaResponse {
    public String id;
    public String nome;
    public BigDecimal precoVenda;
    public BigDecimal saldoAtual;
    public String unidadeMedida;
  }

  /** O que o caixa precisa para o resgate de pontos numa comanda (etapa 4). */
  public static class FidelidadeDaComandaResponse {
    public boolean programaAtivo;
    public boolean temCliente;
    /** Saldo da cliente agora (pode ser negativo, depois de estorno aceito pelo dono). */
    public int saldo;
    public BigDecimal valorDoSaldo;
    public BigDecimal pontosPorResgateReal;
    public int resgatadosNaComanda;
    public BigDecimal valorResgatado;
  }

  public static class ResgatarFidelidadeRequest {
    /** Numero (e nao int): 1,5 chega como 1,5 e e recusado, em vez de virar 1 em silencio. */
    public java.math.BigDecimal pontos;
  }

  public static class ComandaItemResponse {
    public String id;
    public String tipo;
    public String referenciaId;
    public String descricao;
    public String professionalId;
    public BigDecimal quantidade;
    public BigDecimal precoUnitario;
    public BigDecimal total;

    /** AGENDAMENTO ou MANUAL (V132): a tela mostra de onde a linha veio. */
    public String origem;

    /** PACOTE ou ASSINATURA quando o servico saiu do saldo do cliente (V134). */
    public String coberturaTipo;

    public String coberturaSaldoId;

    /** Valor da sessao dentro do que o cliente pagou: a base da comissao do item coberto. */
    public BigDecimal valorCobertura;
  }

  /** A recepcao escolhe de onde o servico sai (V134). */
  public static class AplicarCoberturaRequest {
    @NotBlank
    @Pattern(regexp = "PACOTE|ASSINATURA")
    public String tipo;

    @NotBlank public String saldoId;
  }

  /** Um saldo do cliente que pode cobrir o servico do item. */
  public static class OpcaoDeCoberturaResponse {
    public String tipo;
    public String saldoId;
    /** Nome do pacote ou do plano. */
    public String nome;
    /** Quantas sessoes ainda restam nesse saldo. */
    public int disponiveis;
    /** Valor de cada sessao dentro do que foi pago — base da comissao. */
    public BigDecimal valorDaSessao;
  }

  public static class ComandaPagamentoResponse {
    public String id;
    public String meio;
    public BigDecimal valor;
    public String status;
    public String pixPayload;
    /** QR Code do Pix pendente, SVG em data URI, para o cliente ler na tela do caixa. */
    public String pixQrCode;
    public String paidAt;
    /** Meios integrados: a cobranca na operadora (a tela acompanha e cancela por ela). */
    public String cobrancaId;
    public String linkDePagamento;
    public String nsu;
    public String bandeira;
    public Integer parcelas;
    public String motivoRecusa;
    /** TEF: a via do cliente do comprovante, para reimprimir. */
    public String comprovante;
    /** Pix na chave do salao: o caixa confirma o recebimento ("Recebi"); nao vem aviso. */
    public boolean confirmacaoManual;
  }

  public static class ComandaResponse {
    public String id;
    public String appointmentId;
    public String clientId;
    public String status;
    public BigDecimal subtotal;
    public BigDecimal desconto;
    public String descontoMotivo;
    public BigDecimal gorjeta;
    public String gorjetaProfessionalId;
    public BigDecimal total;
    public String cancelMotivo;
    public String estornoMotivo;
    public String estornadoEm;
    public String openedAt;
    public String closedAt;
    public List<ComandaItemResponse> itens = new ArrayList<>();
    public List<ComandaPagamentoResponse> pagamentos = new ArrayList<>();
  }

  public static class ComandaPageResponse {
    public List<ComandaResponse> content = new ArrayList<>();
    public long totalElements;
    public int page;
    public int size;
  }
}
