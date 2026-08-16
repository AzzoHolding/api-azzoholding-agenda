package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantTelegramDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantTelegram;
import jakarta.validation.Valid;

/** Espelha {@code modules/tenant/api/TelegramResource.java}. */
@RestController
@RequestMapping("/api/v1/tenant/telegram")
@PreAuthorize("hasRole('OWNER')")
public class TelegramController {

  private final ServicoTenantTelegram servicoTenantTelegram;

  public TelegramController(ServicoTenantTelegram servicoTenantTelegram) {
    this.servicoTenantTelegram = servicoTenantTelegram;
  }

  @GetMapping
  public TenantTelegramDtos.ConfigResponse obterConfiguracao() {
    return servicoTenantTelegram.obterConfiguracaoAtual();
  }

  @PutMapping
  public TenantTelegramDtos.ConfigResponse atualizarConfiguracao(@Valid @RequestBody TenantTelegramDtos.UpdateRequest request) {
    return servicoTenantTelegram.atualizar(request);
  }

  @PostMapping("/test")
  public TenantTelegramDtos.TestResponse testarConexao() {
    return servicoTenantTelegram.testarConexao();
  }

  @PostMapping("/validate")
  public TenantTelegramDtos.ValidateResponse validarConfiguracao(@Valid @RequestBody TenantTelegramDtos.ValidateRequest request) {
    return servicoTenantTelegram.validarConfiguracao(request);
  }

  @PostMapping("/test-message")
  public TenantTelegramDtos.TestMessageResponse enviarMensagemTeste(
      @Valid @RequestBody TenantTelegramDtos.TestMessageRequest request) {
    return servicoTenantTelegram.enviarMensagemTeste(request);
  }
}
