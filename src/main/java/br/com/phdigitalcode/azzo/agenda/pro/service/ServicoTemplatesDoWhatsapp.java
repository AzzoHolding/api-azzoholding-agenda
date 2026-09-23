package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppTemplateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppTemplateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelTemplateCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Cria e acompanha os templates de cada salao na conta DELE.
 *
 * <p>Template pertence a WABA, e nao ao app: os templates do Azzo nao existem na conta do cliente.
 * No modelo de provedor quem cria e o Azzo, com a permissao que o salao concedeu no popup — e o
 * dono nunca precisa saber que "template" existe.
 *
 * <p><b>Criar nao e aprovar.</b> A Meta analisa depois, e ate virar {@code APPROVED} o template
 * nao entrega nada. Por isso o estado fica guardado e e reconferido: um salao com template
 * recusado precisa saber ANTES de o cliente nao receber a confirmacao.
 */
@Service
public class ServicoTemplatesDoWhatsapp {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoTemplatesDoWhatsapp.class);

  /** O nome e PADRAO, e nao escolhido pelo salao: quem cria e o Azzo, em toda conta. */
  public static final String NOME_DO_TESTE = "teste_integracao";
  private static final String IDIOMA_PADRAO = "pt_BR";
  private static final String CATEGORIA = "UTILITY";

  /**
   * O texto do teste nao tem variavel, de proposito.
   *
   * <p>Ele existe para provar que a ponte funciona; variavel a mais e mais coisa para a Meta
   * recusar justamente no template que precisa passar primeiro.
   */
  private static final String CORPO_DO_TESTE =
      "Integracao do AZZO Agenda Pro concluida. Esta e uma mensagem automatica de teste.";

  private final TenantWhatsAppConfigRepository configRepository;
  private final WhatsAppTemplateRepository templateRepository;
  private final WhatsAppClient whatsAppClient;
  private final CommunicationChannelDispatcher dispatcher;

  public ServicoTemplatesDoWhatsapp(
      TenantWhatsAppConfigRepository configRepository,
      WhatsAppTemplateRepository templateRepository,
      WhatsAppClient whatsAppClient,
      CommunicationChannelDispatcher dispatcher) {
    this.configRepository = configRepository;
    this.templateRepository = templateRepository;
    this.whatsAppClient = whatsAppClient;
    this.dispatcher = dispatcher;
  }

  /**
   * Cria o template de teste na conta do salao. <b>Nunca lanca.</b>
   *
   * <p>Roda no fim do Embedded Signup, junto do registro do numero. Falhar aqui nao pode derrubar
   * a conexao: sem o template o salao ainda tem credencial valida, e a tela mostra o que faltou.
   */
  @Transactional
  public void criarTemplateDeTeste(TenantWhatsAppConfig config) {
    if (config == null || config.getTenantId() == null) return;
    try {
      criarOuAtualizar(
          config, WhatsAppTemplateEntity.TESTE, NOME_DO_TESTE, CORPO_DO_TESTE, List.of(), List.of());
    } catch (RuntimeException erro) {
      LOG.warn(
          "whatsapp.template.teste.falhou tenantId={} motivo={}",
          config.getTenantId(), erro.getMessage());
    }
  }

  /**
   * Cria na Meta os templates das mensagens automaticas que o salao escreveu na tela.
   *
   * <p>O texto e dele; a traducao para o formato que a Meta aprova e nossa. Um modelo vazio e
   * pulado — nao ha o que aprovar, e um template sem corpo seria recusado.
   */
  @Transactional
  public List<WhatsAppTemplateEntity> criarTemplatesDasMensagens(UUID tenantId) {
    TenantWhatsAppConfig config = configRepository.findByTenantIdOrCreate(tenantId);
    List<WhatsAppTemplateEntity> criados = new ArrayList<>();

    Map<String, String> modelos = new LinkedHashMap<>();
    modelos.put(WhatsAppTemplateEntity.CONFIRMACAO, config.getConfirmationMessageTemplate());
    modelos.put(WhatsAppTemplateEntity.CANCELAMENTO, config.getCancellationMessageTemplate());
    modelos.put(WhatsAppTemplateEntity.LEMBRETE, config.getReminderMessageTemplate());

    for (Map.Entry<String, String> modelo : modelos.entrySet()) {
      String texto = modelo.getValue();
      if (texto == null || texto.isBlank()) continue;
      ModeloParaTemplate.Convertido convertido = ModeloParaTemplate.converter(texto);
      criados.add(
          criarOuAtualizar(
              config,
              modelo.getKey(),
              nomePadrao(modelo.getKey()),
              convertido.corpo(),
              convertido.variaveis(),
              convertido.exemplos()));
    }
    return criados;
  }

  /** {@code azzo_confirmacao}, {@code azzo_cancelamento}, {@code azzo_lembrete} — iguais em todo salao. */
  static String nomePadrao(String finalidade) {
    return "azzo_" + finalidade.toLowerCase();
  }

  private WhatsAppTemplateEntity criarOuAtualizar(
      TenantWhatsAppConfig config,
      String finalidade,
      String nome,
      String corpo,
      List<String> variaveis,
      List<String> exemplos) {
    WhatsAppTemplateEntity registro =
        templateRepository
            .findByTenantIdAndFinalidade(config.getTenantId(), finalidade)
            .orElseGet(
                () -> {
                  WhatsAppTemplateEntity novo = new WhatsAppTemplateEntity();
                  novo.setTenantId(config.getTenantId());
                  novo.setFinalidade(finalidade);
                  return novo;
                });

    // Ja aprovado com o MESMO corpo nao volta para analise: recriar zeraria a aprovacao e o salao
    // ficaria sem mandar nada ate a Meta decidir de novo.
    if (registro.aprovado() && corpo.equals(registro.getCorpo())) {
      return registro;
    }

    WhatsAppClient.TemplateDetails detalhes =
        whatsAppClient.criarTemplate(config, nome, IDIOMA_PADRAO, CATEGORIA, corpo, exemplos);

    registro.setNome(nome);
    registro.setIdioma(IDIOMA_PADRAO);
    registro.setCorpo(corpo);
    registro.setVariaveis(String.join(",", variaveis));
    registro.setMetaTemplateId(detalhes.id);
    registro.setStatus(detalhes.status == null ? WhatsAppTemplateEntity.PENDING : detalhes.status);
    registro.setMotivoRecusa(null);
    return templateRepository.save(registro);
  }

  /**
   * Reconfere na Meta o estado dos templates que ainda nao foram decididos.
   *
   * <p>A Meta nao avisa quando aprova ou recusa — a nao ser pelo webhook
   * {@code message_template_status_update}, que depende de o webhook estar recebendo. Perguntar e
   * o caminho que funciona de qualquer jeito.
   *
   * @return quantos deixaram de estar pendentes
   */
  @Transactional
  public int sincronizarPendentes() {
    List<WhatsAppTemplateEntity> pendentes =
        templateRepository.findByStatus(WhatsAppTemplateEntity.PENDING);
    if (pendentes.isEmpty()) return 0;

    // Agrupado por salao: uma consulta por conta, e nao uma por template.
    Map<UUID, List<WhatsAppTemplateEntity>> porTenant = new LinkedHashMap<>();
    for (WhatsAppTemplateEntity pendente : pendentes) {
      porTenant.computeIfAbsent(pendente.getTenantId(), chave -> new ArrayList<>()).add(pendente);
    }

    int mudaram = 0;
    for (Map.Entry<UUID, List<WhatsAppTemplateEntity>> grupo : porTenant.entrySet()) {
      TenantWhatsAppConfig config = configRepository.findById(grupo.getKey()).orElse(null);
      if (config == null) continue;

      List<WhatsAppClient.TemplateDetails> naMeta;
      try {
        naMeta = whatsAppClient.listarTemplates(config);
      } catch (RuntimeException erro) {
        // Um salao com credencial vencida nao pode travar a conferencia dos outros.
        LOG.warn(
            "whatsapp.template.sync.falhou tenantId={} motivo={}", grupo.getKey(), erro.getMessage());
        continue;
      }

      for (WhatsAppTemplateEntity pendente : grupo.getValue()) {
        WhatsAppClient.TemplateDetails detalhe =
            naMeta.stream()
                .filter(item -> pendente.getNome().equalsIgnoreCase(item.name))
                .findFirst()
                .orElse(null);
        if (detalhe == null || detalhe.status == null) continue;
        if (detalhe.status.equalsIgnoreCase(pendente.getStatus())) continue;

        pendente.setStatus(detalhe.status);
        pendente.setMotivoRecusa(detalhe.rejectedReason);
        templateRepository.save(pendente);
        mudaram++;
        LOG.info(
            "whatsapp.template.status tenantId={} template={} status={} motivo={}",
            pendente.getTenantId(), pendente.getNome(), detalhe.status,
            detalhe.rejectedReason == null ? "-" : detalhe.rejectedReason);
      }
    }
    return mudaram;
  }

  /**
   * Manda a mensagem pelo template APROVADO, e cai no texto livre quando nao da.
   *
   * <p>E aqui que o template deixa de ser cadastro e vira entrega. Texto livre so chega a quem
   * escreveu para o salao nas ultimas 24h; lembrete e confirmacao sao quase sempre primeiro
   * contato, e sem template a Meta aceita e descarta.
   *
   * <p><b>Falta uma variavel, nao manda o template.</b> Mandar assim mesmo poria um espaco em
   * branco no lugar do nome do servico na mensagem que o cliente le — e a Meta aceitaria sem
   * reclamar. Texto livre errado pelo menos nao chega; template errado chega errado.
   */
  @Transactional(readOnly = true)
  public ChannelSendResult enviar(
      UUID tenantId,
      String finalidade,
      ChatChannel canal,
      String destino,
      Map<String, String> valores,
      String textoEquivalente) {

    WhatsAppTemplateEntity template =
        templateRepository.findByTenantIdAndFinalidade(tenantId, finalidade).orElse(null);

    if (template == null || !template.aprovado()) {
      return dispatcher.sendText(new ChannelSendCommand(tenantId, canal, destino, textoEquivalente));
    }

    List<String> nomes = variaveisDe(template);
    List<String> ordenados = new ArrayList<>();
    for (String nome : nomes) {
      String valor = valores == null ? null : valores.get(nome);
      if (valor == null || valor.isBlank()) {
        LOG.warn(
            "whatsapp.template.variavelFaltando tenantId={} template={} variavel={}",
            tenantId, template.getNome(), nome);
        return dispatcher.sendText(new ChannelSendCommand(tenantId, canal, destino, textoEquivalente));
      }
      ordenados.add(valor);
    }

    return dispatcher.sendTemplate(
        new ChannelTemplateCommand(
            tenantId, canal, destino, template.getNome(), template.getIdioma(), ordenados,
            textoEquivalente));
  }

  /** A ordem guardada e o contrato: e ela que liga {@code {{1}}} ao nome do cliente. */
  static List<String> variaveisDe(WhatsAppTemplateEntity template) {
    String guardadas = template.getVariaveis();
    if (guardadas == null || guardadas.isBlank()) return List.of();
    return java.util.Arrays.stream(guardadas.split(","))
        .map(String::trim)
        .filter(nome -> !nome.isEmpty())
        .toList();
  }

  @Transactional(readOnly = true)
  public List<WhatsAppTemplateEntity> listarDoTenant(UUID tenantId) {
    return templateRepository.findByTenantId(tenantId);
  }
}
