package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalDanfeMapper.java}.
 *
 * <p>Monta o view model de renderizacao do DANFE a partir da invoice e da configuracao tributaria
 * do tenant — usado por {@link FiscalDanfeRenderService} e pelo worker de jobs.
 */
@Component
public class FiscalDanfeMapper {

  public DanfeViewModel map(FiscalDtos.Invoice invoice, FiscalDtos.TaxConfig taxConfig) {
    DanfeViewModel vm = new DanfeViewModel();
    if (invoice == null) return vm;
    vm.invoiceId = invoice.id;
    vm.model = invoice.type;
    vm.status = invoice.status;
    vm.number = invoice.numeroNf;
    vm.series = invoice.serieNf;
    vm.operationNature = invoice.operationNature;
    vm.issueDate = invoice.createdAt;
    vm.accessKey = invoice.accessKey;
    vm.authorizationProtocol = invoice.authorizationProtocol;
    vm.customer = invoice.customer;
    vm.notes = invoice.notes;
    vm.taxConfig = taxConfig;
    vm.totalAmount = invoice.totalAmount;
    vm.items = invoice.items != null ? invoice.items : new ArrayList<>();
    return vm;
  }

  public static class DanfeViewModel {
    public String invoiceId;
    public String model;
    public String status;
    public String number;
    public String series;
    public String operationNature;
    public String issueDate;
    public String accessKey;
    public String authorizationProtocol;
    public FiscalDtos.InvoiceCustomer customer;
    public String notes;
    public FiscalDtos.TaxConfig taxConfig;
    public long totalAmount;
    public List<FiscalDtos.InvoiceItem> items = new ArrayList<>();
  }
}
