package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.tef;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;

/**
 * TEF no balcao (Fase 6): PayGo (PGWebLib) ou SiTef (CliSiTef), pelo Azzo Conector.
 *
 * <p>Diferente das outras operadoras, nao ha API para chamar: a "operadora" e o Conector instalado
 * no Windows do balcao, ligado ao pinpad. Criar a cobranca so a deixa na fila do terminal; o
 * Conector a busca ({@code ServicoDoConectorTef}), faz a transacao e devolve o resultado. Por isso
 * a consulta daqui nunca muda nada (quem muda e o resultado do Conector) e nao ha aviso.
 *
 * <p>Estorno: no TEF o cancelamento precisa do cartao no pinpad e do operador. Ele e feito no
 * Conector (menu "Cancelar venda"), que avisa o Azzo; depois disso a comanda estorna normalmente.
 */
@Component
public class TefProvedor implements ProvedorDePagamento {

  public static final String CODIGO = "TEF";

  @Override
  public String codigo() {
    return CODIGO;
  }

  @Override
  public String nome() {
    return "TEF no balcão (PayGo ou SiTef)";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.MAQUININHA_NUVEM);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("sistema", "Sistema de TEF: PAYGO ou SITEF", false),
        new CampoDeCredencial("servidorSitef", "SiTef: endereço do servidor SiTef (IP ou nome)", false, false),
        new CampoDeCredencial("empresaSitef", "SiTef: código da loja (8 caracteres, dado pela Software Express)", false, false),
        new CampoDeCredencial("pdcPaygo", "PayGo: ponto de captura (PDC), se a PayGo pediu", false, false));
  }

  /** Nao ha o que consultar fora: so confere se a configuracao do sistema escolhido esta completa. */
  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    String sistema = sistema(conta);
    if (sistema == null) return new ResultadoValidacao(false, "Informe o sistema de TEF: PAYGO ou SITEF.");
    if (sistema.equals("SITEF") && (vazio(conta.credencial("servidorSitef")) || vazio(conta.credencial("empresaSitef")))) {
      return new ResultadoValidacao(false, "No SiTef, informe o endereço do servidor e o código da loja.");
    }
    return new ResultadoValidacao(true,
        "TEF " + (sistema.equals("SITEF") ? "SiTef" : "PayGo") + " configurado. Agora instale o Azzo Conector no computador do balcão e pareie em Maquininhas.");
  }

  /** A cobranca fica na fila do terminal: o id "na operadora" e o da propria cobranca. */
  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"CREDITO".equals(pedido.meio()) && !"DEBITO".equals(pedido.meio())) {
      throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    }
    if (pedido.terminalSerial() == null || pedido.terminalSerial().isBlank()) {
      throw new NaoSuportado(codigo(), "cartão sem o Azzo Conector");
    }
    return new CobrancaCriada(pedido.cobrancaId(), Status.AGUARDANDO, null, null, pedido.expiraEm());
  }

  /** O status vem do Conector; a consulta de reserva nao tem o que perguntar. */
  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    return StatusNaOperadora.so(Status.AGUARDANDO);
  }

  /** Cancelar e so marcar: o Conector ve a cobranca cancelada e aborta (ou desfaz) no pinpad. */
  @Override
  public void cancelar(Conta conta, String idNaOperadora) {}

  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    throw new FalhaNaOperadora("Venda no TEF: faça o cancelamento no Azzo Conector do caixa (menu \"Cancelar venda\", com o "
        + "cartão no pinpad). Quando o Conector confirmar, estorne a comanda de novo.", null);
  }

  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    throw new AvisoInvalido("O TEF não manda aviso: o resultado vem do Azzo Conector.");
  }

  public static String sistema(Conta conta) {
    String s = conta.credencial("sistema");
    if (s == null) return null;
    s = s.trim().toUpperCase();
    return s.equals("PAYGO") || s.equals("SITEF") ? s : null;
  }

  private static boolean vazio(String s) {
    return s == null || s.isBlank();
  }
}
