package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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

  /** O credito que uma venda gerou (para o estorno saber quanto dele ja foi usado). */
  Optional<MovimentoDePontos> findFirstByTenantIdAndComandaIdAndTipo(UUID tenantId, UUID comandaId, String tipo);
}
