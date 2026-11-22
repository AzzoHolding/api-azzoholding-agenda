package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RankingProfissionais;

@Repository
public interface RankingProfissionaisRepository extends JpaRepository<RankingProfissionais, UUID> {

  @Query("select r from RankingProfissionais r where r.tenantId = :tenantId and r.ativo = true order by lower(r.nome)")
  List<RankingProfissionais> ativosDoSalao(@Param("tenantId") UUID tenantId);

  Optional<RankingProfissionais> findByIdAndTenantIdAndAtivoTrue(UUID id, UUID tenantId);

  @Query("select count(r) > 0 from RankingProfissionais r where r.tenantId = :tenantId and r.ativo = true "
      + "and lower(r.nome) = lower(:nome) and (:ignorar is null or r.id <> :ignorar)")
  boolean nomeEmUso(@Param("tenantId") UUID tenantId, @Param("nome") String nome, @Param("ignorar") UUID ignorar);
}
