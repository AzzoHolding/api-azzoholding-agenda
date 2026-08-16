package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EstoqueConfiguracao;

/**
 * Espelha {@code modules/inventory/domain/repository/EstoqueConfiguracaoRepository.java} (que no
 * original nao declara metodo proprio — o service usa {@code find("tenantId", ...)} direto).
 *
 * <p>Ausencia de linha significa "usar os defaults", e nao erro: o motor de movimentacao trata
 * {@code Optional.empty()} como bloqueio de saida sem saldo <b>ativo</b>, igual ao original.
 */
@Repository
public interface EstoqueConfiguracaoRepository extends JpaRepository<EstoqueConfiguracao, UUID> {

  Optional<EstoqueConfiguracao> findByTenantId(UUID tenantId);
}
