package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pixchave;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;

/**
 * Pix na chave do proprio salao (Pix estatico, sem operadora).
 *
 * <p>O Azzo monta o QR Code com a chave, o nome e a cidade cadastrados, o valor da comanda e um
 * identificador da cobranca. O dinheiro cai direto na conta dona da chave, sem taxa.
 *
 * <p><b>O Azzo nao fica sabendo do pagamento:</b> sem operadora, nao ha aviso nem consulta. Quem
 * confirma e o caixa, depois de ver o dinheiro no aplicativo do banco ("Recebi" no PDV,
 * {@code ServicoDePagamentos.confirmarRecebimento}), e isso fica na auditoria com quem confirmou.
 * Pelo mesmo motivo, o estorno so marca a cobranca: a devolucao e feita pelo banco do salao.
 */
@Component
public class PixNaChaveProvedor implements ProvedorDePagamento {

  public static final String CODIGO = "PIX_CHAVE";

  @Override
  public String codigo() {
    return CODIGO;
  }

  @Override
  public String nome() {
    return "Pix na minha chave";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.ESTORNO, Capacidade.ESTORNO_PARCIAL);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("tipoDaChave", "Tipo da chave: CPF, CNPJ, EMAIL, TELEFONE ou ALEATORIA", false),
        new CampoDeCredencial("chave", "Chave Pix", false),
        new CampoDeCredencial("nome", "Nome do recebedor (como está no banco, até 25 letras)", false),
        new CampoDeCredencial("cidade", "Cidade do recebedor (até 15 letras)", false));
  }

  /** Nao ha banco para perguntar: confere o formato da chave e monta um Pix de exemplo. */
  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      String chave = chave(conta);
      BrCodeDoPix.montar(chave, conta.credencial("nome"), conta.credencial("cidade"), BigDecimal.ONE, "TESTE");
      return new ResultadoValidacao(true,
          "Chave Pix cadastrada. Faça um Pix de teste de R$ 1,00 e confira se o nome que o banco mostra é o seu.");
    } catch (IllegalArgumentException e) {
      return new ResultadoValidacao(false, e.getMessage());
    }
  }

  /** O id "na operadora" e o txid que vai no QR: e por ele que o salao acha o Pix no extrato. */
  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"PIX".equals(pedido.meio())) throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    String txid = txid(pedido.cobrancaId());
    try {
      String copiaECola = BrCodeDoPix.montar(chave(conta), conta.credencial("nome"), conta.credencial("cidade"),
          pedido.valor(), txid);
      return new CobrancaCriada(txid, Status.AGUARDANDO, copiaECola, null, pedido.expiraEm());
    } catch (IllegalArgumentException e) {
      throw new FalhaNaOperadora("Pix na minha chave: " + e.getMessage() + " Corrija em Configurações → Pagamentos.", e);
    }
  }

  /** Sem operadora nao ha o que consultar: so o caixa muda o status. */
  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    return StatusNaOperadora.so(Status.AGUARDANDO);
  }

  /** O QR estatico continua valido no banco da cliente; cancelar so tira da comanda. */
  @Override
  public void cancelar(Conta conta, String idNaOperadora) {}

  /** O Azzo so marca: a devolucao do dinheiro e feita pelo banco do salao. */
  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    return new StatusNaOperadora(Status.ESTORNADA, null, null, null, null, null, null, null, null, null,
        "Pix na chave do salão: devolva o valor à cliente pelo aplicativo do banco.");
  }

  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    throw new AvisoInvalido("O Pix na chave do salão não tem aviso: o caixa confirma o recebimento.");
  }

  static String chave(Conta conta) {
    BrCodeDoPix.TipoDeChave tipo = BrCodeDoPix.tipo(conta.credencial("tipoDaChave"));
    if (tipo == null) throw new IllegalArgumentException("Tipo da chave inválido: use CPF, CNPJ, EMAIL, TELEFONE ou ALEATORIA.");
    return BrCodeDoPix.normalizarChave(tipo, conta.credencial("chave"));
  }

  /** "AZZO" + 21 caracteres do id da cobranca = 25, o maximo do padrao. */
  static String txid(String cobrancaId) {
    String limpo = cobrancaId == null ? "" : cobrancaId.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    String t = "AZZO" + limpo;
    return t.length() > 25 ? t.substring(0, 25) : t;
  }

  /** Para a tela de contas: o tipo e os 4 ultimos da chave. */
  public static String mascara(Map<String, String> credenciais) {
    String digitada = credenciais == null ? null : credenciais.get("chave");
    // Sem pontuacao: "11.222.333/0001-81" mostra "0181", e nao "1-81".
    String chave = digitada == null ? "" : digitada.replaceAll("[^A-Za-z0-9@.]", "");
    if (chave.isBlank()) return null;
    String tipo = credenciais.getOrDefault("tipoDaChave", "").trim().toUpperCase();
    return (tipo.isEmpty() ? "" : tipo + " ") + "••••" + chave.substring(Math.max(0, chave.length() - 4));
  }
}
