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

  /**
   * Registra o numero no Cloud API para uma conexao que JA existe.
   *
   * <p>O Embedded Signup passou a registrar na conexao nova, mas quem ja estava conectado antes
   * ficou com um numero que nao envia — e reconectar pelo popup so para disparar o registro seria
   * absurdo. Este e o caminho para esses casos, e para quando o registro falhou na primeira vez.
   */
  /** O salao cadastra o template que ele aprovou na Meta para a confirmacao de agendamento. */
  @PostMapping("/confirmation-template")
  public TenantWhatsAppDtos.ConfigResponse definirTemplateDeConfirmacao(
      @RequestBody TenantWhatsAppDtos.TemplateDeConfirmacaoRequest request) {
    return servicoTenantWhatsapp.definirTemplateDeConfirmacao(request);
  }

  /** Os templates do salao, com o estado da analise da Meta. */
  @GetMapping("/templates")
  public TenantWhatsAppDtos.TemplatesResponse listarTemplates() {
    return servicoTenantWhatsapp.listarTemplates();
  }

  /**
   * Cria na Meta os templates das mensagens automaticas que o salao escreveu.
   *
   * <p>Criar nao e aprovar: a resposta volta com os pendentes, e o estado e reconferido sozinho.
   */
  @PostMapping("/templates")
  public TenantWhatsAppDtos.TemplatesResponse criarTemplates() {
    return servicoTenantWhatsapp.criarTemplatesDasMensagens();
  }

  @PostMapping("/register-number")
  public TenantWhatsAppDtos.RegistroDoNumeroResponse registrarNumero() {
    return servicoTenantWhatsapp.registrarNumero();
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
