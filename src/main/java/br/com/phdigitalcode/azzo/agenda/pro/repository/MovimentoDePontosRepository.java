package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentoDePontos;

@Repository
public interface MovimentoDePontosRepository extends JpaRepository<MovimentoDePontos, UUID> {

  /** Creditos com pontos sobrando: o que vence antes primeiro; sem vencimento por ultimo; depois o mais antigo. */
  @Query("select m from MovimentoDePontos m where m.tenantId = :tenantId and m.clientId = :clientId"
      + " and m.pontosRestantes > 0"
      + " order by case when m.expiraEm is null then 1 else 0 end, m.expiraEm, m.createdAt")
  List<MovimentoDePontos> creditosComSaldo(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

  /** Creditos vencidos com pontos sobrando (rotina diaria de expiracao), de todos os saloes. */
  @Query("select m.id from MovimentoDePontos m where m.pontosRestantes > 0 and m.expiraEm is not null"
      + " and m.expiraEm <= :agora order by m.expiraEm")
  List<UUID> vencidos(@Param("agora") java.time.Instant agora, Limit limite);

  /** O que sobra do credito, lido do banco (depois de travar o saldo da cliente). */
  @Query(value = "select pontos_restantes from movimentos_de_pontos where id = :id", nativeQuery = true)
  Integer restantesDe(@Param("id") UUID id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "update movimentos_de_pontos set pontos_restantes = 0 where id = :id", nativeQuery = true)
  int zerarRestantes(@Param("id") UUID id);

  /** Extrato da ficha: os ultimos movimentos. */
  List<MovimentoDePontos> findTop50ByTenantIdAndClientIdOrderByCreatedAtDesc(UUID tenantId, UUID clientId);

  /** O credito que uma venda gerou (para o estorno saber quanto dele ja foi usado). */
  Optional<MovimentoDePontos> findFirstByTenantIdAndComandaIdAndTipo(UUID tenantId, UUID comandaId, String tipo);
}
