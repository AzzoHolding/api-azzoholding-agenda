package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicLegalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.service.TermsService;

/**
 * Espelha {@code modules/audit/api/publicapi/PublicLegalDocumentsResource.java}. A rota ja e
 * publica via allowlist {@code /api/v1/public/**} em {@code SecurityConfig} (nao repetido aqui);
 * este teste cobre apenas o contrato do controller.
 */
class PublicLegalDocumentsControllerTest {

  private TermsService termsService;
  private PublicLegalDocumentsController controller;

  @BeforeEach
  void setUp() {
    termsService = mock(TermsService.class);
    controller = new PublicLegalDocumentsController(termsService);
  }

  private void setField(String name, String value) throws Exception {
    Field field = PublicLegalDocumentsController.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(controller, value);
  }

  private void configureContact(String email, String channel, String sla) throws Exception {
    setField("lgpdContactEmail", email);
    setField("lgpdContactChannel", channel);
    setField("lgpdResponseSla", sla);
  }

  private TermsVersion buildVersion(String documentType, String content) {
    TermsVersion version = new TermsVersion();
    version.setId(java.util.UUID.randomUUID());
    version.setDocumentType(documentType);
    version.setVersion("v1");
    version.setTitle("Titulo");
    version.setContent(content);
    version.setContentHash("hash");
    version.setCreatedAt(Instant.now());
    return version;
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(PublicLegalDocumentsController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/public/legal");
  }

  @Test
  void currentDocumentsMontaOsDoisDocumentosEOContato() throws Exception {
    configureContact("contato@azzo.com", "WhatsApp", "2 dias uteis");
    TermsVersion terms = buildVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE, "conteudo termos");
    TermsVersion privacy = buildVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY, "conteudo privacidade");
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE)).thenReturn(Optional.of(terms));
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY)).thenReturn(Optional.of(privacy));

    PublicLegalDtos.PublicLegalResponse response = controller.currentDocuments();

    assertThat(response.termsOfUse.content).isEqualTo("conteudo termos");
    assertThat(response.privacyPolicy.content).isEqualTo("conteudo privacidade");
    assertThat(response.lgpdContact.email).isEqualTo("contato@azzo.com");
    assertThat(response.lgpdContact.channel).isEqualTo("WhatsApp");
  }

  @Test
  void currentDocumentsRetornaNuloParaDocumentosNaoPublicados() throws Exception {
    configureContact("contato@azzo.com", "WhatsApp", "2 dias uteis");
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE)).thenReturn(Optional.empty());
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY)).thenReturn(Optional.empty());

    PublicLegalDtos.PublicLegalResponse response = controller.currentDocuments();

    assertThat(response.termsOfUse).isNull();
    assertThat(response.privacyPolicy).isNull();
  }

  @Test
  void termsOfUseLancaNotFoundQuandoNaoPublicado() {
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.TERMS_OF_USE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.termsOfUse())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void privacyPolicyLancaNotFoundQuandoNaoPublicada() {
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.privacyPolicy())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void contactLancaServiceUnavailableQuandoConfigAusente() throws Exception {
    configureContact("__unset__", "__unset__", "sla padrao");

    assertThatThrownBy(() -> controller.lgpdContact())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("503");
  }

  @Test
  void contactRetornaValoresConfigurados() throws Exception {
    configureContact("contato@azzo.com", "e-mail", "15 dias corridos");

    PublicLegalDtos.LgpdContactResponse contact = controller.lgpdContact();

    assertThat(contact.email).isEqualTo("contato@azzo.com");
    assertThat(contact.channel).isEqualTo("e-mail");
    assertThat(contact.responseSla).isEqualTo("15 dias corridos");
  }

  @Test
  void aplicaPlaceholdersDeContatoNoConteudo() throws Exception {
    configureContact("dpo@azzo.com", "Canal Oficial", "2 dias uteis");
    TermsVersion version = buildVersion(
        AuditConstants.TermsDocumentType.PRIVACY_POLICY,
        "Fale conosco: [PREENCHER EMAIL/CANAL]. Canal de privacidade: [PREENCHER]");
    when(termsService.getLatestActiveVersion(AuditConstants.TermsDocumentType.PRIVACY_POLICY)).thenReturn(Optional.of(version));

    PublicLegalDtos.LegalDocumentResponse response = controller.privacyPolicy();

    assertThat(response.content).contains("dpo@azzo.com");
    assertThat(response.content).contains("Canal de privacidade: Canal Oficial");
  }

  @Test
  void obterContatoLgpdDetalhadoRetornaDireitosDoTitular() throws Exception {
    configureContact("dpo@azzo.com", "e-mail", "30 dias corridos");

    ResponseEntity<java.util.Map<String, Object>> response = controller.obterContatoLgpdDetalhado();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).containsEntry("contactEmail", "dpo@azzo.com");
    assertThat((java.util.List<?>) response.getBody().get("rights")).hasSize(9);
  }

  @Test
  void submeterSolicitacaoTitularRetorna400QuandoCamposObrigatoriosAusentes() {
    ResponseEntity<java.util.Map<String, Object>> response = controller.submeterSolicitacaoTitular(null);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void submeterSolicitacaoTitularRetorna202ComProtocoloQuandoValido() {
    PublicLegalDocumentsController.DataRightsRequest request = new PublicLegalDocumentsController.DataRightsRequest();
    request.requestType = "ACCESS";
    request.requesterName = "Fulano de Tal";
    request.requesterEmail = "fulano@example.com";

    ResponseEntity<java.util.Map<String, Object>> response = controller.submeterSolicitacaoTitular(request);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat((String) response.getBody().get("protocol")).startsWith("LGPD-");
  }
}
