package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uma operadora de pagamento (Mercado Pago, Stone/Pagar.me, Cielo, PagBank, Rede, Getnet, Asaas).
 *
 * <p>O dinheiro vai para a conta do PROPRIO salao na operadora: o Azzo cria a cobranca com as
 * credenciais dele, recebe o aviso (webhook) e concilia. Nenhum dado de cartao passa pelo Azzo —
 * maquininha, checkout ou tokenizacao da operadora.
 *
 * <p>Cada operadora implementa so o que oferece e diz o que oferece em {@link #capacidades()}; o
 * resto lanca {@link NaoSuportado}. Mesmo desenho dos provedores da NFS-e
 * ({@code NfseProviderAdapter}). Plano completo: {@code PLANO-PAGAMENTOS.md}.
 */
public interface ProvedorDePagamento {

  /** ASAAS, MERCADO_PAGO, PAGARME, STONE, CIELO, PAGBANK, REDE, GETNET. */
  String codigo();

  String nome();

  Set<Capacidade> capacidades();

  /**
   * Os campos que a tela pede para conectar a conta (ex.: Asaas: "apiKey"). Na ordem em que
   * aparecem; o rotulo e o que a pessoa le.
   */
  List<CampoDeCredencial> camposDeCredencial();

  /** Confere as credenciais na operadora, sem cobrar nada. */
  ResultadoValidacao validarCredenciais(Conta conta);

  CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido);

  /** Status atual na operadora — a reserva para quando o webhook se perde. */
  StatusNaOperadora consultar(Conta conta, String idNaOperadora);

  default void cancelar(Conta conta, String idNaOperadora) {
    throw new NaoSuportado(codigo(), "cancelar");
  }

  default StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    throw new NaoSuportado(codigo(), "estornar");
  }

  /**
   * Traduz o aviso da operadora. O token da conta ja foi conferido pela URL; aqui a operadora
   * confere a ASSINATURA do corpo, quando ela assina (HMAC), e devolve o que aconteceu.
   */
  AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo);

  default List<Terminal> terminais(Conta conta) {
    return List.of();
  }

  // ---- tipos ---------------------------------------------------------------------------------

  enum Capacidade { PIX, LINK, CARTAO_ONLINE, MAQUININHA_NUVEM, RECORRENTE, ESTORNO, ESTORNO_PARCIAL }

  enum Status { CRIADA, AGUARDANDO, PAGA, RECUSADA, CANCELADA, EXPIRADA, ESTORNADA, ESTORNO_PARCIAL }

  record CampoDeCredencial(String chave, String rotulo, boolean secreto) {}

  /** A conta do salao, ja com as credenciais abertas (so em memoria, so durante a chamada). */
  record Conta(String id, String tenantId, String ambiente, Map<String, String> credenciais, String urlDeAviso) {
    public String credencial(String chave) {
      return credenciais == null ? null : credenciais.get(chave);
    }

    public boolean producao() {
      return "PROD".equals(ambiente);
    }
  }

  /** Quem paga. CPF/CNPJ so quando a operadora exige (o Asaas exige; o Mercado Pago nao). */
  record Pagador(String id, String nome, String email, String telefone, String documento) {}

  record PedidoDeCobranca(
      String cobrancaId,
      String meio,
      BigDecimal valor,
      int parcelas,
      String descricao,
      Pagador pagador,
      String terminalSerial,
      Instant expiraEm) {}

  record CobrancaCriada(
      String idNaOperadora, Status status, String pixCopiaECola, String linkDePagamento, Instant expiraEm) {}

  record StatusNaOperadora(
      Status status,
      BigDecimal valorPago,
      BigDecimal taxa,
      BigDecimal valorLiquido,
      LocalDate previsaoRecebimento,
      String nsu,
      String autorizacao,
      String bandeira,
      String cartaoFinal4,
      BigDecimal valorEstornado,
      String motivo) {

    public static StatusNaOperadora so(Status status) {
      return new StatusNaOperadora(status, null, null, null, null, null, null, null, null, null, null);
    }
  }

  /** O aviso traduzido: qual cobranca (pelo id na operadora), o tipo e o status novo. */
  record AvisoDaOperadora(String idDoEvento, String tipo, String idNaOperadora, StatusNaOperadora status) {}

  record ResultadoValidacao(boolean valida, String mensagem) {}

  record Terminal(String serial, String modelo, String apelido) {}

  final class NaoSuportado extends RuntimeException {
    public NaoSuportado(String provedor, String operacao) {
      super("A operadora " + provedor + " não oferece \"" + operacao + "\" pelo Azzo.");
    }
  }

  /** A operadora recusou ou falhou: a mensagem vai para a tela como esta. */
  final class FalhaNaOperadora extends RuntimeException {
    public FalhaNaOperadora(String mensagem, Throwable causa) {
      super(mensagem, causa);
    }
  }

  /** Aviso com assinatura invalida: nao se processa. */
  final class AvisoInvalido extends RuntimeException {
    public AvisoInvalido(String mensagem) {
      super(mensagem);
    }
  }
}
