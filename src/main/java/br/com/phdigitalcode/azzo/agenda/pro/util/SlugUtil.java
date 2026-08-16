package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.text.Normalizer;

/** Porte verbatim de {@code shared/SlugUtil.java}. */
public final class SlugUtil {
  private SlugUtil() {}

  public static String gerarSlug(String texto) {
    if (texto == null) return null;
    String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    String slug = normalizado.toLowerCase()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    return slug.isBlank() ? "salao" : slug;
  }
}
