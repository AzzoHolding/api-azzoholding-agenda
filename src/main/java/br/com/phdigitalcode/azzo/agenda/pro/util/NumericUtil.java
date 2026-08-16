package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Porte verbatim de {@code shared/NumericUtil.java}. */
public final class NumericUtil {

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private NumericUtil() {}

  public static BigDecimal zero() {
    return ZERO;
  }

  public static BigDecimal normalize(BigDecimal value) {
    return value == null ? zero() : value.setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal fromCents(long cents) {
    return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal fromCents(Number cents) {
    if (cents == null) return zero();
    return new BigDecimal(String.valueOf(cents)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
  }

  public static long toCents(BigDecimal value) {
    return normalize(value).movePointRight(2).longValueExact();
  }

  public static BigDecimal add(BigDecimal left, BigDecimal right) {
    return normalize(normalize(left).add(normalize(right)));
  }

  public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
    return normalize(normalize(left).subtract(normalize(right)));
  }

  public static BigDecimal multiply(BigDecimal value, int multiplier) {
    return normalize(normalize(value).multiply(BigDecimal.valueOf(multiplier)));
  }

  public static BigDecimal maxZero(BigDecimal value) {
    return normalize(value).max(zero());
  }

  public static BigDecimal percentOf(BigDecimal baseValue, double percentValue) {
    if (baseValue == null || percentValue <= 0.0d) return zero();
    return normalize(normalize(baseValue)
        .multiply(BigDecimal.valueOf(percentValue))
        .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP));
  }

  public static boolean isPositive(BigDecimal value) {
    return value != null && normalize(value).compareTo(zero()) > 0;
  }

  public static boolean isZeroOrNegative(BigDecimal value) {
    return value == null || normalize(value).compareTo(zero()) <= 0;
  }
}
