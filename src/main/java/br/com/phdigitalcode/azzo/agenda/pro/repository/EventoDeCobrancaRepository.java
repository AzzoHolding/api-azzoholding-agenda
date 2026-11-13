package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EventoDeCobranca;

@Repository
public interface EventoDeCobrancaRepository extends JpaRepository<EventoDeCobranca, UUID> {

  boolean existsByIdempotencyKey(String idempotencyKey);
}
