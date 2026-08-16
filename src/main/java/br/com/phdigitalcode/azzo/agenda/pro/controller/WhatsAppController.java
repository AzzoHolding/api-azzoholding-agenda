package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantWhatsAppDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTenantWhatsapp;
import jakarta.validation.Valid;

/** Espelha {@code modules/tenant/api/WhatsAppResource.java}. */
@RestController
@RequestMapping("/api/v1/tenant/whatsapp")
@PreAuthorize("hasRole('OWNER')")
public class WhatsAppController {

  private final ServicoTenantWhatsapp servicoTenantWhatsapp;

  public WhatsAppController(ServicoTenantWhatsapp servicoTenantWhatsapp) {
    this.servicoTenantWhatsapp = servicoTenantWhatsapp;
  }

  @GetMapping
  public TenantWhatsAppDtos.ConfigResponse obterConfiguracao() {
    return servicoTenantWhatsapp.obterConfiguracaoAtual();
  }

  @PutMapping
  public TenantWhatsAppDtos.ConfigResponse atualizarConfiguracao(@Valid @RequestBody TenantWhatsAppDtos.UpdateRequest request) {
    return servicoTenantWhatsapp.atualizar(request);
  }

  @PatchMapping("/settings")
  public TenantWhatsAppDtos.ConfigResponse atualizarPreferencias(@RequestBody TenantWhatsAppDtos.SettingsPatchRequest request) {
    return servicoTenantWhatsapp.atualizarPreferencias(request);
  }

  @PostMapping("/test")
  public TenantWhatsAppDtos.TestResponse testarConexao() {
    return servicoTenantWhatsapp.testarConexao();
  }

  @PostMapping("/validate")
  public TenantWhatsAppDtos.ValidateResponse validarConfiguracao(
      @Valid @RequestBody TenantWhatsAppDtos.ValidateRequest request) {
    return servicoTenantWhatsapp.validarConfiguracao(request);
  }

  @PostMapping("/test-message")
  public TenantWhatsAppDtos.TestMessageResponse enviarMensagemTeste(
      @Valid @RequestBody TenantWhatsAppDtos.TestMessageRequest request) {
    return servicoTenantWhatsapp.enviarMensagemTeste(request);
  }

  @GetMapping("/embedded-signup/status")
  public TenantWhatsAppDtos.EmbeddedSignupStatusResponse obterStatusEmbeddedSignup() {
    return servicoTenantWhatsapp.obterStatusEmbeddedSignup();
  }

  @PostMapping("/embedded-signup/complete")
  public TenantWhatsAppDtos.EmbeddedSignupStatusResponse concluirEmbeddedSignup(
      @Valid @RequestBody TenantWhatsAppDtos.EmbeddedSignupCompleteRequest request) {
    return servicoTenantWhatsapp.concluirEmbeddedSignup(request);
  }

  @GetMapping("/message-log")
  public TenantWhatsAppDtos.MessageLogResponse listarMensagens(
      @RequestParam(name = "limit", defaultValue = "50") int limit) {
    return servicoTenantWhatsapp.listarMensagens(limit);
  }
}
