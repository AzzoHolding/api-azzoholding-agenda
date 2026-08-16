package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CnpjConsultaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.CnpjConsultaService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator;

/**
 * Espelha {@code modules/company/api/CnpjConsultaResource.java}.
 *
 * <p>GET /api/v1/cnpj/{cnpj} — aceita CNPJ com ou sem formatacao. 200 com os dados da empresa,
 * 400 se invalido, 503 se ambas as APIs externas estiverem indisponiveis.
 */
@RestController
@RequestMapping("/api/v1/cnpj")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class CnpjConsultaController {

  private final CnpjConsultaService cnpjConsultaService;

  public CnpjConsultaController(CnpjConsultaService cnpjConsultaService) {
    this.cnpjConsultaService = cnpjConsultaService;
  }

  @GetMapping("/{cnpj}")
  public CnpjConsultaResponse consultar(@PathVariable String cnpj) {
    String sanitized = CnpjValidator.sanitize(cnpj);
    if (!CnpjValidator.isValid(sanitized)) {
      throw new IllegalArgumentException("CNPJ invalido. Informe os 14 digitos corretamente.");
    }
    return cnpjConsultaService.consultar(sanitized);
  }
}
