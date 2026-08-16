package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Porte verbatim de {@code modules/fiscal/domain/provider/FiscalProvider.java}.
 *
 * <p>Contrato de integracao fiscal: emissao/consulta de notas, apuracao mensal e configuracao
 * tributaria do emitente. A implementacao ativa e escolhida por {@code app.fiscal.provider}
 * ({@code service.impl.MockFiscalProvider} quando ausente/"mock").
 */
public interface FiscalProvider {

  FiscalDtos.TaxConfig obterTaxConfig(UUID tenantId);

  FiscalDtos.TaxConfig atualizarTaxConfig(UUID tenantId, FiscalDtos.TaxConfig request);

  FiscalDtos.InvoiceListResponse listarInvoices(
      UUID tenantId, String status, String from, String to, Integer page, Integer pageSize);

  FiscalDtos.Invoice obterInvoice(UUID tenantId, String id);

  FiscalDtos.Invoice criarInvoice(UUID tenantId, FiscalDtos.Invoice request);

  FiscalDtos.Invoice atualizarInvoice(UUID tenantId, String id, FiscalDtos.Invoice request);

  FiscalDtos.Invoice cancelarInvoice(UUID tenantId, String id, FiscalDtos.CancelInvoiceRequest request);

  FiscalDtos.Invoice autorizarInvoice(UUID tenantId, String id);

  byte[] gerarPdfInvoice(UUID tenantId, String id);

  FiscalDtos.ApuracaoMensal obterApuracaoAtual(UUID tenantId);

  FiscalDtos.ApuracaoMensal obterApuracao(UUID tenantId, int ano, int mes);

  FiscalDtos.ApuracaoMensal recalcularApuracao(UUID tenantId, int ano, int mes);

  List<FiscalDtos.ApuracaoResumo> historico(UUID tenantId, int limite);

  FiscalDtos.ResumoAnual resumoAnual(UUID tenantId, int ano);
}
