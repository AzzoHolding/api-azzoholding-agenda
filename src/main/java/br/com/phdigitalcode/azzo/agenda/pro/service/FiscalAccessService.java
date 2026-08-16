package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/fiscal/application/FiscalAccessService.java}.
 *
 * <p>Porteiro do modulo fiscal: so tenant com <b>CNPJ</b> emite documento fiscal. O criterio do
 * original e puramente estrutural — 14 digitos depois de remover tudo que nao e numero. Nao ha
 * verificacao de digito verificador nem consulta a Receita: um documento de 14 digitos invalidos
 * passa. Preservado como esta.
 *
 * <p>CPF (11 digitos), documento vazio e tenant sem linha de documento caem todos no mesmo lugar:
 * acesso negado.
 *
 * <p><b>Adaptacao ao Spring:</b> o original lanca {@code jakarta.ws.rs.ForbiddenException}, que o
 * {@code ApiExceptionMapper} converte em 403 com {@code code = "FORBIDDEN"} e a mensagem da
 * excecao. Aqui vai {@link ApiClientErrorException} com status 403 — o
 * {@code GlobalExceptionHandler} produz o mesmo corpo. <b>Nao</b> usar
 * {@code AccessDeniedException}: o handler dela descarta a mensagem e responde sempre "Acesso
 * negado", o que perderia a explicacao do CNPJ que o frontend exibe.
 */
@Service
public class FiscalAccessService {

  private static final Logger LOG = LoggerFactory.getLogger(FiscalAccessService.class);

  private static final String ACCESS_DENIED_MESSAGE =
      "Modulo fiscal disponivel apenas para empresas com CNPJ cadastrado.";

  private final TenantRepository tenantRepository;

  public FiscalAccessService(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @Transactional(readOnly = true)
  public boolean podeAcessarFiscal(UUID tenantId) {
    Optional<String> document = tenantRepository.buscarDocumentoPorTenant(tenantId);
    return document.map(this::isCnpj).orElse(false);
  }

  @Transactional(readOnly = true)
  public void validarAcessoFiscal(UUID tenantId) {
    if (podeAcessarFiscal(tenantId)) {
      return;
    }
    LOG.warn(
        CorrelatedLogging.context(
            "Acesso fiscal negado: documento nao elegivel para modulo fiscal", "tenantId", tenantId));
    throw new ApiClientErrorException(ACCESS_DENIED_MESSAGE, 403);
  }

  private boolean isCnpj(String document) {
    if (document == null || document.isBlank()) {
      return false;
    }
    String digitsOnly = document.replaceAll("\\D", "");
    return digitsOnly.length() == 14;
  }
}
