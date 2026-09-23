package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppTemplateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppTemplateRepository;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelTemplateCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Template pertence a WABA: os templates do Azzo nao existem na conta do salao, e no modelo de
 * provedor quem cria e o Azzo. <b>Criar nao e aprovar</b> — ate virar APPROVED nao entrega nada.
 */
class ServicoTemplatesDoWhatsappTest {

  private final UUID tenantId = UUID.randomUUID();

  private TenantWhatsAppConfigRepository configRepository;
  private WhatsAppTemplateRepository templateRepository;
  private WhatsAppClient whatsAppClient;
  private CommunicationChannelDispatcher dispatcher;
  private ServicoTemplatesDoWhatsapp servico;

  @BeforeEach
  void setUp() {
    configRepository = mock(TenantWhatsAppConfigRepository.class);
    templateRepository = mock(WhatsAppTemplateRepository.class);
    whatsAppClient = mock(WhatsAppClient.class);
    dispatcher = mock(CommunicationChannelDispatcher.class);
    when(dispatcher.sendText(any())).thenReturn(ChannelSendResult.sent("wamid.txt"));
    when(dispatcher.sendTemplate(any())).thenReturn(ChannelSendResult.sent("wamid.tpl"));
    servico =
        new ServicoTemplatesDoWhatsapp(
            configRepository, templateRepository, whatsAppClient, dispatcher);

    when(templateRepository.save(any(WhatsAppTemplateEntity.class)))
        .thenAnswer(invocacao -> invocacao.getArgument(0));
    when(templateRepository.findByTenantIdAndFinalidade(any(), anyString()))
        .thenReturn(Optional.empty());
  }

