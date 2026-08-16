package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEntity;
import jakarta.persistence.LockModeType;

/**
 * Espelha {@code modules/fiscal/domain/repository/FiscalInvoiceRepository.java}.
 *
 * <p>A guarda de argumento do original (tenant nulo, externalInvoiceId nulo/branco e o
 * {@code trim()}) fica no {@code default} de {@link #findByTenantAndExternalInvoiceId} e
 * {@link #findByTenantAndExternalInvoiceIdForUpdate} — as queries em si nao aceitam nulo.
 *
 * <p>⚠️ Metodos {@code default} de interface <b>nao sao interceptados em mock do Mockito</b>: em
 * teste, stube o metodo {@code ...Interno} correspondente, nao o {@code default}.
 *
 * <p>{@link JpaSpecificationExecutor} esta aqui porque a listagem paginada de notas (fronteira 3)
 * filtra por campos opcionais — {@code :param is null} em JPQL quebra no PostgreSQL.
 */
@Repository
public interface FiscalInvoiceRepository
    extends JpaRepository<FiscalInvoiceEntity, UUID>, JpaSpecificationExecutor<FiscalInvoiceEntity> {

  @Query(
      "select i from FiscalInvoiceEntity i"
          + " where i.tenantId = :tenantId and i.externalInvoiceId = :externalInvoiceId")
  Optional<FiscalInvoiceEntity> buscarPorExternalInvoiceId(
      @Param("tenantId") UUID tenantId, @Param("externalInvoiceId") String externalInvoiceId);

  /** Mesma consulta com {@code PESSIMISTIC_WRITE}, como o {@code withLock} do original. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from FiscalInvoiceEntity i"
          + " where i.tenantId = :tenantId and i.externalInvoiceId = :externalInvoiceId")
  Optional<FiscalInvoiceEntity> buscarPorExternalInvoiceIdParaAtualizacao(
      @Param("tenantId") UUID tenantId, @Param("externalInvoiceId") String externalInvoiceId);

  default Optional<FiscalInvoiceEntity> findByTenantAndExternalInvoiceId(
      UUID tenantId, String externalInvoiceId) {
    if (tenantId == null || externalInvoiceId == null || externalInvoiceId.isBlank()) {
      return Optional.empty();
    }
    return buscarPorExternalInvoiceId(tenantId, externalInvoiceId.trim());
  }

  default Optional<FiscalInvoiceEntity> findByTenantAndExternalInvoiceIdForUpdate(
      UUID tenantId, String externalInvoiceId) {
    if (tenantId == null || externalInvoiceId == null || externalInvoiceId.isBlank()) {
      return Optional.empty();
    }
    return buscarPorExternalInvoiceIdParaAtualizacao(tenantId, externalInvoiceId.trim());
  }

  @Query(
      "select count(i) from FiscalInvoiceEntity i"
          + " where i.tenantId = :tenantId and i.modelo = :modelo and i.serie = :serie"
          + "   and i.numero = :numero and i.ambiente = :ambiente")
  long contarPorNumeroFiscal(
      @Param("tenantId") UUID tenantId,
      @Param("modelo") String modelo,
      @Param("serie") int serie,
      @Param("numero") int numero,
      @Param("ambiente") String ambiente);

  @Query(
      "select count(i) from FiscalInvoiceEntity i"
          + " where i.tenantId = :tenantId and i.modelo = :modelo and i.serie = :serie"
          + "   and i.numero = :numero and i.ambiente = :ambiente"
          + "   and i.externalInvoiceId <> :externalInvoiceIdToIgnore")
  long contarPorNumeroFiscalIgnorando(
      @Param("tenantId") UUID tenantId,
      @Param("modelo") String modelo,
      @Param("serie") int serie,
      @Param("numero") int numero,
      @Param("ambiente") String ambiente,
      @Param("externalInvoiceIdToIgnore") String externalInvoiceIdToIgnore);

  /**
   * Guarda do original preservada na integra: qualquer argumento invalido devolve <b>false</b> —
   * ou seja, "numero livre" — em vez de estourar. Vale inclusive para {@code numero <= 0} e
   * {@code serie <= 0}.
   */
  default boolean existsByFiscalNumber(
      UUID tenantId,
      String modelo,
      int serie,
      int numero,
      String ambiente,
      String externalInvoiceIdToIgnore) {
    if (tenantId == null || modelo == null || ambiente == null || numero <= 0 || serie <= 0) {
      return false;
    }
    long count;
    if (externalInvoiceIdToIgnore == null || externalInvoiceIdToIgnore.isBlank()) {
      count = contarPorNumeroFiscal(tenantId, modelo, serie, numero, ambiente);
    } else {
      count =
          contarPorNumeroFiscalIgnorando(
              tenantId, modelo, serie, numero, ambiente, externalInvoiceIdToIgnore);
    }
    return count > 0;
  }
}
