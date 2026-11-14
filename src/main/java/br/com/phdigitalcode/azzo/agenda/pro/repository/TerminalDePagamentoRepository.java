package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TerminalDePagamento;

@Repository
public interface TerminalDePagamentoRepository extends JpaRepository<TerminalDePagamento, UUID> {

  List<TerminalDePagamento> findByTenantIdAndContaIdAndAtivoTrue(UUID tenantId, UUID contaId);

  Optional<TerminalDePagamento> findByIdAndTenantIdAndAtivoTrue(UUID id, UUID tenantId);

  Optional<TerminalDePagamento> findByContaIdAndSerial(UUID contaId, String serial);

  Optional<TerminalDePagamento> findByTokenHashAndAtivoTrue(String tokenHash);

  Optional<TerminalDePagamento> findByCodigoPareamentoHashAndAtivoTrue(String codigoPareamentoHash);
}
