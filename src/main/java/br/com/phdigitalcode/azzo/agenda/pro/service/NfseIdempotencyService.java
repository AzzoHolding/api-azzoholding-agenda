package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseIdempotencyRequestEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseIdempotencyRequestRepository;

/**
 * Espelha {@code modules/nfse/application/NfseIdempotencyService.java} verbatim. Fronteira 2 do
 * porte de {@code nfse} (ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26). Mesma logica e
 * mesmas assimetrias ja documentadas em {@code FiscalIdempotencyService} (equivalente em
 * {@code fiscal}, ja portado):
 *
 * <ul>
 *   <li>Argumento faltando ({@code tenantId}/{@code operation}/{@code idempotencyKey}) degrada
 *       para execucao direta do supplier, sem gravar nada e sem erro — so {@code supplier}/
 *       {@code responseType} nulos estouram.
 *   <li>{@code operation} e normalizada para maiuscula; {@code idempotencyKey} so recebe
 *       {@code trim} (sensivel a caixa).
 *   <li>Panache {@code persist()} emite INSERT na hora; aqui {@code saveAndFlush} para preservar o
 *       mesmo comportamento sob concorrencia (armadilha 3 do briefing).
 * </ul>
 */
@Service
public class NfseIdempotencyService {

  private final NfseIdempotencyRequestRepository nfseIdempotencyRequestRepository;
  private final ObjectMapper objectMapper;

  public NfseIdempotencyService(
      NfseIdempotencyRequestRepository nfseIdempotencyRequestRepository, ObjectMapper objectMapper) {
    this.nfseIdempotencyRequestRepository = nfseIdempotencyRequestRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public <T> T execute(
      UUID tenantId,
      String operation,
      String idempotencyKey,
      Supplier<T> supplier,
      Class<T> responseType) {
    if (supplier == null) throw new IllegalArgumentException("supplier obrigatorio");
    if (responseType == null) throw new IllegalArgumentException("responseType obrigatorio");
    if (tenantId == null
        || operation == null
        || operation.isBlank()
        || idempotencyKey == null
        || idempotencyKey.isBlank()) {
      return supplier.get();
    }

    String normalizedOperation = operation.trim().toUpperCase();
    String normalizedKey = idempotencyKey.trim();

    NfseIdempotencyRequestEntity existing =
        nfseIdempotencyRequestRepository.findByKey(tenantId, normalizedOperation, normalizedKey).orElse(null);
    if (existing != null) {
      return fromJson(existing.getResponseJson(), responseType);
    }

    T response = supplier.get();
    NfseIdempotencyRequestEntity entity = new NfseIdempotencyRequestEntity();
    entity.setTenantId(tenantId);
    entity.setOperation(normalizedOperation);
    entity.setIdempotencyKey(normalizedKey);
    entity.setResponseJson(toJson(response));
    nfseIdempotencyRequestRepository.saveAndFlush(entity);
    return response;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar resposta idempotente NFS-e", e);
    }
  }

  private <T> T fromJson(String value, Class<T> responseType) {
    try {
      return objectMapper.readValue(value, responseType);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao desserializar resposta idempotente NFS-e", e);
    }
  }
}
