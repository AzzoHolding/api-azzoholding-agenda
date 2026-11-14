package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ConsumoDePontos;

@Repository
public interface ConsumoDePontosRepository extends JpaRepository<ConsumoDePontos, UUID> {

  /** O que os resgates desta comanda usaram e ainda nao voltou, do mais novo ao mais antigo. */
  List<ConsumoDePontos> findByTenantIdAndComandaIdAndDevolvidoFalseOrderByCreatedAtDesc(UUID tenantId, UUID comandaId);
}
