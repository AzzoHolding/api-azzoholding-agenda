package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseProviderCapabilitiesEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCancelMode;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseProviderCapabilitiesRepository;

/**
 * Espelha a fatia de CRUD de {@code nfse_provider_capabilities} de
 * {@code modules/nfse/application/NfseService.java} (metodos {@code listarProviderCapabilities}/
 * {@code salvarProviderCapabilities} + helpers privados que eles usam). Fronteira 2 do porte de
 * {@code nfse} — extraida como servico proprio pelo mesmo motivo de {@link NfseConfigService}: CRUD
 * puro, sem I/O externo, sem XML/assinatura/mTLS.
 *
 * <p>Panache {@code isPersistent(entity)} + {@code persist()} condicional vira sempre
 * {@code save(entity)} (mesmo padrao de {@link NfseConfigService}).
 */
@Service
public class NfseProviderCapabilitiesService {

  private final NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository;

  public NfseProviderCapabilitiesService(
      NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository) {
    this.nfseProviderCapabilitiesRepository = nfseProviderCapabilitiesRepository;
  }

  @Transactional(readOnly = true)
  public List<NfseDtos.ProviderCapabilities> listarProviderCapabilities(
      String municipioCodigoIbge, String provedor) {
    return nfseProviderCapabilitiesRepository
        .listByMunicipioAndProvedor(normalizeOrNull(municipioCodigoIbge), normalizeOrNull(provedor))
        .stream()
        .map(this::toProviderCapabilitiesDto)
        .toList();
  }

  @Transactional
  public NfseDtos.ProviderCapabilities salvarProviderCapabilities(NfseDtos.ProviderCapabilities request) {
    if (request == null) {
      throw new IllegalArgumentException("Capacidades do provedor sao obrigatorias.");
    }
    if (isBlank(request.municipioCodigoIbge)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_CAP_MISSING_MUNICIPIO");
    }
    if (isBlank(request.provedor)) throw new IllegalArgumentException("NFSE_PROVIDER_CAP_MISSING_PROVEDOR");
    if (isBlank(request.layoutVersion)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_CAP_MISSING_LAYOUT_VERSION");
    }

    NfseProviderCapabilitiesEntity entity =
        nfseProviderCapabilitiesRepository
            .findByMunicipioProvedor(request.municipioCodigoIbge, request.provedor)
            .orElseGet(NfseProviderCapabilitiesEntity::new);
    entity.setMunicipioCodigoIbge(normalize(request.municipioCodigoIbge));
    entity.setProvedor(normalize(request.provedor));
    entity.setLayoutVersion(normalize(request.layoutVersion));
    entity.setCancelSupported(request.cancelSupported);
    entity.setCancelWindowHours(request.cancelSupported ? request.cancelWindowHours : null);
    entity.setCancelMode(parseCancelMode(request.cancelMode));
    entity.setAcceptedCancelReasonCodes(normalizeOrNull(request.acceptedCancelReasonCodes));

    nfseProviderCapabilitiesRepository.save(entity);
    return toProviderCapabilitiesDto(entity);
  }

  private NfseDtos.ProviderCapabilities toProviderCapabilitiesDto(NfseProviderCapabilitiesEntity entity) {
    NfseDtos.ProviderCapabilities dto = new NfseDtos.ProviderCapabilities();
    dto.municipioCodigoIbge = entity.getMunicipioCodigoIbge();
    dto.provedor = entity.getProvedor();
    dto.layoutVersion = entity.getLayoutVersion();
    dto.cancelSupported = entity.isCancelSupported();
    dto.cancelWindowHours = entity.getCancelWindowHours();
    dto.cancelMode = entity.getCancelMode() != null ? entity.getCancelMode().name() : null;
    dto.acceptedCancelReasonCodes = entity.getAcceptedCancelReasonCodes();
    dto.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    dto.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return dto;
  }

  private NfseCancelMode parseCancelMode(String value) {
    String raw = isBlank(value) ? NfseCancelMode.SYNC.name() : value;
    try {
      return NfseCancelMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Cancel mode NFS-e invalido.");
    }
  }

  private String normalize(String value) {
    if (isBlank(value)) throw new IllegalArgumentException("Campo obrigatorio ausente.");
    return value.trim();
  }

  private String normalizeOrNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
