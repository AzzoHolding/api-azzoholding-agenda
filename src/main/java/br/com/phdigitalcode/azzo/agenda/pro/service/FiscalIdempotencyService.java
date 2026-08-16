package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalIdempotencyRequestEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalIdempotencyRequestRepository;

/**
 * Espelha {@code modules/fiscal/application/FiscalIdempotencyService.java}.
 *
 * <p>Envolve uma operacao fiscal de escrita para que o mesmo {@code Idempotency-Key} devolva a
 * resposta ja gravada em vez de emitir outro documento. A resposta e guardada <b>serializada em
 * JSON</b>, e reconstruida no replay via {@code responseType} — por isso o tipo precisa ser
 * desserializavel pelo Jackson sem informacao de generico.
 *
 * <p><b>Duas assimetrias do original, preservadas:</b>
 *
 * <ul>
 *   <li><b>Argumento faltando degrada para execucao direta, sem erro.</b> Sem {@code tenantId},
 *       {@code operation} ou {@code idempotencyKey} o supplier roda e nada e gravado — a requisicao
 *       simplesmente perde a protecao. So {@code supplier} e {@code responseType} nulos estouram.
 *   <li><b>A operacao e normalizada, a chave nao.</b> {@code operation} recebe {@code trim} +
 *       maiuscula; {@code idempotencyKey} recebe so {@code trim}, entao a chave e sensivel a caixa.
 * </ul>
 *
 * <p><b>Adaptacao a armadilha 3:</b> o {@code persist()} do Panache emite o INSERT na hora, o que
 * faz duas requisicoes concorrentes com a mesma chave colidirem na unique ali mesmo.
 * {@code save()} adiaria ate o commit e deixaria as duas emitirem documento — dai
 * {@code saveAndFlush}.
 *
 * <p>O original usa {@code toUpperCase()} sem locale; mantido aqui para nao alterar a chave gravada
 * em linhas ja existentes. As operacoes sao ASCII, entao so um locale turco veria diferenca.
 */
@Service
public class FiscalIdempotencyService {

  private final FiscalIdempotencyRequestRepository fiscalIdempotencyRequestRepository;
  private final ObjectMapper objectMapper;

  public FiscalIdempotencyService(
      FiscalIdempotencyRequestRepository fiscalIdempotencyRequestRepository,
      ObjectMapper objectMapper) {
    this.fiscalIdempotencyRequestRepository = fiscalIdempotencyRequestRepository;
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

    FiscalIdempotencyRequestEntity existing =
        fiscalIdempotencyRequestRepository
            .findByKey(tenantId, normalizedOperation, normalizedKey)
            .orElse(null);
    if (existing != null) {
      return fromJson(existing.getResponseJson(), responseType);
    }

    T response = supplier.get();
    FiscalIdempotencyRequestEntity entity = new FiscalIdempotencyRequestEntity();
    entity.setTenantId(tenantId);
    entity.setOperation(normalizedOperation);
    entity.setIdempotencyKey(normalizedKey);
    entity.setResponseJson(toJson(response));
    fiscalIdempotencyRequestRepository.saveAndFlush(entity);
    return response;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar resposta idempotente fiscal", e);
    }
  }

  private <T> T fromJson(String value, Class<T> responseType) {
    try {
      return objectMapper.readValue(value, responseType);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao desserializar resposta idempotente fiscal", e);
    }
  }
}
