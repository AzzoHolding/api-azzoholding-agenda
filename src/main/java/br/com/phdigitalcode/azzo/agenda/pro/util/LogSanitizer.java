package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogSanitizer {

  private static final Pattern CPF_PATTERN =
      Pattern.compile("(?<!\\d)(\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})(?!\\d)");
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("(?i)\\b([a-z0-9._%+-]+)@([a-z0-9.-]+\\.[a-z]{2,})\\b");
  private static final Pattern PHONE_PATTERN =
      Pattern.compile("(?<!\\d)(\\+?\\d[\\d\\s().-]{8,}\\d)(?!\\d)");

  private LogSanitizer() {}

  public static String sanitizeCpf(String value) {
    if (value == null) return null;
    String digits = value.replaceAll("\\D", "");
    if (digits.length() != 11) return value;
    return "***.***." + digits.substring(6, 9) + "-" + digits.substring(9);
  }

  public static String sanitizeEmail(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    int atIndex = trimmed.indexOf('@');
    if (atIndex <= 0 || atIndex == trimmed.length() - 1) return value;

    String localPart = trimmed.substring(0, atIndex);
    String domainPart = trimmed.substring(atIndex + 1);
    if (domainPart.isBlank()) return value;

    return localPart.charAt(0) + "***@" + domainPart;
  }

  public static String sanitizePhone(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    boolean hasPlus = trimmed.startsWith("+");
    String digits = trimmed.replaceAll("\\D", "");
    if (digits.length() < 10) return value;

    String country = "";
    String local = digits;
    if (digits.startsWith("55") && digits.length() >= 12) {
      country = "55";
      local = digits.substring(2);
    }
    if (local.length() < 6) return value;

    String area = local.substring(0, Math.min(2, local.length()));
    String last4 = local.substring(local.length() - 4);
    String prefix = hasPlus && !country.isBlank() ? "+" + country + " " : "";
    return prefix + area + "*****" + last4;
  }

  public static String sanitizeLogMessage(String message) {
    if (message == null) return null;

    String sanitized = replaceWithSanitizer(message, CPF_PATTERN, LogSanitizer::sanitizeCpf);
    sanitized = replaceEmail(sanitized);
    sanitized = replaceWithSanitizer(sanitized, PHONE_PATTERN, LogSanitizer::sanitizePhone);
    return sanitized;
  }

  private static String replaceEmail(String value) {
    Matcher matcher = EMAIL_PATTERN.matcher(value);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String fullEmail = matcher.group(1) + "@" + matcher.group(2);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(sanitizeEmail(fullEmail)));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String replaceWithSanitizer(String value, Pattern pattern, java.util.function.Function<String, String> sanitizer) {
    Matcher matcher = pattern.matcher(value);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String original = matcher.group(1);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(sanitizer.apply(original)));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }
}
