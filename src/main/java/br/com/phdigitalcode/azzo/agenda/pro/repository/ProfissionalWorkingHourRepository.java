package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;

/** Espelha {@code modules/professionals/domain/repository/ProfissionalWorkingHourRepository.java}. */
@Repository
public interface ProfissionalWorkingHourRepository extends JpaRepository<ProfissionalWorkingHour, UUID> {

  @Query(
      "select w from ProfissionalWorkingHour w where w.tenantId = :tenantId and w.professionalId = :professionalId "
          + "order by w.dayOfWeek, w.startTime, w.endTime")
  List<ProfissionalWorkingHour> listByProfessional(UUID tenantId, UUID professionalId);

  /**
   * Usado pelo heatmap de ocupacao ({@code ServicoRelatorios.heatmap}) quando nenhum profissional
   * e informado — equivalente ao {@code list("tenantId = ?1 order by professionalId, dayOfWeek, ...")}
   * do original. JPQL nao tem {@code NULLS FIRST} portavel entre bancos; como {@code startTime}/
   * {@code endTime} sao opcionais e o original usa {@code nulls first}, ordenamos por
   * {@code professionalId, dayOfWeek} apenas — a ordem entre linhas com mesmo dia/profissional nao
   * e observavel no resultado (o heatmap agrega por dia da semana, nao depende da ordem fina).
   */
  @Query("select w from ProfissionalWorkingHour w where w.tenantId = :tenantId order by w.professionalId, w.dayOfWeek")
  List<ProfissionalWorkingHour> listByTenant(UUID tenantId);

  @Modifying
  @Transactional
  @Query("delete from ProfissionalWorkingHour w where w.tenantId = :tenantId and w.professionalId = :professionalId")
  void deleteByProfessional(UUID tenantId, UUID professionalId);
}
