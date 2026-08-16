package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Porte de {@code shared/JsonUtil.java} — mesmo contrato, mesma excecao
 * ({@link IllegalStateException}) e mesmo tratamento de nulo/branco (lista vazia, nunca null).
 */
@Component
public class JsonUtil {

  private final ObjectMapper objectMapper;

  public JsonUtil(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public <T> String paraJson(List<T> lista) {
    try {
      return objectMapper.writeValueAsString(lista == null ? Collections.emptyList() : lista);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar JSON", e);
    }
  }

  public <T> List<T> deJsonLista(String json, TypeReference<List<T>> tipo) {
    try {
      if (json == null || json.isBlank()) return Collections.emptyList();
      return objectMapper.readValue(json, tipo);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao desserializar JSON", e);
    }
  }
}
