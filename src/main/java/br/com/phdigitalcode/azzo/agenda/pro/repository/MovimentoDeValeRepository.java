package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentoDeVale;

@Repository
public interface MovimentoDeValeRepository extends JpaRepository<MovimentoDeVale, UUID> {

  List<MovimentoDeVale> findByValeIdOrderByCreatedAtAsc(UUID valeId);

  boolean existsByValeIdAndTipo(UUID valeId, String tipo);
}
