package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pixchave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Conta;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.PedidoDeCobranca;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class PixNaChaveProvedorTest {

  private final PixNaChaveProvedor provedor = new PixNaChaveProvedor();

  /** O exemplo do Manual de Padroes para Iniciacao do Pix (Banco Central), caractere por caractere. */
  @Test
  void montaOExemploDoManualDoBancoCentral() {
    String esperado = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR"
        + "5913Fulano de Tal6008BRASILIA62070503***63041D3D";
    assertThat(BrCodeDoPix.montar("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "BRASILIA", null, null))
        .isEqualTo(esperado);
  }

  @Test
  void comValorETxidOCrcFechaComOConteudo() {
    String pix = BrCodeDoPix.montar("12345678909", "Salão da Ana", "São Paulo", new BigDecimal("150.5"), "AZZO123");
    assertThat(pix).contains("5406150.50").contains("5912Salao da Ana").contains("6009Sao Paulo").contains("0507AZZO123");
    String semCrc = pix.substring(0, pix.length() - 4);
    assertThat(pix.substring(pix.length() - 4)).isEqualTo(BrCodeDoPix.crc16(semCrc));
  }

  @Test
  void cortaNomeECidadeNoTamanhoDoPadrao() {
    String pix = BrCodeDoPix.montar("12345678909", "Studio de Beleza e Estética Avançada Ltda", "Santo Antônio de Jesus",
        BigDecimal.TEN, "X");
    assertThat(pix).contains("5925Studio de Beleza e Esteti").contains("6015Santo Antonio d");
  }

  @Test
  void normalizaCadaTipoDeChave() {
    assertThat(BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.CPF, "123.456.789-09")).isEqualTo("12345678909");
    assertThat(BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.CNPJ, "11.222.333/0001-81")).isEqualTo("11222333000181");
    assertThat(BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.EMAIL, " Ana@Salao.com.br ")).isEqualTo("ana@salao.com.br");
    assertThat(BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.TELEFONE, "(11) 98765-4321")).isEqualTo("+5511987654321");
    assertThat(BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.TELEFONE, "+55 11 98765-4321")).isEqualTo("+5511987654321");
    assertThat(BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.ALEATORIA, "123E4567-E12B-12D1-A456-426655440000"))
        .isEqualTo("123e4567-e12b-12d1-a456-426655440000");
  }

  @Test
  void recusaChaveInvalida() {
    assertThatThrownBy(() -> BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.CPF, "123.456.789-00"))
        .hasMessageContaining("CPF inválido");
    assertThatThrownBy(() -> BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.CPF, "111.111.111-11"))
        .hasMessageContaining("CPF inválido");
    assertThatThrownBy(() -> BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.CNPJ, "11.222.333/0001-82"))
        .hasMessageContaining("CNPJ inválido");
    assertThatThrownBy(() -> BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.EMAIL, "ana@"))
        .hasMessageContaining("E-mail inválido");
    assertThatThrownBy(() -> BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.TELEFONE, "98765"))
        .hasMessageContaining("Telefone inválido");
    assertThatThrownBy(() -> BrCodeDoPix.normalizarChave(BrCodeDoPix.TipoDeChave.ALEATORIA, "abc"))
        .hasMessageContaining("Chave aleatória inválida");
  }

  @Test
  void validaAContaSemChamarNinguem() {
    assertThat(provedor.validarCredenciais(conta("CPF", "123.456.789-09")).valida()).isTrue();
    assertThat(provedor.validarCredenciais(conta("cpf", "123.456.789-00")).mensagem()).contains("CPF inválido");
    assertThat(provedor.validarCredenciais(conta("PIX", "123.456.789-09")).mensagem()).contains("Tipo da chave inválido");
  }

  @Test
  void criaACobrancaComOTxidDaCobrancaEValorDaComanda() {
    var criada = provedor.criarCobranca(conta("TELEFONE", "11987654321"), pedido("PIX", "89"));
    assertThat(criada.status()).isEqualTo(Status.AGUARDANDO);
    assertThat(criada.idNaOperadora()).isEqualTo("AZZO0F3A1C2B4D5E6F708192A");
    assertThat(criada.idNaOperadora()).hasSize(25);
    assertThat(criada.pixCopiaECola()).contains("0114+5511987654321").contains("540589.00")
        .contains("0525AZZO0F3A1C2B4D5E6F708192A");
  }

  @Test
  void naoCobraCartaoNemLink() {
    assertThatThrownBy(() -> provedor.criarCobranca(conta("CPF", "12345678909"), pedido("CREDITO", "10")))
        .isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
  }

  @Test
  void chaveQueFicouInvalidaViraFalhaComMensagemParaATela() {
    assertThatThrownBy(() -> provedor.criarCobranca(conta("CPF", "00000000000"), pedido("PIX", "10")))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("Configurações");
  }

  @Test
  void consultaNuncaMudaOStatusEEstornoSoMarca() {
    Conta conta = conta("CPF", "12345678909");
    assertThat(provedor.consultar(conta, "x").status()).isEqualTo(Status.AGUARDANDO);
    assertThat(provedor.estornar(conta, "x", null).motivo()).contains("aplicativo do banco");
    assertThatThrownBy(() -> provedor.lerAviso(conta, Map.of(), "{}")).isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
  }

  @Test
  void mascaraMostraSoOTipoEOFinal() {
    assertThat(PixNaChaveProvedor.mascara(Map.of("tipoDaChave", "cpf", "chave", "12345678909"))).isEqualTo("CPF ••••8909");
  }

  private static Conta conta(String tipo, String chave) {
    return new Conta("c1", "t1", "PROD", Map.of("tipoDaChave", tipo, "chave", chave, "nome", "Salão da Ana", "cidade", "Campinas"),
        null);
  }

  private static PedidoDeCobranca pedido(String meio, String valor) {
    return new PedidoDeCobranca("0f3a1c2b-4d5e-6f70-8192-a3b4c5d6e7f8", meio, new BigDecimal(valor), 1, "Comanda", null, null, null);
  }
}
