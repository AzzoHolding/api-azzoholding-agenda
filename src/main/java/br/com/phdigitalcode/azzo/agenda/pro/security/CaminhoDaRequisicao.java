package br.com.phdigitalcode.azzo.agenda.pro.security;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Caminho da requisicao do jeito que o roteador o enxerga (achados SEG-001 e SEG-002, auditoria de
 * 2026-10-06).
 *
 * <p>{@code getRequestURI()} devolve o caminho BRUTO: {@code /api/v1/%69nternal/...} nao comeca com
 * {@code /api/v1/internal/}, mas o Spring MVC decodifica o segmento e roteia para o mesmo controller.
 * Um filtro que decide por prefixo ou igualdade sobre a URI bruta, portanto, e contornado so
 * escrevendo uma letra em percent-encoding. Todo filtro que protege uma rota por caminho deve
 * comparar o caminho NORMALIZADO por aqui: sem o context path, decodificado, sem parametros de
 * caminho ({@code ;x=1}), sem {@code .}/{@code ..} e sem barra final.
 */
public final class CaminhoDaRequisicao {

  // Decodifica e remove ";param" por padrao, como o roteador.
  private static final UrlPathHelper AJUDANTE = new UrlPathHelper();

  private CaminhoDaRequisicao() {}

  public static String normalizado(HttpServletRequest request) {
    String caminho = AJUDANTE.getPathWithinApplication(request);
    if (caminho == null || caminho.isEmpty()) return "/";
    caminho = StringUtils.cleanPath(caminho);
    if (!caminho.startsWith("/")) caminho = "/" + caminho;
    while (caminho.length() > 1 && caminho.endsWith("/")) {
      caminho = caminho.substring(0, caminho.length() - 1);
    }
    return caminho;
  }
}
