package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.exception.FiscalProviderException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.FiscalStateTransitionException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/fiscal/application/ServicoFiscalUnitTest.java}.
 *
 * <p>O original injeta campos {@code @Inject} diretamente (CDI, sem construtor); aqui os
 * colaboradores viram mocks Mockito passados pelo construtor unico de {@link ServicoFiscal} —
 * mesmos cenarios, mesma asserção, adaptado para injeção por construtor (regra do projeto: zero
 * {@code @Autowired} em atributo).
 */
class ServicoFiscalTest {

  private final UUID tenantId = UUID.randomUUID();

  private ContextoTenant contextoTenant;
  private FiscalProvider fiscalProvider;
  private FiscalDocumentStateMachine fiscalDocumentStateMachine;
  private AuditService auditService;
  private AuthenticatedUser authenticatedUser;
  private FiscalPersistenceService fiscalPersistenceService;
  private FiscalDanfeJobService fiscalDanfeJobService;
  private FiscalInvoiceRepository fiscalInvoiceRepository;
  private FiscalInvoiceEventService fiscalInvoiceEventService;
  private FiscalCertificateService fiscalCertificateService;
  private FiscalRuleValidationService fiscalRuleValidationService;
  private FiscalTaxCalculationService fiscalTaxCalculationService;
  private FiscalXmlBuilderService fiscalXmlBuilderService;
  private FiscalXmlSignerService fiscalXmlSignerService;
  private FiscalSefazClient fiscalSefazClient;

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    fiscalProvider = mock(FiscalProvider.class);
    fiscalDocumentStateMachine = new FiscalDocumentStateMachine();
    auditService = mock(AuditService.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    fiscalPersistenceService = mock(FiscalPersistenceService.class);
    fiscalDanfeJobService = mock(FiscalDanfeJobService.class);
    fiscalInvoiceRepository = mock(FiscalInvoiceRepository.class);
    fiscalInvoiceEventService = mock(FiscalInvoiceEventService.class);
    fiscalCertificateService = mock(FiscalCertificateService.class);
    FiscalCodeCatalogService fiscalCodeCatalogService = mock(FiscalCodeCatalogService.class);
    when(fiscalCodeCatalogService.hasCatalogForType(anyString())).thenReturn(false);
    fiscalRuleValidationService = new FiscalRuleValidationService(fiscalCodeCatalogService);
    fiscalTaxCalculationService = new FiscalTaxCalculationService();
    fiscalXmlBuilderService = mock(FiscalXmlBuilderService.class);
    fiscalXmlSignerService = mock(FiscalXmlSignerService.class);
    fiscalSefazClient = mock(FiscalSefazClient.class);

    // Por padrao nenhuma invoice local existe (mesma postura do original: default do repository
    // real, aqui explicito porque o mock nao chama o metodo default sozinho).
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(any(), any()))
        .thenReturn(Optional.empty());
  }

  private ServicoFiscal novoServico() {
    return new ServicoFiscal(
        contextoTenant,
        fiscalProvider,
        fiscalDocumentStateMachine,
        auditService,
        authenticatedUser,
        fiscalPersistenceService,
        fiscalDanfeJobService,
        fiscalInvoiceRepository,
        fiscalInvoiceEventService,
        fiscalCertificateService,
        fiscalRuleValidationService,
        fiscalTaxCalculationService,
        fiscalXmlBuilderService,
        fiscalXmlSignerService,
        fiscalSefazClient,
        1,
        "HOMOLOGACAO");
  }

  @Test
  void deveFalharCancelamentoSemMotivo() {
    ServicoFiscal servicoFiscal = novoServico();
    FiscalDtos.Invoice atual = new FiscalDtos.Invoice();
    atual.status = "AUTHORIZED";
    when(fiscalProvider.obterInvoice(tenantId, "inv-001")).thenReturn(atual);

    FiscalDtos.CancelInvoiceRequest request = new FiscalDtos.CancelInvoiceRequest();
    request.reason = " ";

    assertThrows(IllegalArgumentException.class, () -> servicoFiscal.cancelarInvoice("inv-001", request));
  }

  @Test
  void deveFalharCancelamentoForaDeStatusPermitido() {
    ServicoFiscal servicoFiscal = novoServico();
    FiscalDtos.Invoice atual = new FiscalDtos.Invoice();
    atual.status = "DRAFT";
    when(fiscalProvider.obterInvoice(tenantId, "inv-001")).thenReturn(atual);

    FiscalDtos.CancelInvoiceRequest request = new FiscalDtos.CancelInvoiceRequest();
    request.reason = "Cliente desistiu do servico";

    assertThrows(
        FiscalStateTransitionException.class, () -> servicoFiscal.cancelarInvoice("inv-001", request));
  }

  @Test
  void devePermitirCancelamentoComStatusAuthorizedEMotivo() {
    ServicoFiscal servicoFiscal = novoServico();
    FiscalDtos.Invoice atual = new FiscalDtos.Invoice();
    atual.status = "AUTHORIZED";
    when(fiscalProvider.obterInvoice(tenantId, "inv-001")).thenReturn(atual);
    FiscalDtos.Invoice cancelado = new FiscalDtos.Invoice();
    cancelado.id = "inv-001";
    cancelado.status = "CANCELLED";
    when(fiscalProvider.cancelarInvoice(eq(tenantId), eq("inv-001"), any())).thenReturn(cancelado);

    FiscalDtos.CancelInvoiceRequest request = new FiscalDtos.CancelInvoiceRequest();
    request.reason = "Erro de emissao identificado apos conferencia";

    FiscalDtos.Invoice response = servicoFiscal.cancelarInvoice("inv-001", request);

    assertThat(response).isNotNull();
    assertThat(response.status).isEqualTo("CANCELLED");
  }

  @Test
  void deveDelegarCriacaoDeInvoiceComTenantAtual() {
    ServicoFiscal servicoFiscal = novoServico();
    FiscalDtos.TaxConfig tax = new FiscalDtos.TaxConfig();
    tax.regime = "SIMPLES_NACIONAL";
    when(fiscalProvider.obterTaxConfig(tenantId)).thenReturn(tax);
    when(fiscalProvider.criarInvoice(eq(tenantId), any()))
        .thenAnswer(
            invocation -> {
              FiscalDtos.Invoice req = invocation.getArgument(1);
              FiscalDtos.Invoice created = new FiscalDtos.Invoice();
              created.id = UUID.randomUUID().toString();
              created.type = req.type;
              created.status = req.status;
              return created;
            });

    FiscalDtos.Invoice request = new FiscalDtos.Invoice();
    request.type = "NFSE";
    request.customer = new FiscalDtos.InvoiceCustomer();
    request.customer.name = "Cliente Teste";
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    item.cfop = "5102";
    item.cst = "101";
    item.quantity = 1;
    item.unitPrice = 1000L;
    item.totalPrice = 1000L;
    request.items = List.of(item);

    FiscalDtos.Invoice result = servicoFiscal.criarInvoice(request);

    assertThat(result).isNotNull();
    assertThat(result.type).isEqualTo("NFSE");
    verify(fiscalProvider).criarInvoice(eq(tenantId), any());
  }

  @Test
  void deveDelegarConsultaDeHistoricoComLimite() {
    ServicoFiscal servicoFiscal = novoServico();
    when(fiscalProvider.historico(tenantId, 6)).thenReturn(List.of(new FiscalDtos.ApuracaoResumo()));

    List<FiscalDtos.ApuracaoResumo> historico = servicoFiscal.historico(6);

    assertThat(historico).isNotNull();
    verify(fiscalProvider).historico(tenantId, 6);
  }

  @Test
  void deveRegistrarEventoDeInutilizacaoQuandoFalhaAntesDoEnvioComNumeroReservado() {
    FiscalDtos.Invoice atual = new FiscalDtos.Invoice();
    atual.id = "inv-001";
    atual.type = "NFE";
    atual.status = "SIGNED";
    atual.customer = new FiscalDtos.InvoiceCustomer();
    atual.customer.name = "Cliente";
    FiscalDtos.InvoiceItem item = new FiscalDtos.InvoiceItem();
    item.cfop = "5102";
    item.cst = "00";
    item.quantity = 1;
    item.unitPrice = 1000L;
    item.totalPrice = 1000L;
    atual.items = List.of(item);
    when(fiscalProvider.obterInvoice(tenantId, "inv-001")).thenReturn(atual);

    when(fiscalPersistenceService.reservarNumeroFiscal(eq(tenantId), eq("inv-001"), anyString(), eq(1), anyString()))
        .thenReturn("123");
    when(fiscalPersistenceService.obterNumeroFiscalReservado(tenantId, "inv-001")).thenReturn("123");

    FiscalInvoiceEntity entity = new FiscalInvoiceEntity();
    entity.setId(UUID.randomUUID());
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "inv-001"))
        .thenReturn(Optional.of(entity));

    FiscalProviderException falha = new FiscalProviderException("timeout autorizacao", 500);
    when(fiscalSefazClient.autorizarInvoice(tenantId, "inv-001")).thenThrow(falha);

    ServicoFiscal servicoFiscal = novoServico();

    assertThrows(FiscalProviderException.class, () -> servicoFiscal.autorizarInvoice("inv-001", "secret"));

    verify(fiscalPersistenceService).atualizarStatusInvoice(tenantId, "inv-001", "ERROR_FINAL");

    InOrder ordem = inOrder(fiscalInvoiceEventService);
    ordem
        .verify(fiscalInvoiceEventService)
        .registrarEvento(
            eq(tenantId),
            eq(entity.getId()),
            eq("AUTHORIZATION_PRE_SUBMISSION_FAILURE"),
            eq("ERROR_FINAL"),
            eq("500"),
            anyString());
    ordem
        .verify(fiscalInvoiceEventService)
        .registrarEvento(
            eq(tenantId),
            eq(entity.getId()),
            eq("FISCAL_NUMBER_INUTILIZATION_REQUIRED"),
            eq("PENDING_MANUAL_ACTION"),
            eq("INUTILIZATION_REQUIRED"),
            anyString());
  }
}
