package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalCertificateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Cobre {@code modules/fiscal/application/FiscalCertificateService.java}.
 *
 * <p>Usa um PKCS12 real gerado com {@code keytool} em {@code src/test/resources/fiscal/
 * certificado-teste.p12} (senha {@code senha-teste-123}, alias {@code fiscal-test}) — certificado
 * de teste autoassinado, nunca de produção. {@link EncryptionService} é mockado como
 * pass-through (o teste não precisa exercitar AES/GCM, isso já é coberto por
 * {@code EncryptionServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
class FiscalCertificateServiceTest {

  private static final String CERT_PASSWORD = "senha-teste-123";

  @Mock private ContextoTenant contextoTenant;
  @Mock private FiscalCertificateRepository fiscalCertificateRepository;
  @Mock private EncryptionService encryptionService;
  @Mock private AuditService auditService;
  @Mock private AuthenticatedUser authenticatedUser;

  private FiscalCertificateService service;
  private final UUID tenantId = UUID.randomUUID();
  private String pfxBase64;

  @BeforeEach
  void setUp() {
    service =
        new FiscalCertificateService(
            contextoTenant, fiscalCertificateRepository, encryptionService, auditService, authenticatedUser);
    pfxBase64 = Base64.getEncoder().encodeToString(lerCertificadoTeste());
  }

  // ─── salvar ─────────────────────────────────────────────────────────────

  @Test
  void salvarComRequestNuloLancaIllegalArgument() {
    assertThatThrownBy(() -> service.salvar(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Request obrigatorio");
  }

  @Test
  void salvarSemPfxLancaIllegalArgument() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    request.password = "x";

    assertThatThrownBy(() -> service.salvar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("pfxBase64 obrigatorio");
  }

  @Test
  void salvarSemSenhaLancaIllegalArgument() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    request.pfxBase64 = "abc";

    assertThatThrownBy(() -> service.salvar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password obrigatoria");
  }

  @Test
  void salvarComBase64InvalidoLancaIllegalArgument() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    request.pfxBase64 = "%%% nao e base64 %%%";
    request.password = CERT_PASSWORD;
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    assertThatThrownBy(() -> service.salvar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("pfxBase64 invalido");
  }

  @Test
  void salvarComSenhaErradaLancaPasswordInvalid() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    request.pfxBase64 = pfxBase64;
    request.password = "senha-errada";
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    assertThatThrownBy(() -> service.salvar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID);
  }

  @Test
  void salvarComPfxValidoDesativaOsAtivosECriaOEntityAtivo() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    request.pfxBase64 = pfxBase64;
    request.password = CERT_PASSWORD;
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(encryptionService.encrypt(pfxBase64)).thenReturn("cifrado-xyz");
    when(fiscalCertificateRepository.save(any(FiscalCertificateEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(authenticatedUser.idOuNulo()).thenReturn(UUID.randomUUID());

    FiscalDtos.CertificateResponse resposta = service.salvar(request);

    verify(fiscalCertificateRepository).deactivateAllActive(tenantId);
    ArgumentCaptor<FiscalCertificateEntity> captor = ArgumentCaptor.forClass(FiscalCertificateEntity.class);
    verify(fiscalCertificateRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getCertificatePfxEnc()).isEqualTo("cifrado-xyz");
    assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    assertThat(captor.getValue().getThumbprint()).isNotBlank();
    assertThat(captor.getValue().getSubjectName()).contains("Salao Teste LTDA");

    assertThat(resposta.status).isEqualTo("ACTIVE");
    assertThat(resposta.thumbprint).isEqualTo(captor.getValue().getThumbprint());
    // CertificateResponse nao tem campo de PFX/senha — o contrato ja exclui o segredo por design.

    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void auditoriaFalhandoNaoInterrompeOSalvar() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    request.pfxBase64 = pfxBase64;
    request.password = CERT_PASSWORD;
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(encryptionService.encrypt(pfxBase64)).thenReturn("cifrado-xyz");
    when(fiscalCertificateRepository.save(any(FiscalCertificateEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(authenticatedUser.idOuNulo()).thenThrow(new RuntimeException("sem contexto de seguranca"));

    FiscalDtos.CertificateResponse resposta = service.salvar(request);

    assertThat(resposta.status).isEqualTo("ACTIVE");
  }

  // ─── listar ─────────────────────────────────────────────────────────────

  @Test
  void listarMapeiaOsCertificadosDoTenantSemExporOPfx() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    FiscalCertificateEntity entity = entidadeAtiva();
    when(fiscalCertificateRepository.listByTenant(tenantId)).thenReturn(List.of(entity));

    List<FiscalDtos.CertificateResponse> resultado = service.listar();

    assertThat(resultado).hasSize(1);
    assertThat(resultado.get(0).id).isEqualTo(entity.getId().toString());
    assertThat(resultado.get(0).status).isEqualTo("ACTIVE");
  }

  // ─── remover ────────────────────────────────────────────────────────────

  @Test
  void removerComIdInvalidoLancaIllegalArgument() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    assertThatThrownBy(() -> service.remover("nao-e-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("id de certificado invalido");
  }

  @Test
  void removerCertificadoInexistenteLanca404() {
    UUID id = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findByTenantAndId(tenantId, id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remover(id.toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Certificado fiscal nao encontrado.")
        .satisfies(ex -> assertThat(((ApiClientErrorException) ex).getStatus()).isEqualTo(404));
  }

  @Test
  void removerMarcaComoDeletedSemExcluirFisicamente() {
    FiscalCertificateEntity entity = entidadeAtiva();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findByTenantAndId(tenantId, entity.getId()))
        .thenReturn(Optional.of(entity));

    service.remover(entity.getId().toString());

    assertThat(entity.getStatus()).isEqualTo("DELETED");
    verify(fiscalCertificateRepository, never()).save(any());
  }

  // ─── ativar ─────────────────────────────────────────────────────────────

  @Test
  void ativarCertificadoInexistenteLanca404() {
    UUID id = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findByTenantAndId(tenantId, id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.ativar(id.toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Certificado fiscal nao encontrado.");
  }

  @Test
  void ativarDesativaOsDemaisEMarcaEsteComoAtivo() {
    FiscalCertificateEntity entity = entidadeAtiva();
    entity.setStatus("INACTIVE");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findByTenantAndId(tenantId, entity.getId()))
        .thenReturn(Optional.of(entity));

    FiscalDtos.CertificateResponse resposta = service.ativar(entity.getId().toString());

    verify(fiscalCertificateRepository).deactivateAllActive(tenantId);
    assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    assertThat(resposta.status).isEqualTo("ACTIVE");
  }

  // ─── validarSenhaCertificadoAtivo ───────────────────────────────────────

  @Test
  void validarSenhaComSenhaVaziaLancaErroDePassword() {
    assertThatThrownBy(() -> service.validarSenhaCertificadoAtivo(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
  }

  @Test
  void validarSenhaSemCertificadoAtivoLancaErroDeCertificadoAusente() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findActiveByTenant(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.validarSenhaCertificadoAtivo("qualquer"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_ACTIVE_MISSING);
  }

  @Test
  void validarSenhaCorretaNaoLancaNada() {
    FiscalCertificateEntity entity = entidadeAtiva();
    entity.setCertificatePfxEnc("cifrado-xyz");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findActiveByTenant(tenantId)).thenReturn(Optional.of(entity));
    when(encryptionService.decrypt("cifrado-xyz")).thenReturn(pfxBase64);

    service.validarSenhaCertificadoAtivo(CERT_PASSWORD);
  }

  @Test
  void validarSenhaIncorretaLancaPasswordInvalid() {
    FiscalCertificateEntity entity = entidadeAtiva();
    entity.setCertificatePfxEnc("cifrado-xyz");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findActiveByTenant(tenantId)).thenReturn(Optional.of(entity));
    when(encryptionService.decrypt("cifrado-xyz")).thenReturn(pfxBase64);

    assertThatThrownBy(() -> service.validarSenhaCertificadoAtivo("senha-errada"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID);
  }

  // ─── loadActiveKeyMaterial ──────────────────────────────────────────────

  @Test
  void loadActiveKeyMaterialSemSenhaLancaErroDePassword() {
    assertThatThrownBy(() -> service.loadActiveKeyMaterial(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
  }

  @Test
  void loadActiveKeyMaterialDevolveAChavePrivadaEOCertificadoX509() {
    FiscalCertificateEntity entity = entidadeAtiva();
    entity.setCertificatePfxEnc("cifrado-xyz");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalCertificateRepository.findActiveByTenant(tenantId)).thenReturn(Optional.of(entity));
    when(encryptionService.decrypt("cifrado-xyz")).thenReturn(pfxBase64);

    FiscalCertificateService.KeyMaterial material = service.loadActiveKeyMaterial(CERT_PASSWORD);

    assertThat(material.privateKey()).isNotNull();
    assertThat(material.privateKey().getAlgorithm()).isEqualTo("RSA");
    assertThat(material.certificate()).isNotNull();
    assertThat(material.certificate().getSubjectX500Principal().getName()).contains("Salao Teste LTDA");
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private FiscalCertificateEntity entidadeAtiva() {
    FiscalCertificateEntity entity = new FiscalCertificateEntity();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(tenantId);
    entity.setCertificatePfxEnc("cifrado-xyz");
    entity.setThumbprint("thumb-1");
    entity.setSubjectName("CN=Salao Teste LTDA");
    entity.setStatus("ACTIVE");
    return entity;
  }

  private byte[] lerCertificadoTeste() {
    try (InputStream input =
        getClass().getResourceAsStream("/fiscal/certificado-teste.p12")) {
      if (input == null) {
        throw new IllegalStateException("Fixture de certificado de teste nao encontrada no classpath.");
      }
      return input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
