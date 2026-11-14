package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TaxaDePagamento;

public interface TaxaDePagamentoRepository extends JpaRepository<TaxaDePagamento, UUID> {

  List<TaxaDePagamento> findByContaIdOrderByMeioAscParcelasDeAsc(UUID contaId);

  @Modifying
  void deleteByContaId(UUID contaId);
}
