package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalMunicipalityEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalStateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalMunicipalityRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalStateRepository;

/**
 * Espelha {@code modules/nfse/application/NfseLocationService.java} verbatim. Fronteira 2 do porte
 * de {@code nfse} (ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26) — catalogo de UFs e
 * municipios (IBGE/TOM), sem I/O externo, so leitura de {@code fiscal_states}/
 * {@code fiscal_municipalities} (ja portados na Fronteira 1).
 */
@Service
public class NfseLocationService {

  private final FiscalStateRepository fiscalStateRepository;
  private final FiscalMunicipalityRepository fiscalMunicipalityRepository;

  public NfseLocationService(
      FiscalStateRepository fiscalStateRepository,
      FiscalMunicipalityRepository fiscalMunicipalityRepository) {
    this.fiscalStateRepository = fiscalStateRepository;
    this.fiscalMunicipalityRepository = fiscalMunicipalityRepository;
  }

  @Transactional(readOnly = true)
  public List<NfseDtos.FiscalState> listarEstados() {
    return fiscalStateRepository.listAllOrdered().stream().map(this::toStateDto).toList();
  }

  @Transactional(readOnly = true)
  public List<NfseDtos.FiscalMunicipality> listarMunicipios(String stateUf, String search, Integer limit) {
    int safeLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 1000);
    List<FiscalMunicipalityEntity> entities =
        search == null || search.isBlank()
            ? fiscalMunicipalityRepository.listByStateUf(stateUf)
            : fiscalMunicipalityRepository.searchByStateUfAndName(stateUf, search, safeLimit);
    if (search == null || search.isBlank()) {
      return entities.stream().limit(safeLimit).map(this::toMunicipalityDto).toList();
    }
    return entities.stream().map(this::toMunicipalityDto).toList();
  }

  private NfseDtos.FiscalState toStateDto(FiscalStateEntity entity) {
    NfseDtos.FiscalState dto = new NfseDtos.FiscalState();
    dto.codigoIbge = entity.getCodigoIbge();
    dto.uf = entity.getUf();
    dto.nome = entity.getNome();
    dto.regiaoSigla = entity.getRegiaoSigla();
    dto.regiaoNome = entity.getRegiaoNome();
    return dto;
  }

  private NfseDtos.FiscalMunicipality toMunicipalityDto(FiscalMunicipalityEntity entity) {
    NfseDtos.FiscalMunicipality dto = new NfseDtos.FiscalMunicipality();
    dto.codigoIbge = entity.getCodigoIbge();
    dto.nome = entity.getNome();
    dto.codigoTom = entity.getCodigoTom();
    dto.codigoTomDv = entity.getCodigoTomDv();
    dto.codigoTomComDv = entity.getCodigoTomComDv();
    if (entity.getState() != null) {
      dto.stateCodigoIbge = entity.getState().getCodigoIbge();
      dto.stateUf = entity.getState().getUf();
      dto.stateNome = entity.getState().getNome();
    }
    return dto;
  }
}