  private TenantWhatsAppConfig config() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappBusinessAccountId("waba-1");
    return config;
  }

  private WhatsAppClient.TemplateDetails criado(String status) {
    WhatsAppClient.TemplateDetails detalhes = new WhatsAppClient.TemplateDetails();
    detalhes.id = "tpl-1";
    detalhes.status = status;
    return detalhes;
  }

  @Test
  @DisplayName("o template de teste nasce com nome padrao e sem variavel")
  void templateDeTesteEPadraoESemVariavel() {
    when(whatsAppClient.criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(criado("PENDING"));

    servico.criarTemplateDeTeste(config());

    ArgumentCaptor<String> nome = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> exemplos = ArgumentCaptor.forClass(List.class);
    verify(whatsAppClient)
        .criarTemplate(any(), nome.capture(), eq("pt_BR"), eq("UTILITY"), anyString(), exemplos.capture());
    assertThat(nome.getValue()).isEqualTo("teste_integracao");
    // Variavel a mais e mais coisa para a Meta recusar no template que precisa passar primeiro.
    assertThat(exemplos.getValue()).isEmpty();
  }

  /** Falhar aqui nao pode derrubar a conexao: credencial valida vale mais que template. */
  @Test
  @DisplayName("template de teste que falha nao propaga o erro")
  void templateDeTesteQueFalhaNaoPropaga() {
    when(whatsAppClient.criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("(#100) template name already exists"));

    servico.criarTemplateDeTeste(config());
  }

  @Test
  @DisplayName("as mensagens da tela viram templates com as variaveis numeradas")
  void mensagensDaTelaViramTemplates() {
    TenantWhatsAppConfig config = config();
    config.setConfirmationMessageTemplate("Olá {cliente}! Seu {servico} é dia {data}.");
    config.setReminderMessageTemplate("Lembrete: {servico} amanhã às {hora}.");
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(criado("PENDING"));

    List<WhatsAppTemplateEntity> criados = servico.criarTemplatesDasMensagens(tenantId);

    // Cancelamento estava vazio: nao ha o que aprovar, e um template sem corpo seria recusado.
    assertThat(criados).hasSize(2);
    assertThat(criados).extracting(WhatsAppTemplateEntity::getNome)
        .containsExactly("azzo_confirmacao", "azzo_lembrete");
    assertThat(criados.get(0).getCorpo()).isEqualTo("Olá {{1}}! Seu {{2}} é dia {{3}}.");
    assertThat(criados.get(0).getVariaveis()).isEqualTo("cliente,servico,data");
    assertThat(criados.get(0).getStatus()).isEqualTo("PENDING");
  }

  /** Recriar um aprovado o devolveria para analise, e o salao ficaria sem mandar nada. */
  @Test
  @DisplayName("aprovado com o mesmo texto nao volta para analise")
  void aprovadoComOMesmoTextoNaoRecria() {
    TenantWhatsAppConfig config = config();
    config.setConfirmationMessageTemplate("Olá {cliente}!");
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    WhatsAppTemplateEntity jaAprovado = new WhatsAppTemplateEntity();
    jaAprovado.setTenantId(tenantId);
    jaAprovado.setFinalidade(WhatsAppTemplateEntity.CONFIRMACAO);
    jaAprovado.setNome("azzo_confirmacao");
    jaAprovado.setStatus(WhatsAppTemplateEntity.APPROVED);
    jaAprovado.setCorpo("Olá {{1}}!");
    when(templateRepository.findByTenantIdAndFinalidade(tenantId, WhatsAppTemplateEntity.CONFIRMACAO))
        .thenReturn(Optional.of(jaAprovado));

    servico.criarTemplatesDasMensagens(tenantId);

    verify(whatsAppClient, never())
        .criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  @DisplayName("o monitoramento traz a aprovacao e o motivo da recusa")
  void monitoramentoAtualizaOStatus() {
    WhatsAppTemplateEntity pendente = new WhatsAppTemplateEntity();
    pendente.setTenantId(tenantId);
    pendente.setFinalidade(WhatsAppTemplateEntity.CONFIRMACAO);
    pendente.setNome("azzo_confirmacao");
    pendente.setStatus(WhatsAppTemplateEntity.PENDING);
    when(templateRepository.findByStatus(WhatsAppTemplateEntity.PENDING)).thenReturn(List.of(pendente));
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config()));

    WhatsAppClient.TemplateDetails naMeta = new WhatsAppClient.TemplateDetails();
    naMeta.name = "azzo_confirmacao";
    naMeta.status = "REJECTED";
    naMeta.rejectedReason = "INVALID_FORMAT";
    when(whatsAppClient.listarTemplates(any())).thenReturn(List.of(naMeta));

    int mudaram = servico.sincronizarPendentes();

    assertThat(mudaram).isEqualTo(1);
    assertThat(pendente.getStatus()).isEqualTo("REJECTED");
    // Sem o motivo, o dono sabe que falhou e nao sabe o que corrigir.
    assertThat(pendente.getMotivoRecusa()).isEqualTo("INVALID_FORMAT");
  }

  /** Um salao com credencial vencida nao pode travar a conferencia dos outros. */
  @Test
  @DisplayName("salao que falha na consulta nao derruba o monitoramento")
  void falhaDeUmSalaoNaoDerrubaOResto() {
    WhatsAppTemplateEntity pendente = new WhatsAppTemplateEntity();
    pendente.setTenantId(tenantId);
    pendente.setFinalidade(WhatsAppTemplateEntity.CONFIRMACAO);
    pendente.setNome("azzo_confirmacao");
    pendente.setStatus(WhatsAppTemplateEntity.PENDING);
    when(templateRepository.findByStatus(WhatsAppTemplateEntity.PENDING)).thenReturn(List.of(pendente));
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config()));
    when(whatsAppClient.listarTemplates(any()))
        .thenThrow(new IllegalStateException("(#190) token expirado"));

    assertThat(servico.sincronizarPendentes()).isZero();
    assertThat(pendente.getStatus()).isEqualTo(WhatsAppTemplateEntity.PENDING);
  }

  private WhatsAppTemplateEntity aprovado(String variaveis) {
    WhatsAppTemplateEntity template = new WhatsAppTemplateEntity();
    template.setTenantId(tenantId);
    template.setFinalidade(WhatsAppTemplateEntity.LEMBRETE);
    template.setNome("azzo_lembrete");
    template.setIdioma("pt_BR");
    template.setStatus(WhatsAppTemplateEntity.APPROVED);
    template.setVariaveis(variaveis);
    return template;
  }

  @Test
  @DisplayName("com template aprovado, manda template com as variaveis na ordem guardada")
  void aprovadoMandaTemplateNaOrdem() {
    when(templateRepository.findByTenantIdAndFinalidade(tenantId, WhatsAppTemplateEntity.LEMBRETE))
        .thenReturn(Optional.of(aprovado("cliente,data,hora")));

    servico.enviar(
        tenantId, WhatsAppTemplateEntity.LEMBRETE, ChatChannel.WHATSAPP, "5511999998888",
        Map.of("hora", "14:30", "cliente", "Marina", "data", "23/09/2026"),
        "Lembrete: Marina, 23/09/2026 as 14:30.");

    ArgumentCaptor<ChannelTemplateCommand> captor =
        ArgumentCaptor.forClass(ChannelTemplateCommand.class);
    verify(dispatcher).sendTemplate(captor.capture());
    // A ordem e o contrato: e ela que liga {{1}} ao nome do cliente.
    assertThat(captor.getValue().variaveis()).containsExactly("Marina", "23/09/2026", "14:30");
    assertThat(captor.getValue().templateName()).isEqualTo("azzo_lembrete");
    verify(dispatcher, never()).sendText(any());
  }

  /** Enquanto a Meta nao aprova, o template nao entrega nada — texto livre ao menos tenta. */
  @Test
  @DisplayName("template pendente ainda manda texto livre")
  void pendenteCaiNoTextoLivre() {
    WhatsAppTemplateEntity pendente = aprovado("cliente");
    pendente.setStatus(WhatsAppTemplateEntity.PENDING);
    when(templateRepository.findByTenantIdAndFinalidade(tenantId, WhatsAppTemplateEntity.LEMBRETE))
        .thenReturn(Optional.of(pendente));

    servico.enviar(
        tenantId, WhatsAppTemplateEntity.LEMBRETE, ChatChannel.WHATSAPP, "5511999998888",
        Map.of("cliente", "Marina"), "Lembrete para Marina.");

    verify(dispatcher).sendText(any());
    verify(dispatcher, never()).sendTemplate(any());
  }

  /**
   * Mandar o template sem uma variavel poria um espaco em branco no lugar do nome do servico na
   * mensagem que o cliente le — e a Meta aceitaria sem reclamar.
   */
  @Test
  @DisplayName("faltando uma variavel, nao manda o template")
  void variavelFaltandoNaoMandaTemplate() {
    when(templateRepository.findByTenantIdAndFinalidade(tenantId, WhatsAppTemplateEntity.LEMBRETE))
        .thenReturn(Optional.of(aprovado("cliente,servico")));

    servico.enviar(
        tenantId, WhatsAppTemplateEntity.LEMBRETE, ChatChannel.WHATSAPP, "5511999998888",
        Map.of("cliente", "Marina"), "Lembrete para Marina.");

    verify(dispatcher).sendText(any());
    verify(dispatcher, never()).sendTemplate(any());
  }

  @Test
  @DisplayName("sem template cadastrado, segue o texto livre de sempre")
  void semTemplateSegueOTextoLivre() {
    when(templateRepository.findByTenantIdAndFinalidade(tenantId, WhatsAppTemplateEntity.LEMBRETE))
        .thenReturn(Optional.empty());

    servico.enviar(
        tenantId, WhatsAppTemplateEntity.LEMBRETE, ChatChannel.WHATSAPP, "5511999998888",
        Map.of(), "Lembrete de sempre.");

    verify(dispatcher).sendText(any());
  }

  /**
   * O caso legado: quem conectou antes de 2026-09-23 tem numero registrado e ZERO template,
   * porque a criacao automatica so roda no fim do Embedded Signup. Refazer o popup so para
   * disparar isso seria absurdo — o botao "Sincronizar" existe para esses.
   */
  @Test
  @DisplayName("sincronizar cria o que falta numa conexao antiga")
  void sincronizarCriaOQueFaltaNoLegado() {
    TenantWhatsAppConfig config = config();
    config.setConfirmationMessageTemplate("Olá {cliente}!");
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(criado("PENDING"));
    when(templateRepository.findByTenantId(tenantId)).thenReturn(List.of());
    when(whatsAppClient.listarTemplates(any())).thenReturn(List.of());

    servico.sincronizar(tenantId);

    ArgumentCaptor<String> nomes = ArgumentCaptor.forClass(String.class);
    verify(whatsAppClient, org.mockito.Mockito.times(2))
        .criarTemplate(any(), nomes.capture(), anyString(), anyString(), anyString(), anyList());
    assertThat(nomes.getAllValues()).containsExactly("teste_integracao", "azzo_confirmacao");
  }

  /** Nao saber o status ainda e diferente de a criacao ter dado errado. */
  @Test
  @DisplayName("consulta que falha nao derruba a sincronizacao")
  void consultaQueFalhaNaoDerrubaASincronizacao() {
    TenantWhatsAppConfig config = config();
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(criado("PENDING"));

    WhatsAppTemplateEntity teste = new WhatsAppTemplateEntity();
    teste.setTenantId(tenantId);
    teste.setFinalidade(WhatsAppTemplateEntity.TESTE);
    teste.setNome("teste_integracao");
    teste.setStatus(WhatsAppTemplateEntity.PENDING);
    when(templateRepository.findByTenantId(tenantId)).thenReturn(List.of(teste));
    when(whatsAppClient.listarTemplates(any()))
        .thenThrow(new IllegalStateException("(#190) token expirado"));

    assertThat(servico.sincronizar(tenantId)).hasSize(1);
  }

  @Test
  @DisplayName("sincronizar tambem traz o status de quem ja existia")
  void sincronizarAtualizaOStatusDeQuemJaExistia() {
    TenantWhatsAppConfig config = config();
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.criarTemplate(any(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(criado("PENDING"));

    WhatsAppTemplateEntity teste = new WhatsAppTemplateEntity();
    teste.setTenantId(tenantId);
    teste.setFinalidade(WhatsAppTemplateEntity.TESTE);
    teste.setNome("teste_integracao");
    teste.setStatus(WhatsAppTemplateEntity.PENDING);
    when(templateRepository.findByTenantId(tenantId)).thenReturn(List.of(teste));

    WhatsAppClient.TemplateDetails naMeta = new WhatsAppClient.TemplateDetails();
    naMeta.name = "teste_integracao";
    naMeta.status = "APPROVED";
    when(whatsAppClient.listarTemplates(any())).thenReturn(List.of(naMeta));

    servico.sincronizar(tenantId);

    assertThat(teste.getStatus()).isEqualTo("APPROVED");
  }
}
