package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDePagamentos;
import jakarta.servlet.http.HttpServletRequest;

/**
 * O aviso (webhook) de qualquer operadora: {@code POST /webhook/pagamentos/{operadora}/{token}}.
 *
 * <p>Publico (sem login, isento da licenca, como os demais {@code /webhook/**}). Quem autentica e o
 * token da conta na URL e, quando a operadora assina, a assinatura conferida pelo provedor. O corpo
 * chega cru: a assinatura (HMAC) e calculada sobre o texto exato que a operadora mandou.
 */
@RestController
@RequestMapping("/webhook/pagamentos")
public class WebhookDePagamentoController {

  private final ServicoDePagamentos pagamentos;

  public WebhookDePagamentoController(ServicoDePagamentos pagamentos) {
    this.pagamentos = pagamentos;
  }

  @PostMapping("/{provedor}/{token}")
  public ResponseEntity<Void> receber(
      @PathVariable String provedor,
      @PathVariable String token,
      @RequestBody(required = false) String corpo,
      HttpServletRequest request) {
    Map<String, String> cabecalhos = new LinkedHashMap<>();
    for (String nome : Collections.list(request.getHeaderNames())) {
      cabecalhos.put(nome.toLowerCase(Locale.ROOT), request.getHeader(nome));
    }
    // Alguns avisos (Mercado Pago) vem com dados na query string tambem.
    request.getParameterMap().forEach((k, v) -> {
      if (v != null && v.length > 0) cabecalhos.put("query." + k, v[0]);
    });
    pagamentos.receberAviso(provedor, token, cabecalhos, corpo == null ? "" : corpo);
    return ResponseEntity.ok().build();
  }
}
