package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditTermsAcceptance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsLifecycleEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditTermsAcceptanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TermsLifecycleEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TermsVersionRepository;

/** Espelha {@code modules/audit/application/TermsService.java}. */
class TermsServiceTest {

  private TermsVersionRepository termsVersionRepository;
  private TermsLifecycleEventRepository termsLifecycleEventRepository;
  private AuditTermsAcceptanceRepository termsAcceptanceRepository;
  private TermsService service;

  @BeforeEach
  void setUp() {
    termsVersionRepository = mock(TermsVersionRepository.class);
    termsLifecycleEventRepository = mock(TermsLifecycleEventRepository.class);
    termsAcceptanceRepository = mock(AuditTermsAcceptanceRepository.class);
    service = new TermsService(
        termsVersionRepository, termsLifecycleEventRepository, termsAcceptanceRepository, new ObjectMapper());
  }

  private TermsVersion buildVersion(String documentType, String version) {
    TermsVersion terms = new TermsVersion();
    terms.setId(UUID.randomUUID());
    terms.setDocumentType(documentType);
    terms.setVersion(version);
    terms.setTitle("Titulo");
    terms.setContent("Conteudo");
    terms.setContentHash("hash");
    return terms;
  }

  @Test
  void publishVersionCriaVersaoEEventoDeCicloDeVida() {
    // Nota: o original faz o check de duplicidade com documentType.trim() SEM uppercase (assimetria
    // preexistente com o save(), que uppercasa) — preservado aqui, mesmo criterio de outras fronteiras.
    when(termsVersionRepository.findByDocumentTypeAndVersion("terms_of_use", "v1")).thenReturn(Optional.empty());
    UUID publishedBy = UUID.randomUUID();

    TermsVersion version = service.publishVersion("terms_of_use", "v1", "Titulo", "Conteudo", publishedBy);

    assertThat(version.getDocumentType()).isEqualTo("TERMS_OF_USE");
    assertThat(version.getVersion()).isEqualTo("v1");
    assertThat(version.getContentHash()).isNotBlank();
    assertThat(version.getPublishedBy()).isEqualTo(publishedBy);
    verify(termsVersionRepository).save(version);
    verify(termsLifecycleEventRepository).save(any(TermsLifecycleEvent.class));
  }

