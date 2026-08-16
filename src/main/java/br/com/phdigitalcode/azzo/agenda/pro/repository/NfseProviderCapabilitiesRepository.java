package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseProviderCapabilitiesEntity;

/** Espelha {@code modules/nfse/domain/repository/NfseProviderCapabilitiesRepository.java}. */
@Repository
public interface NfseProviderCapabilitiesRepository
    extends JpaRepository<NfseProviderCapabilitiesEntity, UUID> {

  Optional<NfseProviderCapabilitiesEntity> findByMunicipioCodigoIbgeAndProvedor(
      String municipioCodigoIbge, String provedor);

  List<NfseProviderCapabilitiesEntity> findByMunicipioCodigoIbgeAndProvedorOrderByUpdatedAtDesc(
      String municipioCodigoIbge, String provedor);

  List<NfseProviderCapabilitiesEntity> findByMunicipioCodigoIbgeOrderByUpdatedAtDesc(
      String municipioCodigoIbge);

  List<NfseProviderCapabilitiesEntity> findByProvedorOrderByUpdatedAtDesc(String provedor);

  List<NfseProviderCapabilitiesEntity> findAllByOrderByUpdatedAtDesc();

  /**
   * Ordem de preferencia: ABRASF (padrao nacional) primeiro, depois SEFIN_NACIONAL, depois
   * qualquer outro — mesmo {@code CASE} do original.
   */
  @Query(
      "from NfseProviderCapabilitiesEntity e where e.municipioCodigoIbge = :municipio"
          + " order by case e.provedor when 'ABRASF' then 1 when 'SEFIN_NACIONAL' then 2 else 3 end,"
          + " e.updatedAt desc")
  List<NfseProviderCapabilitiesEntity> listOrderedByPreference(
      @Param("municipio") String municipioCodigoIbge,
      org.springframework.data.domain.Pageable pageable);

  default Optional<NfseProviderCapabilitiesEntity> findByMunicipioProvedor(
      String municipioCodigoIbge, String provedor) {
    if (municipioCodigoIbge == null
        || municipioCodigoIbge.isBlank()
        || provedor == null
        || provedor.isBlank()) {
      return Optional.empty();
    }
    return findByMunicipioCodigoIbgeAndProvedor(municipioCodigoIbge.trim(), provedor.trim());
  }

  default Optional<String> findPreferredProviderForMunicipio(String municipioCodigoIbge) {
    if (municipioCodigoIbge == null || municipioCodigoIbge.isBlank()) return Optional.empty();
    List<NfseProviderCapabilitiesEntity> result =
        listOrderedByPreference(
            municipioCodigoIbge.trim(), org.springframework.data.domain.PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0).getProvedor());
  }

  default List<NfseProviderCapabilitiesEntity> listByMunicipioAndProvedor(
      String municipioCodigoIbge, String provedor) {
    boolean hasMunicipio = municipioCodigoIbge != null && !municipioCodigoIbge.isBlank();
    boolean hasProvedor = provedor != null && !provedor.isBlank();
    if (hasMunicipio && hasProvedor) {
      return findByMunicipioCodigoIbgeAndProvedorOrderByUpdatedAtDesc(
          municipioCodigoIbge.trim(), provedor.trim());
    }
    if (hasMunicipio) {
      return findByMunicipioCodigoIbgeOrderByUpdatedAtDesc(municipioCodigoIbge.trim());
    }
    if (hasProvedor) {
      return findByProvedorOrderByUpdatedAtDesc(provedor.trim());
    }
    return findAllByOrderByUpdatedAtDesc();
  }
}
