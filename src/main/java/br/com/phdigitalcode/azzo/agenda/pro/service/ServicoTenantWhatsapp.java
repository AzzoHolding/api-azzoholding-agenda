package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantWhatsAppDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupGateway;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;

/** Espelha {@code modules/tenant/application/ServicoTenantWhatsapp.java}. */
@Service
public class ServicoTenantWhatsapp {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoTenantWhatsapp.class);

  private static final String ONBOARDING_NOT_STARTED = "NOT_STARTED";
  private static final String ONBOARDING_PENDING_EXCHANGE = "PENDING_EXCHANGE";
  private static final String ONBOARDING_CONNECTED = "CONNECTED";
  private static final String ONBOARDING_FAILED = "FAILED";
  private static final String TOKEN_SOURCE_MANUAL = "MANUAL";
  private static final String TOKEN_SOURCE_EMBEDDED = "EMBEDDED_CODE_EXCHANGE";

  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final EncryptionService encryptionService;
  private final WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private final WhatsAppClient whatsAppClient;
  private final MetaEmbeddedSignupGateway metaEmbeddedSignupClient;
  private final WhatsAppMessageLogRepository messageLogRepository;
  private final ServicoTemplatesDoWhatsapp servicoTemplates;
  private final boolean embeddedSignupEnabled;
  private final String templateDeTeste;
  private final String idiomaDoTemplateDeTeste;

  public ServicoTenantWhatsapp(
      ContextoTenant contextoTenant,
      AuditService auditService,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      EncryptionService encryptionService,
      WebhookVerifyTokenHashService webhookVerifyTokenHashService,
      WhatsAppClient whatsAppClient,
      MetaEmbeddedSignupGateway metaEmbeddedSignupClient,
      WhatsAppMessageLogRepository messageLogRepository,
      ServicoTemplatesDoWhatsapp servicoTemplates,
      @Value("${app.whatsapp.embedded-signup.enabled:false}") boolean embeddedSignupEnabled,
      @Value("${app.whatsapp.test-template.name:teste_integracao}") String templateDeTeste,
      @Value("${app.whatsapp.test-template.language:pt_BR}") String idiomaDoTemplateDeTeste) {
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.encryptionService = encryptionService;
    this.webhookVerifyTokenHashService = webhookVerifyTokenHashService;
    this.whatsAppClient = whatsAppClient;
    this.metaEmbeddedSignupClient = metaEmbeddedSignupClient;
    this.messageLogRepository = messageLogRepository;
    this.servicoTemplates = servicoTemplates;
    this.embeddedSignupEnabled = embeddedSignupEnabled;
    this.templateDeTeste = templateDeTeste;
    this.idiomaDoTemplateDeTeste = idiomaDoTemplateDeTeste;
  }

  @Transactional
  public TenantWhatsAppDtos.ConfigResponse atualizar(TenantWhatsAppDtos.UpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    String accessToken = trimToNull(request.accessToken);
    String candidateAccessToken = accessToken != null ? accessToken : decryptAccessToken(config);
    String candidatePhoneNumberId = trimToNull(request.phoneNumberId);
    WhatsAppClient.PhoneNumberDetails phoneDetails = validatePhoneNumberConfiguration(candidateAccessToken, candidatePhoneNumberId);

    if (accessToken != null) {
      config.setWhatsappAccessTokenEnc(encryptionService.encrypt(accessToken));
      config.setWhatsappTokenSource(TOKEN_SOURCE_MANUAL);
    } else if (!hasAccessTokenConfigured(config)) {
      throw new IllegalArgumentException("Token do WhatsApp e obrigatorio na primeira configuracao");
    }
    // O registro e do NUMERO, e nao da configuracao. Trocar de numero sem limpar isto deixava a
    // flag marcada pelo numero ANTIGO: o registro automatico pulava (ele comeca por "ja
    // registrado?"), e o aviso e o botao da tela ficavam escondidos pelo mesmo motivo. Beco sem
    // saida — o sistema dizia registrado, nao registrava e nao oferecia o botao (2026-09-23).
    if (mudouDeNumero(config.getWhatsappPhoneNumberId(), candidatePhoneNumberId)) {
      config.setWhatsappRegisteredAt(null);
    }
    config.setWhatsappPhoneNumberId(candidatePhoneNumberId);
    config.setWhatsappBusinessAccountId(trimToNull(request.businessAccountId));
    config.setMetaBusinessId(trimToNull(request.businessId));
    config.setDisplayPhoneNumber(firstNonBlank(
        trimToNull(request.displayPhoneNumber),
        phoneDetails != null ? phoneDetails.displayPhoneNumber : null,
        config.getDisplayPhoneNumber()));
    String webhookVerifyToken = trimToNull(request.webhookVerifyToken);
    if (webhookVerifyToken != null) {
      assignWebhookVerifyToken(config, webhookVerifyToken);
    } else {
      ensureWebhookVerifyToken(config);
    }
    config.setWhatsappEnabled(request.whatsappEnabled);
    if (config.getWhatsappPhoneNumberId() == null || config.getWhatsappPhoneNumberId().isBlank()) {
      config.setWhatsappOnboardingStatus(ONBOARDING_NOT_STARTED);
    } else if (hasAccessTokenConfigured(config)) {
      config.setWhatsappOnboardingStatus(ONBOARDING_CONNECTED);
    }
    if (request.usageProfile != null && isValidUsageProfile(request.usageProfile)) {
      config.setWhatsappUsageProfile(request.usageProfile);
      applyProfileDefaults(config, request.usageProfile);
    }
    if (request.canSchedule != null) config.setCanSchedule(request.canSchedule);
    if (request.canCancel != null) config.setCanCancel(request.canCancel);
    if (request.canReschedule != null) config.setCanReschedule(request.canReschedule);
    if (request.confirmationMessageTemplate != null) config.setConfirmationMessageTemplate(trimToNull(request.confirmationMessageTemplate));
    if (request.cancellationMessageTemplate != null) config.setCancellationMessageTemplate(trimToNull(request.cancellationMessageTemplate));
    if (request.reminderMessageTemplate != null) config.setReminderMessageTemplate(trimToNull(request.reminderMessageTemplate));

    // Salvar a credencial nao faz o numero enviar: falta registrar no Cloud API. Fazer isso aqui
    // e o que torna o passo invisivel para o cliente — ele salva a configuracao e pronto. Falhar
    // nao derruba o salvamento; o aviso e o botao na tela cobrem o resto.
    registrarSeNecessario(config);

    tenantWhatsAppConfigRepository.save(config);
    TenantWhatsAppDtos.ConfigResponse result = toConfigResponse(config);
    registrarAuditoria(tenantId, "WHATSAPP_CONFIG_UPDATE", tenantId.toString(), null, result, true);
    return result;
  }

  @Transactional
  public TenantWhatsAppDtos.ConfigResponse atualizarPreferencias(TenantWhatsAppDtos.SettingsPatchRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    if (request.whatsappEnabled != null) config.setWhatsappEnabled(request.whatsappEnabled);
    if (request.usageProfile != null && isValidUsageProfile(request.usageProfile)) {
      config.setWhatsappUsageProfile(request.usageProfile);
      applyProfileDefaults(config, request.usageProfile);
    }
    if (request.canSchedule != null) config.setCanSchedule(request.canSchedule);
    if (request.canCancel != null) config.setCanCancel(request.canCancel);
    if (request.canReschedule != null) config.setCanReschedule(request.canReschedule);
    tenantWhatsAppConfigRepository.save(config);
    TenantWhatsAppDtos.ConfigResponse result = toConfigResponse(config);
    registrarAuditoria(tenantId, "WHATSAPP_SETTINGS_PATCH", tenantId.toString(), null, result, true);
    return result;
  }

  @Transactional
  public TenantWhatsAppDtos.TestResponse testarConexao() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    TenantWhatsAppDtos.TestResponse response = new TenantWhatsAppDtos.TestResponse();
    try {
      boolean ok = whatsAppClient.testConnection(config);
      config.setWhatsappEnabled(ok);
      tenantWhatsAppConfigRepository.save(config);

      response.success = ok;
      response.whatsappEnabled = config.isWhatsappEnabled();
      response.message = "Conexao validada com sucesso.";
      registrarAuditoria(tenantId, "WHATSAPP_CONNECTION_TEST", tenantId.toString(), null, java.util.Map.of("success", ok), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.whatsappEnabled = Boolean.TRUE.equals(config.isWhatsappEnabled());
      response.message = mapTestConnectionError(ex);
      registrarAuditoria(tenantId, "WHATSAPP_CONNECTION_TEST", tenantId.toString(), null, java.util.Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  public TenantWhatsAppDtos.ValidateResponse validarConfiguracao(TenantWhatsAppDtos.ValidateRequest request) {
    WhatsAppClient.PhoneNumberDetails details =
        whatsAppClient.fetchPhoneNumberDetails(trimToNull(request.accessToken), trimToNull(request.phoneNumberId));
    TenantWhatsAppDtos.ValidateResponse response = new TenantWhatsAppDtos.ValidateResponse();
    response.success = true;
    response.message = "Conexao com a Meta validada com sucesso.";
    response.phoneNumberId = details.id;
    response.displayPhoneNumber = details.displayPhoneNumber;
    response.verifiedName = details.verifiedName;
    return response;
  }

  @Transactional(readOnly = true)
  public TenantWhatsAppDtos.TemplatesResponse listarTemplates() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return paraResposta(servicoTemplates.listarDoTenant(tenantId));
  }

  @Transactional
  public TenantWhatsAppDtos.TemplatesResponse sincronizarTemplates() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    // Sincronizar e "poe tudo em dia", e nao "poe os modelos em dia": criar template num numero
    // que nao envia resolve metade do problema. Registrar aqui tambem resgata quem ficou com a
    // flag de registro errada depois de trocar de numero.
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    if (registrarSeNecessario(config)) {
      tenantWhatsAppConfigRepository.save(config);
    }
    TenantWhatsAppDtos.TemplatesResponse resposta =
        paraResposta(servicoTemplates.sincronizar(tenantId));
    registrarAuditoria(tenantId, "WHATSAPP_TEMPLATES_SYNC", tenantId.toString(), null,
        java.util.Map.of("quantidade", resposta.items.size()), true);
    return resposta;
  }

  @Transactional
  public TenantWhatsAppDtos.TemplatesResponse criarTemplatesDasMensagens() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    servicoTemplates.criarTemplatesDasMensagens(tenantId);
    TenantWhatsAppDtos.TemplatesResponse resposta =
        paraResposta(servicoTemplates.listarDoTenant(tenantId));
    registrarAuditoria(tenantId, "WHATSAPP_TEMPLATES_CREATE", tenantId.toString(), null,
        java.util.Map.of("quantidade", resposta.items.size()), true);
    return resposta;
  }

  private TenantWhatsAppDtos.TemplatesResponse paraResposta(
      java.util.List<br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppTemplateEntity> templates) {
    TenantWhatsAppDtos.TemplatesResponse resposta = new TenantWhatsAppDtos.TemplatesResponse();
    resposta.items =
        templates.stream()
            .map(
                template -> {
                  TenantWhatsAppDtos.TemplateItem item = new TenantWhatsAppDtos.TemplateItem();
                  item.finalidade = template.getFinalidade();
                  item.nome = template.getNome();
                  item.idioma = template.getIdioma();
                  item.status = template.getStatus();
                  item.motivoRecusa = template.getMotivoRecusa();
                  item.corpo = template.getCorpo();
                  item.variaveis = template.getVariaveis();
                  return item;
                })
            .toList();
    return resposta;
  }

  /**
   * Guarda o template que o salao aprovou na Meta para a confirmacao.
   *
   * <p>Nao da para adivinhar nem padronizar: o nome e o idioma sao os que a Meta aprovou para
   * AQUELE salao, com o texto que ele escreveu. Vazio limpa, e volta a nao haver confirmacao para
   * cliente novo.
   */
  @Transactional
  public TenantWhatsAppDtos.ConfigResponse definirTemplateDeConfirmacao(
      TenantWhatsAppDtos.TemplateDeConfirmacaoRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    config.setConfirmationTemplateName(trimToNull(request == null ? null : request.templateName));
    config.setConfirmationTemplateLanguage(
        trimToNull(request == null ? null : request.templateLanguage));
    tenantWhatsAppConfigRepository.save(config);
    TenantWhatsAppDtos.ConfigResponse resposta = toConfigResponse(config);
    registrarAuditoria(tenantId, "WHATSAPP_CONFIRMATION_TEMPLATE_UPDATE", tenantId.toString(), null,
        java.util.Map.of(
            "template", config.getConfirmationTemplateName() == null ? "" : config.getConfirmationTemplateName()),
        true);
    return resposta;
  }

  private boolean mudouDeNumero(String atual, String novo) {
    if (atual == null || atual.isBlank()) return false;
    return !atual.equals(novo);
  }

  /**
   * A Meta disse que o numero nao esta registrado: a flag esta errada, e nao ela.
   *
   * <p>Confiar na flag contra a resposta da Meta e o que criava o beco sem saida. Limpar aqui faz
   * o aviso e o botao voltarem para a tela, e a proxima sincronizacao registrar de novo — util
   * tambem quando a Meta desregistra o numero por conta propria (troca de provedor, numero
   * migrado).
   */
  private void esquecerRegistroSeAMetaDiscordar(TenantWhatsAppConfig config, String erroDaMeta) {
    if (config == null || config.getWhatsappRegisteredAt() == null || erroDaMeta == null) return;
    String texto = erroDaMeta.toLowerCase();
    if (!texto.contains("133010") && !texto.contains("account not registered")) return;
    config.setWhatsappRegisteredAt(null);
    tenantWhatsAppConfigRepository.save(config);
    LOG.warn(
        "whatsapp.registro.desmarcado tenantId={} motivo=meta_respondeu_nao_registrado",
        config.getTenantId());
  }

  /**
   * Registra o numero e inscreve o webhook, se ainda nao foi feito. <b>Nunca lanca.</b>
   *
   * <p>É o passo que faz o numero SAIR DO MUDO, e ele roda sozinho em todo caminho que configura
   * credencial — o cliente nao deve precisar saber que "registrar no Cloud API" existe. Mas
   * tambem nao pode derrubar o salvamento: credencial valida guardada vale mais do que nada, e o
   * botao "Registrar numero" na tela existe exatamente para os casos em que isto falhou.
   *
   * <p>O motivo da falha fica em {@code embeddedSignupLastError} para a tela poder mostrar, e
   * {@code whatsappRegisteredAt} continua nulo — que e o que mantem o aviso e o botao visiveis.
   *
   * @return {@code true} se o numero esta registrado ao fim (inclusive se ja estava).
   */
  private boolean registrarSeNecessario(TenantWhatsAppConfig config) {
    if (config.getWhatsappRegisteredAt() != null) return true;
    if (!hasAccessTokenConfigured(config)
        || config.getWhatsappPhoneNumberId() == null
        || config.getWhatsappPhoneNumberId().isBlank()) {
      return false;
    }
    try {
      whatsAppClient.registrarNumero(config, ensureRegistrationPin(config));
      config.setWhatsappRegisteredAt(Instant.now());
      config.setEmbeddedSignupLastError(null);
    } catch (RuntimeException erro) {
      config.setEmbeddedSignupLastError(sanitizeEmbeddedError(erro.getMessage()));
      LOG.warn(
          "whatsapp.autoRegister.failed tenantId={} reason={}",
          config.getTenantId(), sanitizeEmbeddedError(erro.getMessage()));
      return false;
    }
    // Inscrever o webhook e o que faz o salao RECEBER. Falhar aqui nao desfaz o registro: ficar
    // mudo e pior que nao receber, e o aviso da tela cobre o que ficou pela metade.
    try {
      whatsAppClient.inscreverNoWebhook(config);
    } catch (RuntimeException erro) {
      LOG.warn(
          "whatsapp.autoSubscribe.failed tenantId={} reason={}",
          config.getTenantId(), sanitizeEmbeddedError(erro.getMessage()));
    }

    // Os templates do Azzo nao existem na conta do salao — template pertence a WABA. Criar o de
    // teste aqui e o que permite provar a integracao sem o dono aprovar nada na Meta.
    servicoTemplates.criarTemplateDeTeste(config);
    return true;
  }

  /**
   * Registra o numero no Cloud API, para uma conexao que ja existe.
   *
   * <p>Faz os dois passos que faltavam no onboarding antigo: o registro, sem o qual o numero nao
   * envia, e a inscricao do webhook, sem a qual ele nao recebe. A inscricao NAO derruba o
   * resultado: registrar ja destrava o envio, e falhar em receber e um problema menor que ficar
   * mudo — mas o retorno diz qual dos dois aconteceu, em vez de esconder.
   */
  @Transactional
  public TenantWhatsAppDtos.RegistroDoNumeroResponse registrarNumero() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    TenantWhatsAppDtos.RegistroDoNumeroResponse response =
        new TenantWhatsAppDtos.RegistroDoNumeroResponse();

    try {
      String pin = ensureRegistrationPin(config);
      whatsAppClient.registrarNumero(config, pin);
      config.setWhatsappRegisteredAt(Instant.now());
      config.setEmbeddedSignupLastError(null);
      // Quem aperta este botao esta destravando uma conexao que ja queria funcionar: deixar
      // desligado obrigaria a um segundo passo sem motivo. O interruptor continua na tela.
      config.setWhatsappEnabled(true);
      response.success = true;
      response.registrationPin = pin;
      response.message = "Numero registrado no WhatsApp Cloud API. Guarde o PIN: sem ele o numero nao migra de provedor.";

      try {
        whatsAppClient.inscreverNoWebhook(config);
        response.webhookInscrito = true;
      } catch (RuntimeException erroDoWebhook) {
        response.webhookInscrito = false;
        response.message +=
            " O envio esta liberado, mas a inscricao do webhook falhou — o salao ainda nao RECEBE"
                + " mensagem. A Meta respondeu: " + sanitizeEmbeddedError(erroDoWebhook.getMessage());
      }

      tenantWhatsAppConfigRepository.save(config);
      registrarAuditoria(tenantId, "WHATSAPP_NUMBER_REGISTER", tenantId.toString(), null,
          java.util.Map.of("success", true, "webhookInscrito", response.webhookInscrito), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.message = mapTestConnectionError(ex);
      registrarAuditoria(tenantId, "WHATSAPP_NUMBER_REGISTER", tenantId.toString(), null,
          java.util.Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  @Transactional
  public TenantWhatsAppDtos.TestMessageResponse enviarMensagemTeste(TenantWhatsAppDtos.TestMessageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    TenantWhatsAppDtos.TestMessageResponse response = new TenantWhatsAppDtos.TestMessageResponse();
    String destino = trimToNull(request.destinationPhone);

    // TEMPLATE por padrao, e nao texto livre.
    //
    // Texto livre so e entregue dentro de 24h da ultima mensagem do CLIENTE. O teste quase sempre
    // e primeiro contato — ninguem escreveu para o salao ainda —, e nesse caso a Cloud API aceita,
    // devolve wamid e DESCARTA: em 2026-09-22 foram tres envios com wamid valido, nenhum entregue,
    // e a tela dizendo "Entregue". Um teste que "passa" sem nada chegar e pior que nao ter teste.
    //
    // Texto livre continua acessivel para quem pedir explicitamente (`message` preenchido): dentro
    // da janela ele funciona, e e o que o atendimento de verdade usa.
    String texto = trimToNull(request.message);
    boolean porTemplate = texto == null;
    // O template do PROPRIO salao vem primeiro: `teste_integracao` e criado na conta dele, e e
    // o que prova a integracao DELE. O valor de ambiente virou reserva — serve so enquanto a
    // sincronizacao nao rodou, e para forcar outro template em diagnostico.
    var testeDoSalao = servicoTemplates.templateDeTesteDoTenant(tenantId);
    String template =
        firstNonBlank(
            trimToNull(request.templateName),
            testeDoSalao.map(t -> t.getNome()).orElse(null),
            templateDeTeste);
    String idioma =
        firstNonBlank(
            trimToNull(request.templateLanguage),
            testeDoSalao.map(t -> t.getIdioma()).orElse(null),
            idiomaDoTemplateDeTeste);
    // Template que a Meta ainda nao aprovou NAO entrega: dizer isso junto evita a pessoa culpar
    // a credencial por uma recusa que e so de analise pendente.
    String avisoDeAnalise =
        testeDoSalao.filter(t -> !t.aprovado()).isPresent()
            ? " O modelo ainda nao foi aprovado pela Meta, entao a recusa pode ser so isso."
            : "";
    String descricaoNoLog = porTemplate ? "template: " + template + " (" + idioma + ")" : texto;

    try {
      String providerMessageId =
          porTemplate
              ? whatsAppClient.enviarTemplate(config, destino, template, idioma)
              : whatsAppClient.sendMessage(config, destino, texto);
      response.success = true;
      response.providerMessageId = providerMessageId;
      // "Aceita pela Meta", e nao "entregue": o wamid prova que ela aceitou, e so o status por
      // webhook diz se chegou. Prometer entrega aqui foi exatamente o que enganou em 22/09.
      response.message =
          porTemplate
              ? "Template \"" + template + "\" aceito pela Meta. Se nao chegar, veja se ele esta"
                  + " aprovado e se o idioma confere."
                  + avisoDeAnalise
              : "Mensagem aceita pela Meta. Texto livre so e entregue se o cliente escreveu para o"
                  + " salao nas ultimas 24 horas.";
      registrarNoLog(tenantId, destino, descricaoNoLog, providerMessageId, null);
      registrarAuditoria(tenantId, "WHATSAPP_TEST_MESSAGE", tenantId.toString(), null, java.util.Map.of("success", true, "destination", destino != null ? destino : ""), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.message = mapTestConnectionError(ex);
      // A Meta acabou de dizer que o numero nao esta registrado: a flag esta errada, e insistir
      // nela esconderia o botao que resolve.
      esquecerRegistroSeAMetaDiscordar(config, ex.getMessage());
      // O erro CRU da Meta vai para o log da tela, e nao a versao traduzida: e o que permite
      // pesquisar o codigo depois, quando o aviso da tela ja sumiu.
      registrarNoLog(
          tenantId, destino, descricaoNoLog, null,
          ex.getMessage() == null || ex.getMessage().isBlank() ? response.message : ex.getMessage().trim());
      registrarAuditoria(tenantId, "WHATSAPP_TEST_MESSAGE", tenantId.toString(), null, java.util.Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  /**
   * Traduz a falha para quem esta na tela de integracao.
   *
   * <p><b>O que a Meta disse nao pode ser engolido.</b> Ate 2026-09-21 todo erro nao reconhecido
   * virava "revise as credenciais" — e em 20/09 isso mandou o dono conferir um token que estava
   * correto: a Meta recusava com {@code (#133010) Account not registered}, que e o numero ainda nao
   * registrado no Cloud API, e nao credencial errada. Diagnosticar exigiu abrir o log do servidor
   * para ver a mensagem que a propria resposta ja trazia.
   *
   * <p>Por isso o texto da Meta vai junto no caso desconhecido: o proximo erro que ninguem previu
   * se explica na tela, em vez de mandar a pessoa arrumar o que nao esta quebrado.
   */
  private String mapTestConnectionError(Exception error) {
    String original = error == null || error.getMessage() == null ? "" : error.getMessage().trim();
    String message = original.toLowerCase();
    if (message.contains("token do whatsapp nao configurado")) {
      return "Token de acesso do WhatsApp nao configurado. Salve a configuracao antes de testar.";
    }
    if (message.contains("phonenumberid do whatsapp nao configurado")) {
      return "Phone Number ID do WhatsApp nao configurado. Revise os dados e tente novamente.";
    }
    if (message.contains("invalid oauth access token")
        || message.contains("token nao autorizado")
        || message.contains("token n")
        || message.contains("not authorized")) {
      return "Falha ao validar a conexao com o WhatsApp. Revise o token de acesso informado e tente novamente.";
    }
    // 133010: o numero existe e esta verificado (ler os dados dele funciona), mas falta o registro
    // no Cloud API — um passo unico, feito no painel da Meta, que o Azzo nao executa.
    if (message.contains("133010") || message.contains("account not registered")) {
      // O texto da Meta vai junto mesmo no caso reconhecido: e o que se pesquisa e o que se manda
      // para o suporte dela. Explicar sem mostrar o original obriga a abrir o log do servidor.
      return comDetalheDaMeta(
          "O numero ainda nao foi registrado no WhatsApp Cloud API. O token e o Phone Number ID"
              + " estao corretos; falta concluir o registro do numero no painel da Meta (Cloud API >"
              + " registrar numero, com o PIN de verificacao em duas etapas) antes de enviar"
              + " mensagens.",
          original);
    }
    if (original.isBlank()) {
      return "Falha ao validar a conexao com o WhatsApp. Revise as credenciais configuradas e tente novamente.";
    }
    return "Falha ao enviar pelo WhatsApp. A Meta respondeu: " + original;
  }

  /** A explicacao em portugues, com o que a Meta respondeu entre parenteses. */
  private String comDetalheDaMeta(String explicacao, String original) {
    if (original == null || original.isBlank()) return explicacao;
    return explicacao + " (a Meta respondeu: " + original + ")";
  }

  /**
   * Grava o envio de teste em "Mensagens enviadas".
   *
   * <p><b>A tabela {@code whatsapp_message_log} so era LIDA.</b> A tela tem a secao de mensagens
   * enviadas, com o motivo da falha em vermelho, e nada no backend escrevia ali — ficava
   * permanentemente vazia (lacuna do porte do Quarkus, achada em 2026-09-22). O resultado: o unico
   * lugar onde o erro aparecia era um aviso que desaparece da tela, e depois so o log do servidor.
   *
   * <p>Falhar aqui nao pode derrubar o envio: o registro e consequencia, e nao o objetivo.
   */
  private void registrarNoLog(
      UUID tenantId, String destino, String texto, String providerMessageId, String erro) {
    try {
      WhatsAppMessageLogEntity entrada = new WhatsAppMessageLogEntity();
      entrada.setTenantId(tenantId);
      entrada.setEventType("TEST_MESSAGE");
      entrada.setDestinationPhone(destino == null || destino.isBlank() ? "-" : destino);
      entrada.setMessageText(texto);
      entrada.setProviderMessageId(providerMessageId);
      entrada.setStatus(erro == null ? "SENT" : "FAILED");
      entrada.setErrorMessage(erro);
      messageLogRepository.save(entrada);
    } catch (Exception falhaAoRegistrar) {
      LOG.warn(
          "whatsapp.testMessage.logFailed tenantId={} reason={}",
          tenantId, falhaAoRegistrar.getMessage());
    }
  }

  @Transactional
  public TenantWhatsAppDtos.ConfigResponse obterConfiguracaoAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return toConfigResponse(config);
  }

  @Transactional
  public TenantWhatsAppDtos.EmbeddedSignupStatusResponse concluirEmbeddedSignup(
      TenantWhatsAppDtos.EmbeddedSignupCompleteRequest request) {
    if (!embeddedSignupEnabled) {
      TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = obterStatusEmbeddedSignup();
      response.connected = false;
      response.lastError = "Embedded Signup desabilitado neste ambiente.";
      return response;
    }
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);

    String code = trimToNull(request.code);
    String phoneNumberId = request.setupInfo != null ? trimToNull(request.setupInfo.phoneNumberId) : null;
    String businessAccountId = request.setupInfo != null ? trimToNull(request.setupInfo.wabaId) : null;
    String businessId = request.setupInfo != null ? trimToNull(request.setupInfo.businessId) : null;

    config.setWhatsappOnboardingStatus(ONBOARDING_PENDING_EXCHANGE);
    config.setEmbeddedSignupLastError(null);

    try {
      String accessToken = metaEmbeddedSignupClient.exchangeCodeForAccessToken(code);
      MetaEmbeddedSignupClient.PhoneNumberDetails phoneDetails =
          metaEmbeddedSignupClient.fetchPhoneNumberDetails(accessToken, phoneNumberId);

      config.setWhatsappAccessTokenEnc(encryptionService.encrypt(accessToken));
      config.setWhatsappPhoneNumberId(phoneDetails.id != null ? phoneDetails.id : phoneNumberId);
      config.setWhatsappBusinessAccountId(businessAccountId);
      config.setMetaBusinessId(businessId);
      config.setDisplayPhoneNumber(
          firstNonBlank(phoneDetails.displayPhoneNumber, request.setupInfo.phoneNumber, config.getDisplayPhoneNumber()));
      config.setWhatsappTokenSource(TOKEN_SOURCE_EMBEDDED);
      ensureWebhookVerifyToken(config);

      // O QUE FALTAVA. Guardar a credencial nao faz o numero enviar: enquanto ele nao for
      // registrado no Cloud API, todo envio morre com "(#133010) Account not registered" — e a
      // validacao antiga (`testConnection`, que por dentro e uma LEITURA) passava mesmo assim.
      // Resultado: o onboarding dizia "conectado", ligava o WhatsApp e o salao so descobria quando
      // um cliente reclamava de nao ter recebido a confirmacao.
      //
      // Registrar e inscrever o webhook sao acoes do PROVEDOR: feitas com o token da integracao,
      // sem o cliente fazer nada.
      boolean registrado = registrarSeNecessario(config);

      // A credencial e boa: a conexao esta feita, e obrigar a refazer o popup por causa do
      // registro seria perder o que deu certo. Mas LIGAR um numero que nao envia era o bug de
      // origem — entao liga apenas se ele registrou. Nao registrou: conectado, desligado, com o
      // aviso e o botao na tela.
      config.setWhatsappOnboardingStatus(ONBOARDING_CONNECTED);
      config.setEmbeddedSignupCompletedAt(Instant.now());
      config.setWhatsappEnabled(registrado);
      tenantWhatsAppConfigRepository.save(config);
      TenantWhatsAppDtos.EmbeddedSignupStatusResponse signupResult = toEmbeddedSignupStatus(config);
      registrarAuditoria(tenantId, "WHATSAPP_EMBEDDED_SIGNUP_COMPLETE", tenantId.toString(), null, java.util.Map.of("status", ONBOARDING_CONNECTED), true);
      return signupResult;
    } catch (RuntimeException e) {
      config.setWhatsappEnabled(false);
      config.setWhatsappOnboardingStatus(ONBOARDING_FAILED);
      config.setEmbeddedSignupLastError(sanitizeEmbeddedError(e.getMessage()));
      tenantWhatsAppConfigRepository.save(config);
      registrarAuditoria(tenantId, "WHATSAPP_EMBEDDED_SIGNUP_COMPLETE", tenantId.toString(), null, java.util.Map.of("status", ONBOARDING_FAILED), false);
      return toEmbeddedSignupStatus(config);
    }
  }

  @Transactional
  public TenantWhatsAppDtos.EmbeddedSignupStatusResponse obterStatusEmbeddedSignup() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return toEmbeddedSignupStatus(config);
  }

  public TenantWhatsAppDtos.MessageLogResponse listarMensagens(int limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
    List<WhatsAppMessageLogEntity> fetched =
        messageLogRepository.findByTenantIdOrderBySentAtDesc(tenantId, PageRequest.of(0, normalizedLimit + 1));
    boolean hasMore = fetched.size() > normalizedLimit;
    List<WhatsAppMessageLogEntity> page = fetched.stream().limit(normalizedLimit).toList();
    TenantWhatsAppDtos.MessageLogResponse response = new TenantWhatsAppDtos.MessageLogResponse();
    response.items = page.stream().map(e -> {
      TenantWhatsAppDtos.MessageLogItem item = new TenantWhatsAppDtos.MessageLogItem();
      item.id = e.getId() != null ? e.getId().toString() : null;
      item.eventType = e.getEventType();
      item.destinationPhone = e.getDestinationPhone();
      item.messageText = e.getMessageText();
      item.providerMessageId = e.getProviderMessageId();
      item.status = e.getStatus();
      item.errorMessage = e.getErrorMessage();
      item.sentAt = e.getSentAt() != null ? e.getSentAt().toString() : null;
      item.appointmentId = e.getAppointmentId() != null ? e.getAppointmentId().toString() : null;
      return item;
    }).toList();
    response.hasMore = hasMore;
    if (hasMore && !page.isEmpty()) {
      WhatsAppMessageLogEntity last = page.get(page.size() - 1);
      response.nextCursorSentAt = last.getSentAt() != null ? last.getSentAt().toString() : null;
    }
    return response;
  }

  private TenantWhatsAppDtos.ConfigResponse toConfigResponse(TenantWhatsAppConfig config) {
    TenantWhatsAppDtos.ConfigResponse response = new TenantWhatsAppDtos.ConfigResponse();
    response.phoneNumberId = config.getWhatsappPhoneNumberId();
    response.businessAccountId = config.getWhatsappBusinessAccountId();
    response.businessId = config.getMetaBusinessId();
    response.displayPhoneNumber = config.getDisplayPhoneNumber();
    response.webhookVerifyToken = decryptWebhookVerifyToken(config);
    response.numeroRegistrado = config.getWhatsappRegisteredAt() != null;
    response.registrationPin = decryptRegistrationPin(config);
    response.confirmationTemplateName = config.getConfirmationTemplateName();
    response.confirmationTemplateLanguage = config.getConfirmationTemplateLanguage();
    response.accessTokenConfigured = hasAccessTokenConfigured(config);
    response.webhookVerifyTokenConfigured =
        config.getWhatsappWebhookVerifyTokenEnc() != null && !config.getWhatsappWebhookVerifyTokenEnc().isBlank();
    response.whatsappEnabled = config.isWhatsappEnabled();
    response.onboardingStatus = defaultOnboardingStatus(config.getWhatsappOnboardingStatus());
    response.tokenSource = defaultTokenSource(config.getWhatsappTokenSource());
    response.embeddedSignupEnabled = embeddedSignupEnabled;
    response.usageProfile = config.getWhatsappUsageProfile() != null ? config.getWhatsappUsageProfile() : "COMPLETE";
    response.canSchedule = config.isCanSchedule();
    response.canCancel = config.isCanCancel();
    response.canReschedule = config.isCanReschedule();
    response.confirmationMessageTemplate = config.getConfirmationMessageTemplate();
    response.cancellationMessageTemplate = config.getCancellationMessageTemplate();
    response.reminderMessageTemplate = config.getReminderMessageTemplate();
    return response;
  }

  private TenantWhatsAppDtos.EmbeddedSignupStatusResponse toEmbeddedSignupStatus(TenantWhatsAppConfig config) {
    TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = new TenantWhatsAppDtos.EmbeddedSignupStatusResponse();
    response.connected = ONBOARDING_CONNECTED.equals(defaultOnboardingStatus(config.getWhatsappOnboardingStatus()));
    response.whatsappEnabled = config.isWhatsappEnabled();
    response.accessTokenConfigured = hasAccessTokenConfigured(config);
    response.webhookVerifyTokenConfigured =
        config.getWhatsappWebhookVerifyTokenEnc() != null && !config.getWhatsappWebhookVerifyTokenEnc().isBlank();
    response.webhookVerifyToken = decryptWebhookVerifyToken(config);
    response.onboardingStatus = defaultOnboardingStatus(config.getWhatsappOnboardingStatus());
    response.tokenSource = defaultTokenSource(config.getWhatsappTokenSource());
    response.phoneNumberId = config.getWhatsappPhoneNumberId();
    response.businessAccountId = config.getWhatsappBusinessAccountId();
    response.businessId = config.getMetaBusinessId();
    response.displayPhoneNumber = config.getDisplayPhoneNumber();
    response.lastError = config.getEmbeddedSignupLastError();
    response.embeddedSignupEnabled = embeddedSignupEnabled;
    return response;
  }

  private boolean hasAccessTokenConfigured(TenantWhatsAppConfig config) {
    return config != null
        && config.getWhatsappAccessTokenEnc() != null
        && !config.getWhatsappAccessTokenEnc().isBlank();
  }

  private void registrarAuditoria(UUID tenantId, String action, String entityId, Object before, Object after, boolean success) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.WHATSAPP;
      command.action = action;
      command.entityType = "WHATSAPP_CONFIG";
      command.entityId = entityId;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      if (success) {
        auditService.recordSuccess(command);
      } else {
        auditService.recordError(command);
      }
    } catch (Exception e) {
      LOG.warn("Falha ao registrar auditoria whatsapp action={}", action, e);
    }
  }

  private boolean isValidUsageProfile(String profile) {
    return "REACTIVE_ONLY".equals(profile) || "NOTIFICATIONS".equals(profile) || "COMPLETE".equals(profile);
  }

  private void applyProfileDefaults(TenantWhatsAppConfig config, String profile) {
    switch (profile) {
      case "REACTIVE_ONLY" -> {
        config.setCanSchedule(false);
        config.setCanCancel(false);
        config.setCanReschedule(false);
      }
      case "NOTIFICATIONS" -> {
        config.setCanSchedule(true);
        config.setCanCancel(true);
        config.setCanReschedule(true);
      }
      case "COMPLETE" -> {
        config.setCanSchedule(true);
        config.setCanCancel(true);
        config.setCanReschedule(true);
      }
    }
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String decryptAccessToken(TenantWhatsAppConfig config) {
    if (!hasAccessTokenConfigured(config)) return null;
    return trimToNull(encryptionService.decrypt(config.getWhatsappAccessTokenEnc()));
  }

  private WhatsAppClient.PhoneNumberDetails validatePhoneNumberConfiguration(String accessToken, String phoneNumberId) {
    if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
      return null;
    }
    return whatsAppClient.fetchPhoneNumberDetails(accessToken, phoneNumberId);
  }

  /**
   * O PIN de 6 digitos do registro, criado uma vez e reaproveitado.
   *
   * <p>No modelo de provedor quem define o PIN de um numero novo e o Azzo — o dono do salao nao
   * tem motivo para escolher um, e pedir seria atrito sem ganho. Mas guardar e obrigatorio: sem o
   * PIN o numero nao pode ser re-registrado nem migrado para outro provedor depois, e isso e um
   * direito do cliente sobre o numero dele. Fica criptografado e visivel ao dono na tela, igual ao
   * verify token do webhook.
   *
   * <p>Reaproveitar em vez de sortear de novo e o que permite reconectar sem perder o acesso ao
   * numero: um PIN novo nao substitui o antigo sem passar pelo antigo.
   */
  private String ensureRegistrationPin(TenantWhatsAppConfig config) {
    String atual = decryptRegistrationPin(config);
    if (atual != null) return atual;
    String novo = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
    config.setWhatsappRegistrationPinEnc(encryptionService.encrypt(novo));
    return novo;
  }

  private String decryptRegistrationPin(TenantWhatsAppConfig config) {
    if (config == null
        || config.getWhatsappRegistrationPinEnc() == null
        || config.getWhatsappRegistrationPinEnc().isBlank()) {
      return null;
    }
    try {
      return trimToNull(encryptionService.decrypt(config.getWhatsappRegistrationPinEnc()));
    } catch (RuntimeException e) {
      // Chave de criptografia trocada: melhor sortear um PIN novo do que travar o onboarding.
      LOG.warn("whatsapp.registrationPin.decryptFailed tenantId={}", config.getTenantId());
      return null;
    }
  }

  private void ensureWebhookVerifyToken(TenantWhatsAppConfig config) {
    if (config == null) return;
    if (config.getWhatsappWebhookVerifyTokenEnc() != null && !config.getWhatsappWebhookVerifyTokenEnc().isBlank()) {
      return;
    }
    assignWebhookVerifyToken(config, generateWebhookVerifyToken());
  }

  private void assignWebhookVerifyToken(TenantWhatsAppConfig config, String rawToken) {
    String normalized = trimToNull(rawToken);
    config.setWhatsappWebhookVerifyTokenEnc(encryptionService.encrypt(normalized));
    config.setWhatsappWebhookVerifyTokenHash(webhookVerifyTokenHashService.hash(normalized));
  }

  private String decryptWebhookVerifyToken(TenantWhatsAppConfig config) {
    if (config == null
        || config.getWhatsappWebhookVerifyTokenEnc() == null
        || config.getWhatsappWebhookVerifyTokenEnc().isBlank()) {
      return null;
    }
    return trimToNull(encryptionService.decrypt(config.getWhatsappWebhookVerifyTokenEnc()));
  }

  private String generateWebhookVerifyToken() {
    return "wa_verify_" + UUID.randomUUID().toString().replace("-", "");
  }

  private String defaultOnboardingStatus(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? ONBOARDING_NOT_STARTED : normalized;
  }

  private String defaultTokenSource(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? TOKEN_SOURCE_MANUAL : normalized;
  }

  private String sanitizeEmbeddedError(String message) {
    if (message == null || message.isBlank()) return "Falha ao concluir Embedded Signup.";
    String sanitized = message.replaceAll("[\\r\\n]+", " ").trim();
    return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) return normalized;
    }
    return null;
  }
}