  @Test
  void publishVersionLancaQuandoJaExisteVersaoParaODocumentType() {
    when(termsVersionRepository.findByDocumentTypeAndVersion("terms_of_use", "v1"))
        .thenReturn(Optional.of(buildVersion("TERMS_OF_USE", "v1")));

    assertThatThrownBy(() -> service.publishVersion("terms_of_use", "v1", "Titulo", "Conteudo", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void publishVersionValidaCamposObrigatorios() {
    assertThatThrownBy(() -> service.publishVersion(null, "v1", "T", "C", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.publishVersion("TERMS_OF_USE", null, "T", "C", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.publishVersion("TERMS_OF_USE", "v1", null, "C", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.publishVersion("TERMS_OF_USE", "v1", "T", null, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void disableVersionCriaEventoDeDesativacaoComMotivo() {
    TermsVersion version = buildVersion("TERMS_OF_USE", "v1");
    when(termsVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
    UUID actor = UUID.randomUUID();

    TermsLifecycleEvent event = service.disableVersion(version.getId(), actor, "motivo qualquer");

    assertThat(event.getTermsVersionId()).isEqualTo(version.getId());
    assertThat(event.getEventType()).isEqualTo(AuditConstants.TermsEventType.DISABLED);
    assertThat(event.getCreatedBy()).isEqualTo(actor);
    assertThat(event.getEventMetadataJson()).contains("motivo qualquer");
    verify(termsLifecycleEventRepository).save(event);
  }

  @Test
  void disableVersionLancaQuandoVersaoNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(termsVersionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.disableVersion(id, UUID.randomUUID(), "motivo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptCriaAceiteComHashEIdempotencyKeyGeradaQuandoAusente() {
    TermsVersion version = buildVersion("TERMS_OF_USE", "v1");
    when(termsVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    AuditTermsAcceptance acceptance = service.accept(tenantId, userId, version.getId(), null, "203.0.113.1");

    assertThat(acceptance.getTenantId()).isEqualTo(tenantId);
    assertThat(acceptance.getUserId()).isEqualTo(userId);
    assertThat(acceptance.getTermsVersionId()).isEqualTo(version.getId());
    assertThat(acceptance.getRequestId()).isNotBlank();
    assertThat(acceptance.getIpAddress()).isEqualTo("203.0.113.1");
    assertThat(acceptance.getAcceptanceHash()).isNotBlank();
    verify(termsAcceptanceRepository).save(acceptance);
  }

  @Test
  void acceptValidaCamposObrigatorios() {
    UUID version = UUID.randomUUID();
    assertThatThrownBy(() -> service.accept(null, UUID.randomUUID(), version, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.accept(UUID.randomUUID(), null, version, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.accept(UUID.randomUUID(), UUID.randomUUID(), null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptLancaQuandoVersaoNaoEncontrada() {
    UUID versionId = UUID.randomUUID();
    when(termsVersionRepository.findById(versionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.accept(UUID.randomUUID(), UUID.randomUUID(), versionId, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getAcceptanceProofMontaMapaComDadosDoAceiteEDaVersao() {
    TermsVersion version = buildVersion("PRIVACY_POLICY", "v2");
    AuditTermsAcceptance acceptance = new AuditTermsAcceptance();
    acceptance.setId(UUID.randomUUID());
    acceptance.setTenantId(UUID.randomUUID());
    acceptance.setUserId(UUID.randomUUID());
    acceptance.setTermsVersionId(version.getId());
    acceptance.setRequestId("req-1");
    acceptance.setAcceptedAt(java.time.Instant.now());
    acceptance.setAcceptanceHash("hash-aceite");
    when(termsAcceptanceRepository.findById(acceptance.getId())).thenReturn(Optional.of(acceptance));
    when(termsVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));

    var proof = service.getAcceptanceProof(acceptance.getId());

    assertThat(proof).containsEntry("documentType", "PRIVACY_POLICY")
        .containsEntry("version", "v2")
        .containsEntry("acceptanceHash", "hash-aceite")
        .containsEntry("requestId", "req-1");
  }

  @Test
  void getAcceptanceProofLancaQuandoAceiteNaoEncontrado() {
    UUID id = UUID.randomUUID();
    when(termsAcceptanceRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getAcceptanceProof(id)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getLatestActiveVersionRetornaVazioQuandoDocumentTypeAusente() {
    assertThat(service.getLatestActiveVersion(null)).isEmpty();
    assertThat(service.getLatestActiveVersion(" ")).isEmpty();
  }

  @Test
  void getLatestActiveVersionRetornaAPrimeiraNaoDesativada() {
    TermsVersion v2 = buildVersion("TERMS_OF_USE", "v2");
    TermsVersion v1 = buildVersion("TERMS_OF_USE", "v1");
    when(termsVersionRepository.listByDocumentTypeNewestFirst("TERMS_OF_USE")).thenReturn(List.of(v2, v1));
    TermsLifecycleEvent disabledEvent = new TermsLifecycleEvent();
    disabledEvent.setEventType(AuditConstants.TermsEventType.DISABLED);
    when(termsLifecycleEventRepository.findLastByTermsVersionId(v2.getId())).thenReturn(Optional.of(disabledEvent));
    when(termsLifecycleEventRepository.findLastByTermsVersionId(v1.getId())).thenReturn(Optional.empty());

    Optional<TermsVersion> result = service.getLatestActiveVersion("TERMS_OF_USE");

    assertThat(result).contains(v1);
  }

  @Test
  void getLatestActiveVersionRetornaVazioQuandoTodasDesativadas() {
    TermsVersion v1 = buildVersion("TERMS_OF_USE", "v1");
    when(termsVersionRepository.listByDocumentTypeNewestFirst("TERMS_OF_USE")).thenReturn(List.of(v1));
    TermsLifecycleEvent disabledEvent = new TermsLifecycleEvent();
    disabledEvent.setEventType(AuditConstants.TermsEventType.DISABLED);
    when(termsLifecycleEventRepository.findLastByTermsVersionId(v1.getId())).thenReturn(Optional.of(disabledEvent));

    assertThat(service.getLatestActiveVersion("TERMS_OF_USE")).isEmpty();
  }

  @Test
  void requireActiveVersionRetornaVersaoQuandoAtiva() {
    TermsVersion version = buildVersion("TERMS_OF_USE", "v1");
    // Mesma assimetria do publishVersion: requireActiveVersion tambem consulta com trim() sem
    // uppercase (comportamento original preservado).
    when(termsVersionRepository.findByDocumentTypeAndVersion("terms_of_use", "v1")).thenReturn(Optional.of(version));
    when(termsLifecycleEventRepository.findLastByTermsVersionId(version.getId())).thenReturn(Optional.empty());

    assertThat(service.requireActiveVersion("terms_of_use", "v1")).isSameAs(version);
  }

  @Test
  void requireActiveVersionLancaQuandoDesativada() {
    TermsVersion version = buildVersion("TERMS_OF_USE", "v1");
    when(termsVersionRepository.findByDocumentTypeAndVersion("terms_of_use", "v1")).thenReturn(Optional.of(version));
    TermsLifecycleEvent disabledEvent = new TermsLifecycleEvent();
    disabledEvent.setEventType(AuditConstants.TermsEventType.DISABLED);
    when(termsLifecycleEventRepository.findLastByTermsVersionId(version.getId())).thenReturn(Optional.of(disabledEvent));

    assertThatThrownBy(() -> service.requireActiveVersion("terms_of_use", "v1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requireActiveVersionLancaQuandoNaoEncontrada() {
    when(termsVersionRepository.findByDocumentTypeAndVersion("terms_of_use", "v9")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireActiveVersion("terms_of_use", "v9"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requireActiveVersionValidaCamposObrigatorios() {
    assertThatThrownBy(() -> service.requireActiveVersion(null, "v1")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.requireActiveVersion("TERMS_OF_USE", null)).isInstanceOf(IllegalArgumentException.class);
  }
}
