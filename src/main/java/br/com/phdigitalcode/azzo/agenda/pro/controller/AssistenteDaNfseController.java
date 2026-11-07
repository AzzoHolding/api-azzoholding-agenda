package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseAssistenteDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.AssistenteDaNfse;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;

/**
 * Assistente de configuracao da NFS-e. So o dono, como o resto da configuracao fiscal
 * ({@link FiscalController#CONFIGURACAO}). Nada aqui grava: a configuracao continua sendo salva
 * pelas rotas de sempre ({@code PUT /fiscal/nfse/config} e {@code PUT /fiscal/tax-config}).
 */
@RestController
@RequestMapping("/api/v1/fiscal/nfse/assistente")
@PreAuthorize(FiscalController.CONFIGURACAO)
public class AssistenteDaNfseController {

  private final AssistenteDaNfse assistenteDaNfse;
  private final FiscalAccessService fiscalAccessService;
  private final ContextoTenant contextoTenant;

  public AssistenteDaNfseController(
      AssistenteDaNfse assistenteDaNfse, FiscalAccessService fiscalAccessService, ContextoTenant contextoTenant) {
    this.assistenteDaNfse = assistenteDaNfse;
    this.fiscalAccessService = fiscalAccessService;
    this.contextoTenant = contextoTenant;
  }

  @GetMapping("/emissor")
  public NfseAssistenteDtos.Emissor emissor(@RequestParam String cnpj) {
    fiscalAccessService.validarAcessoFiscal(contextoTenant.obterTenantIdOuFalhar());
    return assistenteDaNfse.dadosDoEmissor(cnpj);
  }

  @GetMapping("/conferencia")
  public NfseAssistenteDtos.Conferencia conferencia(@RequestParam(required = false) String ambiente) {
    fiscalAccessService.validarAcessoFiscal(contextoTenant.obterTenantIdOuFalhar());
    return assistenteDaNfse.conferir(ambiente);
  }
}
