package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalSequenceControlEntity;
import jakarta.persistence.LockModeType;

/**
 * Espelha {@code modules/fiscal/domain/repository/FiscalSequenceControlRepository.java}.
 *
 * <p>{@code PESSIMISTIC_WRITE} nao e detalhe: e ele que serializa a reserva do proximo numero de
 * nota entre requisicoes concorrentes do mesmo tenant/modelo/serie/ambiente.
 */
@Repository
public interface FiscalSequenceControlRepository
    extends JpaRepository<FiscalSequenceControlEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from FiscalSequenceControlEntity s"
          + " where s.tenantId = :tenantId and s.modelo = :modelo"
          + "   and s.serie = :serie and s.ambiente = :ambiente")
  Optional<FiscalSequenceControlEntity> findForUpdate(
      @Param("tenantId") UUID tenantId,
      @Param("modelo") String modelo,
      @Param("serie") int serie,
      @Param("ambiente") String ambiente);
}
