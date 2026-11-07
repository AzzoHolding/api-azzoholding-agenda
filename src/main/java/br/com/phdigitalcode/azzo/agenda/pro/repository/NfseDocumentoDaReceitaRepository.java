package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseDocumentoDaReceitaEntity;

@Repository
public interface NfseDocumentoDaReceitaRepository extends JpaRepository<NfseDocumentoDaReceitaEntity, UUID> {

  boolean existsByTenantIdAndAmbienteAndNsu(UUID tenantId, String ambiente, Long nsu);

  Optional<NfseDocumentoDaReceitaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  /** As NFS-e do ambiente (sem os eventos), mais recentes primeiro. */
  @Query(
      "from NfseDocumentoDaReceitaEntity d where d.tenantId = :tenantId and d.ambiente = :ambiente"
          + " and d.tipoDocumento = 'NFSE'"
          + " and (:papel is null or d.papel = :papel)"
          + " and (cast(:de as date) is null or d.competencia >= :de)"
          + " and (cast(:ate as date) is null or d.competencia <= :ate)"
          + " order by d.competencia desc nulls last, d.nsu desc")
  List<NfseDocumentoDaReceitaEntity> listarNotas(
      @Param("tenantId") UUID tenantId,
      @Param("ambiente") String ambiente,
      @Param("papel") String papel,
      @Param("de") LocalDate de,
      @Param("ate") LocalDate ate);

  /** Ha evento de cancelamento recebido para esta chave? (o evento pode chegar antes da nota). */
  @Query(
      "select count(d) > 0 from NfseDocumentoDaReceitaEntity d where d.tenantId = :tenantId"
          + " and d.ambiente = :ambiente and d.tipoDocumento = 'EVENTO' and d.chaveDoEvento = :chave"
          + " and d.cancelada = true")
  boolean existeCancelamentoPara(
      @Param("tenantId") UUID tenantId, @Param("ambiente") String ambiente, @Param("chave") String chave);

  @Modifying
  @Query(
      "update NfseDocumentoDaReceitaEntity d set d.cancelada = true where d.tenantId = :tenantId"
          + " and d.ambiente = :ambiente and d.tipoDocumento = 'NFSE' and d.chaveAcesso = :chave")
  int marcarCancelada(
      @Param("tenantId") UUID tenantId, @Param("ambiente") String ambiente, @Param("chave") String chave);

  /** Notas de producao emitidas pelo salao, validas, na competencia — para a apuracao mensal. */
  @Query(
      "from NfseDocumentoDaReceitaEntity d where d.tenantId = :tenantId and d.ambiente = 'PRODUCAO'"
          + " and d.tipoDocumento = 'NFSE' and d.papel = 'PRESTADOR' and d.cancelada = false"
          + " and d.competencia between :de and :ate")
  List<NfseDocumentoDaReceitaEntity> emitidasPeloSalaoNaCompetencia(
      @Param("tenantId") UUID tenantId, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

  /** Chaves que tambem estao em nfse_invoices: essas foram emitidas pelo Azzo. */
  @Query(
      "select i.chaveAcessoNfse from NfseInvoiceEntity i where i.tenantId = :tenantId"
          + " and i.chaveAcessoNfse in :chaves")
  List<String> chavesEmitidasPeloAzzo(
      @Param("tenantId") UUID tenantId, @Param("chaves") Collection<String> chaves);
}
