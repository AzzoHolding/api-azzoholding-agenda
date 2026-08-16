package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;

/** Espelha {@code modules/pos/domain/repository/ComandaPagamentoRepository.java}. */
@Repository
public interface ComandaPagamentoRepository extends JpaRepository<ComandaPagamento, UUID> {

  List<ComandaPagamento> findByComandaIdOrderByCreatedAt(UUID comandaId);

  Optional<ComandaPagamento> findByAsaasPaymentId(String asaasPaymentId);
}
