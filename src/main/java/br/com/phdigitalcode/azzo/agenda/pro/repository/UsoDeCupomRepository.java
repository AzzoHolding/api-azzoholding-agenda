package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.UsoDeCupom;

@Repository
public interface UsoDeCupomRepository extends JpaRepository<UsoDeCupom, UUID> {

  @Query(value = "select count(*) from usos_de_cupom where cupom_id = :cupomId and client_id = :clientId"
      + " and status = 'ATIVO'", nativeQuery = true)
  long contarAtivosDoCliente(@Param("cupomId") UUID cupomId, @Param("clientId") UUID clientId);

  @Query(value = "select * from usos_de_cupom where comanda_id = :comandaId and status = 'ATIVO'", nativeQuery = true)
  Optional<UsoDeCupom> ativoDaComanda(@Param("comandaId") UUID comandaId);

  @Query(value = "select * from usos_de_cupom where appointment_id = :appointmentId and status = 'ATIVO'", nativeQuery = true)
  Optional<UsoDeCupom> ativoDoAgendamento(@Param("appointmentId") UUID appointmentId);

  List<UsoDeCupom> findTop100ByTenantIdAndCupomIdOrderByCreatedAtDesc(UUID tenantId, UUID cupomId);
}
