package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Espelha {@code modules/fiscal/application/FiscalTaxCalculationService.java}.
 *
 * <p>Servico puro: nenhuma dependencia, nenhum acesso a banco. Recebe a invoice e a configuracao
 * tributaria e devolve os totais em <b>centavos</b>.
 *
 * <p><b>Duas aritmeticas convivem aqui, e isso e do original — nao unificar:</b>
 *
 * <ul>
 *   <li>ICMS/PIS/COFINS saem de {@link #applyRate}, em {@link BigDecimal}, com dois arredondamentos
 *       {@code HALF_UP} encadeados (escala 2 e depois escala 0).
 *   <li>ICMS-ST, DIFAL, FCP e partilha saem de aritmetica {@code double} acumulada e so arredondam
 *       uma vez, no fim, via {@link Math#round} (que e HALF_UP para positivos). Trocar por
 *       {@code BigDecimal} mudaria centavos em notas com muitos itens.
 * </ul>
 *
 * <p>As aliquotas de {@link FiscalDtos.TaxConfig} chegam em pontos percentuais ({@code 10} = 10%) e
 * as de {@link FiscalDtos.InvoiceItem} tambem — dai o {@code / 100} em {@link #percentOrZero}.
 */
@Service
public class FiscalTaxCalculationService {

  public TaxCalculationResult calcular(FiscalDtos.Invoice invoice, FiscalDtos.TaxConfig taxConfig) {
    long totalServicos = 0L;
    if (invoice != null && invoice.items != null) {
      totalServicos = invoice.items.stream().mapToLong(i -> i != null ? i.totalPrice : 0L).sum();
    }

    BigDecimal icmsRate =
        taxConfig != null && taxConfig.icmsRate != null ? taxConfig.icmsRate : BigDecimal.ZERO;
    BigDecimal pisRate =
        taxConfig != null && taxConfig.pisRate != null ? taxConfig.pisRate : BigDecimal.ZERO;
    BigDecimal cofinsRate =
        taxConfig != null && taxConfig.cofinsRate != null ? taxConfig.cofinsRate : BigDecimal.ZERO;

    long icms = applyRate(totalServicos, icmsRate);
    long pis = applyRate(totalServicos, pisRate);
    long cofins = applyRate(totalServicos, cofinsRate);
    long totalImpostos = icms + pis + cofins;

    double totalIcmsSt = 0d;
    double totalDifal = 0d;
    double totalFcp = 0d;
    double totalPartilhaOrigem = 0d;
    double totalPartilhaDestino = 0d;

    if (invoice != null && invoice.items != null) {
      for (FiscalDtos.InvoiceItem item : invoice.items) {
        if (item == null) continue;

        double baseSt = valueOrZero(item.baseIcmsSt);
        double mva = percentOrZero(item.mvaPercent);
        double aliqInterna = percentOrZero(item.aliquotaInternaDestino);
        double aliqInter = percentOrZero(item.aliquotaInterestadual);
        double baseFcp = valueOrZero(item.baseFcp);
        double aliqFcp = percentOrZero(item.aliquotaFcp);
        double pOrigem = percentOrZero(item.percentualPartilhaOrigem);
        double pDestino = percentOrZero(item.percentualPartilhaDestino);

        if (baseSt > 0d && aliqInterna > 0d) {
          double baseComMva = baseSt * (1d + mva);
          double icmsStTotal = baseComMva * aliqInterna;
          double icmsProprio = baseSt * aliqInter;
          double icmsStRecolher = Math.max(icmsStTotal - icmsProprio, 0d);
          totalIcmsSt += icmsStRecolher;
        }

        if (baseSt > 0d && aliqInterna > 0d && aliqInter > 0d) {
          double difalTotal = Math.max((baseSt * aliqInterna) - (baseSt * aliqInter), 0d);
          totalDifal += difalTotal;
          totalPartilhaOrigem += difalTotal * pOrigem;
          totalPartilhaDestino += difalTotal * pDestino;
        }

        if (baseFcp > 0d && aliqFcp > 0d) {
          totalFcp += baseFcp * aliqFcp;
        }
      }
    }

    return new TaxCalculationResult(
        totalServicos,
        icms,
        pis,
        cofins,
        totalImpostos,
        Math.round(totalIcmsSt),
        Math.round(totalDifal),
        Math.round(totalFcp),
        Math.round(totalPartilhaOrigem),
        Math.round(totalPartilhaDestino));
  }

  /**
   * ⚠️ Aliquota zero <b>ou negativa</b> devolve zero, sem estourar. Do original: uma aliquota
   * negativa e silenciosamente ignorada em vez de virar credito.
   */
  private long applyRate(long baseAmountCents, BigDecimal rate) {
    if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) return 0L;
    return BigDecimal.valueOf(baseAmountCents)
        .multiply(rate)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        .setScale(0, RoundingMode.HALF_UP)
        .longValue();
  }

  private double valueOrZero(Double value) {
    return value == null || value < 0d ? 0d : value;
  }

  private double percentOrZero(Double percentValue) {
    if (percentValue == null || percentValue <= 0d) return 0d;
    return percentValue / 100d;
  }

  public record TaxCalculationResult(
      long totalServicos,
      long valorIcms,
      long valorPis,
      long valorCofins,
      long totalImpostos,
      long valorIcmsSt,
      long valorDifal,
      long valorFcp,
      long valorPartilhaOrigem,
      long valorPartilhaDestino) {}
}
