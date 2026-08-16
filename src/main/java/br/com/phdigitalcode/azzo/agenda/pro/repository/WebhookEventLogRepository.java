package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WebhookEventLog;

/** Espelha {@code domain/repository/WebhookEventLogRepository.java}. */
@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {

  long countByIdempotencyKey(String idempotencyKey);

  List<WebhookEventLog> findByIdempotencyKey(String idempotencyKey);

  /** Igual ao original: chave nula/em branco nunca conta como duplicada, e a busca usa {@code trim()}. */
  default boolean existsIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return false;
    return countByIdempotencyKey(idempotencyKey.trim()) > 0;
  }

  default Optional<WebhookEventLog> buscarPorIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
    return findByIdempotencyKey(idempotencyKey.trim()).stream().findFirst();
  }
}
