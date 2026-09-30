package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.PublicCnpjResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.PublicCnpjRateLimiter;
import br.com.phdigitalcode.azzo.agenda.pro.service.CnpjConsultaService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CnpjValidator;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Consulta de CNPJ SEM login, para preencher o cadastro do salao ({@code /api/v1/public/**} e
 * {@code permitAll} no {@code SecurityConfig}).
 *
 * <p>Aceita CNPJ com ou sem formatacao. 200 com razao social, nome fantasia, situacao e endereco;
 * 400 se invalido; 429 acima do limite por IP; 503 se as duas APIs externas estiverem fora. Nao
 * devolve e-mail nem telefone — ver {@link PublicCnpjResponse}.
 */
@RestController
@RequestMapping("/api/v1/public/cnpj")
public class PublicCnpjController {

  private final CnpjConsultaService cnpjConsultaService;
  private final PublicCnpjRateLimiter rateLimiter;

  public PublicCnpjController(CnpjConsultaService cnpjConsultaService, PublicCnpjRateLimiter rateLimiter) {
    this.cnpjConsultaService = cnpjConsultaService;
    this.rateLimiter = rateLimiter;
  }

  @GetMapping("/{cnpj}")
  public PublicCnpjResponse consultar(@PathVariable String cnpj, HttpServletRequest request) {
    rateLimiter.consumirOuFalhar(request);
    String sanitized = CnpjValidator.sanitize(cnpj);
    if (!CnpjValidator.isValid(sanitized)) {
      throw new IllegalArgumentException("CNPJ invalido. Informe os 14 digitos corretamente.");
    }
    return PublicCnpjResponse.de(cnpjConsultaService.consultarSemContato(sanitized));
  }
}
