package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Porte verbatim de {@code shared/DataUtil.java}. */
public final class DataUtil {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

  private DataUtil() {}

  public static LocalDate parseDataISO(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      // aceita "2026-02-14" ou "2026-02-14T00:00:00.000Z"
      if (valor.length() >= 10) {
        return LocalDate.parse(valor.substring(0, 10));
      }
      return LocalDate.parse(valor);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Data invalida (use yyyy-MM-dd): " + valor);
    }
  }

  public static Instant parseInstantISO(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      return Instant.parse(valor);
    } catch (Exception e) {
      try {
        return OffsetDateTime.parse(valor).toInstant();
      } catch (Exception ignored) {
        // continua para formatos sem offset
      }
      try {
        return LocalDateTime.parse(valor).atZone(DEFAULT_ZONE).toInstant();
      } catch (Exception ignored) {
        // continua para date-only
      }
      LocalDate d = parseDataISO(valor);
      return d.atStartOfDay(DEFAULT_ZONE).toInstant();
    }
  }
}
