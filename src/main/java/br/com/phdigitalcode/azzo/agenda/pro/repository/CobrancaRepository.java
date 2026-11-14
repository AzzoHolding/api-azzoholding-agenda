package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cobranca;

@Repository
public interface CobrancaRepository extends JpaRepository<Cobranca, UUID> {

  Optional<Cobranca> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<Cobranca> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

  Optional<Cobranca> findByProvedorAndIdNaOperadora(String provedor, String idNaOperadora);

  List<Cobranca> findByTenantIdAndOrigemTipoAndOrigemIdOrderByCreatedAtAsc(UUID tenantId, String origemTipo, UUID origemId);

  /** Pagas (ou estornadas em parte) com o dinheiro previsto para cair no periodo. */
  @Query("select c from Cobranca c where c.tenantId = :tenantId and c.status in ('PAGA', 'ESTORNO_PARCIAL') "
      + "and c.previsaoRecebimento between :de and :ate order by c.previsaoRecebimento asc, c.pagaEm asc")
  List<Cobranca> recebiveis(@Param("tenantId") UUID tenantId, @Param("de") java.time.LocalDate de,
      @Param("ate") java.time.LocalDate ate);

  @Query("select count(c) from Cobranca c where c.tenantId = :tenantId and c.status in ('PAGA', 'ESTORNO_PARCIAL') "
      + "and c.previsaoRecebimento is null and c.origemTipo <> 'TESTE'")
  long pagasSemPrevisao(@Param("tenantId") UUID tenantId);

  /** A proxima venda da fila do Azzo Conector: do terminal, esperando, ainda nao pega. */
  @Query("select c from Cobranca c where c.terminalId = :terminalId and c.provedor = 'TEF' and c.status in ('CRIADA', 'AGUARDANDO') "
      + "and c.conectorPegouEm is null order by c.createdAt asc")
  List<Cobranca> filaDoConector(@Param("terminalId") UUID terminalId, Limit limit);

  /** TEF sem resposta do Conector (computador desligado, pinpad sem cartao): expira. */
  @Query("select c from Cobranca c where c.provedor = 'TEF' and c.status in ('CRIADA', 'AGUARDANDO') and c.createdAt < :antesDe")
  List<Cobranca> tefSemResposta(@Param("antesDe") Instant antesDe, Limit limit);

  Optional<Cobranca> findFirstByTerminalIdAndNsuOrderByCreatedAtDesc(UUID terminalId, String nsu);

  /** As que ainda esperam resposta, numa janela de tempo: a consulta de reserva olha estas. */
  @Query("select c from Cobranca c where c.status in ('CRIADA', 'AGUARDANDO') and c.idNaOperadora is not null "
      + "and c.createdAt < :antesDe and c.createdAt > :depoisDe order by c.createdAt asc")
  List<Cobranca> pendentesParaConsultar(
      @Param("antesDe") Instant antesDe, @Param("depoisDe") Instant depoisDe, Limit limit);
}
