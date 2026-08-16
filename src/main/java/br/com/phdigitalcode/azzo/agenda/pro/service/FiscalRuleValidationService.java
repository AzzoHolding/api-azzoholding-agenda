package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Espelha {@code modules/fiscal/application/FiscalRuleValidationService.java}.
 *
 * <p>Validacao de regra fiscal do documento antes da emissao. Toda rejeicao e
 * {@link IllegalArgumentException} com a <b>mensagem exata do original</b> — o
 * {@code GlobalExceptionHandler} a converte em 400, e o frontend exibe a mensagem crua. Reescrever
 * qualquer texto daqui muda o que o usuario le na tela.
 *
 * <p><b>Assimetrias do original, preservadas:</b>
 *
 * <ul>
 *   <li><b>NCM e opcional, CFOP e obrigatorio.</b> {@link #validarNcm} sai calado quando o campo
 *       vem nulo ou em branco; {@link #validarCfop} rejeita nulo/branco junto com o formato
 *       invalido, porque o {@code null} nao casa com o regex.
 *   <li><b>A validacao por catalogo so entra em vigor se houver catalogo.</b> Cada
 *       {@code hasCatalogForType} funciona como interruptor: sem linhas ativas daquele tipo, a
 *       vigencia nao e exigida e a nota passa so com a validacao de formato.
 *   <li><b>{@link LocalDate#now()} e chamado por codigo validado</b>, nao uma vez por documento.
 *       Sem efeito pratico, mas explica por que nao ha um "hoje" unico na assinatura.
 *   <li><b>Regime ausente vira {@code SIMPLES_NACIONAL}</b> — o default silencioso decide se o item
 *       e validado contra CSOSN ou contra CST.
 * </ul>
 */
@Service
public class FiscalRuleValidationService {

  private static final Set<String> REGIMES_SUPORTADOS =
      Set.of("SIMPLES_NACIONAL", "LUCRO_PRESUMIDO", "LUCRO_REAL");

  /** CSOSN (Simples Nacional). */
  private static final Set<String> CSOSN_SUPORTADOS =
      Set.of("101", "102", "103", "201", "202", "203", "300", "400", "500", "900");

  /** CST (regimes nao Simples). */
  private static final Set<String> CST_SUPORTADOS =
      Set.of("00", "10", "20", "30", "40", "41", "50", "51", "60", "70", "90");

  private final FiscalCodeCatalogService fiscalCodeCatalogService;

  public FiscalRuleValidationService(FiscalCodeCatalogService fiscalCodeCatalogService) {
    this.fiscalCodeCatalogService = fiscalCodeCatalogService;
  }

  public void validarCriacao(FiscalDtos.Invoice invoice, FiscalDtos.TaxConfig taxConfig) {
    if (invoice == null) throw new IllegalArgumentException("Invoice obrigatoria");
    if (invoice.items == null || invoice.items.isEmpty()) {
      throw new IllegalArgumentException("Invoice deve possuir ao menos um item");
    }
    String regime = normalizeRegime(taxConfig != null ? taxConfig.regime : null);

    for (FiscalDtos.InvoiceItem item : invoice.items) {
      validarItemBasico(item);
      validarNcm(item.ncm);
      validarCfop(item.cfop);
      validarCstPorRegime(regime, item.cst);
      validarPartilha(item);
    }
  }

  /**
   * ⚠️ <b>Este metodo muta a invoice recebida.</b> Quando {@code totalAmount} vem zerado ou negativo
   * ele e preenchido com a soma dos itens em vez de rejeitado. Do original — o
   * {@code ServicoFiscal} depende desse preenchimento para gravar o total da nota.
   */
  public void validarTotais(
      FiscalDtos.Invoice invoice, FiscalTaxCalculationService.TaxCalculationResult calculation) {
    if (invoice == null || calculation == null) return;

    long totalItens = calculation.totalServicos();
    if (invoice.totalAmount <= 0) {
      invoice.totalAmount = totalItens;
      return;
    }
    if (invoice.totalAmount != totalItens) {
      throw new IllegalArgumentException("Total da invoice divergente da soma dos itens");
    }
  }

  private void validarItemBasico(FiscalDtos.InvoiceItem item) {
    if (item == null) throw new IllegalArgumentException("Item fiscal invalido");
    if (item.quantity <= 0) {
      throw new IllegalArgumentException("Quantidade do item deve ser maior que zero");
    }
    if (item.unitPrice < 0) {
      throw new IllegalArgumentException("Valor unitario do item nao pode ser negativo");
    }
    if (item.totalPrice < 0) {
      throw new IllegalArgumentException("Valor total do item nao pode ser negativo");
    }

    long esperado = (long) item.quantity * item.unitPrice;
    if (item.totalPrice != esperado) {
      throw new IllegalArgumentException("Total do item divergente de quantidade x valor unitario");
    }
  }

  private void validarNcm(String ncm) {
    if (ncm == null || ncm.isBlank()) return;
    String value = ncm.trim();
    if (!value.matches("^[0-9]{8}$")) {
      throw new IllegalArgumentException("NCM invalido. Informe 8 digitos numericos.");
    }
    if (fiscalCodeCatalogService.hasCatalogForType("NCM")
        && !fiscalCodeCatalogService.existsActive("NCM", value, LocalDate.now())) {
      throw new IllegalArgumentException("NCM fora da vigencia ativa");
    }
  }

  private void validarCfop(String cfop) {
    if (cfop == null || !cfop.matches("^[0-9]{4}$")) {
      throw new IllegalArgumentException("CFOP invalido. Informe 4 digitos numericos.");
    }
    if (fiscalCodeCatalogService.hasCatalogForType("CFOP")
        && !fiscalCodeCatalogService.existsActive("CFOP", cfop, LocalDate.now())) {
      throw new IllegalArgumentException("CFOP fora da vigencia ativa");
    }
  }

  private void validarCstPorRegime(String regime, String cst) {
    String codigo = cst == null ? "" : cst.trim();
    if (codigo.isBlank()) {
      throw new IllegalArgumentException("CST/CSOSN obrigatorio no item fiscal");
    }
    if ("SIMPLES_NACIONAL".equals(regime)) {
      if (!CSOSN_SUPORTADOS.contains(codigo)) {
        throw new IllegalArgumentException("CSOSN invalido para Simples Nacional");
      }
      if (fiscalCodeCatalogService.hasCatalogForType("CSOSN")
          && !fiscalCodeCatalogService.existsActive("CSOSN", codigo, LocalDate.now())) {
        throw new IllegalArgumentException("CSOSN fora da vigencia ativa");
      }
      return;
    }
    if (!CST_SUPORTADOS.contains(codigo)) {
      throw new IllegalArgumentException("CST invalido para o regime tributario informado");
    }
    if (fiscalCodeCatalogService.hasCatalogForType("CST")
        && !fiscalCodeCatalogService.existsActive("CST", codigo, LocalDate.now())) {
      throw new IllegalArgumentException("CST fora da vigencia ativa");
    }
  }

  /**
   * ⚠️ <b>Partilha e "tudo ou nada" so quando ao menos um lado vem preenchido.</b> Com os dois
   * nulos a validacao sai calada; com apenas um preenchido o outro conta como zero e a soma nao
   * fecha 100%, entao a nota e rejeitada. Do original.
   */
  private void validarPartilha(FiscalDtos.InvoiceItem item) {
    if (item == null) return;
    Double origem = item.percentualPartilhaOrigem;
    Double destino = item.percentualPartilhaDestino;
    if (origem == null && destino == null) return;

    double pOrigem = origem != null ? origem : 0d;
    double pDestino = destino != null ? destino : 0d;
    if (pOrigem < 0d || pDestino < 0d) {
      throw new IllegalArgumentException("Percentuais de partilha nao podem ser negativos");
    }
    double total = pOrigem + pDestino;
    if (Math.abs(total - 100d) > 0.0001d) {
      throw new IllegalArgumentException("Percentuais de partilha devem totalizar 100%");
    }
  }

  private String normalizeRegime(String regime) {
    String value =
        regime == null || regime.isBlank()
            ? "SIMPLES_NACIONAL"
            : regime.trim().toUpperCase(Locale.ROOT);
    if (!REGIMES_SUPORTADOS.contains(value)) {
      throw new IllegalArgumentException("Regime tributario nao suportado");
    }
    return value;
  }
}
