package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

/**
 * Quais saloes usam os pagamentos integrados (piloto, decisao do dono de 14/11/2026).
 *
 * <p>{@code app.pagamentos.saloes-piloto} (variavel {@code PAGAMENTOS_SALOES_PILOTO}): ids separados
 * por virgula; {@code *} libera todos; vazio, nenhum. Fora do piloto o salao nao conecta conta, e o
 * PDV nao oferece nada integrado — fica exatamente como antes. Liberar para todos depois e trocar o
 * valor para {@code *} e reiniciar a API; nada no banco muda.
 */
@Component
public class LiberacaoDePagamentos {

  private final boolean todos;
  private final Set<UUID> saloes;

  public LiberacaoDePagamentos(@Value("${app.pagamentos.saloes-piloto:}") String saloesPiloto) {
    String valor = saloesPiloto == null ? "" : saloesPiloto.trim();
    this.todos = "*".equals(valor);
    this.saloes = todos ? Set.of() : Arrays.stream(valor.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(LiberacaoDePagamentos::uuidOuNulo)
        .filter(id -> id != null)
        .collect(Collectors.toUnmodifiableSet());
  }

  public boolean liberado(UUID tenantId) {
    return todos || (tenantId != null && saloes.contains(tenantId));
  }

  /** Para as acoes que so existem com os pagamentos integrados (conectar conta, taxas, conector). */
  public void exigir(UUID tenantId) {
    if (!liberado(tenantId)) {
      throw new ApiClientErrorException("Os pagamentos integrados ainda não estão liberados para este salão.", 403);
    }
  }

  private static UUID uuidOuNulo(String s) {
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
