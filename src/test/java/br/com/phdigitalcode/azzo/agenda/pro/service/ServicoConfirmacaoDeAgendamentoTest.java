package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppTemplateEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;

/**
 * Ate 2026-09-24 a confirmacao era um placeholder que so logava: o cliente marcava um horario e
 * nao recebia nada. Sai por template aprovado, porque confirmacao de cliente novo e sempre
 * primeiro contato — e fora da janela de 24h o texto livre e aceito e descartado.
 */
class ServicoConfirmacaoDeAgendamentoTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID clienteId = UUID.randomUUID();
  private final UUID agendamentoId = UUID.randomUUID();

  private ServicoTemplatesDoWhatsapp servicoTemplates;
  private ClienteRepository clienteRepository;
  private AgendamentoItemRepository itemRepository;
  private ServicoRepository servicoRepository;
  private ProfissionalRepository profissionalRepository;
  private TenantRepository tenantRepository;
  private TenantWhatsAppConfigRepository configRepository;
  private TenantOperationalSettingsRepository settingsRepository;
  private CustomerCommunicationChannelResolver canalDoCliente;
  private ServicoConfirmacaoDeAgendamento servico;

  @BeforeEach
  void setUp() {
    servicoTemplates = mock(ServicoTemplatesDoWhatsapp.class);
    clienteRepository = mock(ClienteRepository.class);
    itemRepository = mock(AgendamentoItemRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    tenantRepository = mock(TenantRepository.class);
    configRepository = mock(TenantWhatsAppConfigRepository.class);
    settingsRepository = mock(TenantOperationalSettingsRepository.class);
    canalDoCliente = mock(CustomerCommunicationChannelResolver.class);
    servico =
        new ServicoConfirmacaoDeAgendamento(
            servicoTemplates, clienteRepository, itemRepository, servicoRepository,
            profissionalRepository, tenantRepository, configRepository, settingsRepository,
            canalDoCliente);

    TenantOperationalSettings settings = new TenantOperationalSettings();
    settings.setWhatsappNotifications(true);
    when(settingsRepository.findByTenantIdOrCreate(tenantId)).thenReturn(settings);

    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappEnabled(true);
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    when(clienteRepository.findByIdAndTenantId(clienteId, tenantId))
        .thenReturn(Optional.of(cliente(false)));
    when(canalDoCliente.resolve(any(), any(), any()))
        .thenReturn(
            new CustomerCommunicationChannelResolver.ResolvedChannel(
                ChatChannel.WHATSAPP, "5521987599613", null));
    when(itemRepository.findByTenantIdAndAppointmentId(tenantId, agendamentoId))
        .thenReturn(List.of());
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(servicoTemplates.enviar(any(), anyString(), any(), anyString(), any(), anyString()))
        .thenReturn(ChannelSendResult.sent("wamid.1"));
  }

  private Cliente cliente(boolean optOut) {
    Cliente cliente = new Cliente();
    cliente.setId(clienteId);
    cliente.setTenantId(tenantId);
    cliente.setName("Marina");
    cliente.setPhone("5521987599613");
    cliente.setWhatsappOptOut(optOut);
    return cliente;
  }

  private Agendamento agendamento() {
    Agendamento agendamento = new Agendamento();
    agendamento.setId(agendamentoId);
    agendamento.setTenantId(tenantId);
    agendamento.setClientId(clienteId);
    agendamento.setDate(LocalDate.of(2026, 9, 24));
    agendamento.setStartTime("14:30");
    return agendamento;
  }

  @Test
  @DisplayName("manda a confirmacao pelo template, com os dados do agendamento")
  void mandaPeloTemplateComOsDados() {
    servico.enviar(tenantId, agendamento());

    ArgumentCaptor<Map<String, String>> valores = ArgumentCaptor.forClass(Map.class);
    verify(servicoTemplates)
        .enviar(
            eq(tenantId), eq(WhatsAppTemplateEntity.CONFIRMACAO), eq(ChatChannel.WHATSAPP),
            eq("5521987599613"), valores.capture(), anyString());
    // A data vai como o cliente le, e nao no formato do banco.
    assertThat(valores.getValue()).containsEntry("cliente", "Marina").containsEntry("data", "24/09/2026");
    assertThat(valores.getValue()).containsEntry("hora", "14:30");
  }

  @Test
  @DisplayName("inclui o servico e o profissional quando existem")
  void incluiServicoEProfissional() {
    UUID servicoId = UUID.randomUUID();
    UUID profissionalId = UUID.randomUUID();
    AgendamentoItem item = new AgendamentoItem();
    item.setServiceId(servicoId);
    when(itemRepository.findByTenantIdAndAppointmentId(tenantId, agendamentoId))
        .thenReturn(List.of(item));
    Servico corte = new Servico();
    corte.setName("Corte feminino");
    when(servicoRepository.findByIdAndTenantId(servicoId, tenantId)).thenReturn(Optional.of(corte));
    Profissional bruna = new Profissional();
    bruna.setName("Bruna");
    when(profissionalRepository.findByIdAndTenantId(profissionalId, tenantId))
        .thenReturn(Optional.of(bruna));

    Agendamento agendamento = agendamento();
    agendamento.setProfessionalId(profissionalId);
    servico.enviar(tenantId, agendamento);

    ArgumentCaptor<Map<String, String>> valores = ArgumentCaptor.forClass(Map.class);
    verify(servicoTemplates)
        .enviar(any(), anyString(), any(), anyString(), valores.capture(), anyString());
    assertThat(valores.getValue())
        .containsEntry("servico", "Corte feminino")
        .containsEntry("profissional", "Bruna");
  }

  /** Conectar o WhatsApp e o consentimento: quem nao conectou nao passa a mandar nada. */
  @Test
  @DisplayName("salao sem WhatsApp ligado nao manda nada")
  void semWhatsappLigadoNaoManda() {
    TenantWhatsAppConfig desligado = new TenantWhatsAppConfig();
    desligado.setTenantId(tenantId);
    desligado.setWhatsappEnabled(false);
    when(configRepository.findByTenantIdOrCreate(tenantId)).thenReturn(desligado);

    servico.enviar(tenantId, agendamento());

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), any(), anyString());
  }

  @Test
  @DisplayName("notificacoes desligadas nas configuracoes param o envio")
  void notificacoesDesligadasParam() {
    TenantOperationalSettings settings = new TenantOperationalSettings();
    settings.setWhatsappNotifications(false);
    when(settingsRepository.findByTenantIdOrCreate(tenantId)).thenReturn(settings);

    servico.enviar(tenantId, agendamento());

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), any(), anyString());
  }

  /** LGPD: quem pediu para nao receber, nao recebe — nem a confirmacao. */
  @Test
  @DisplayName("cliente com opt-out nao recebe")
  void clienteComOptOutNaoRecebe() {
    when(clienteRepository.findByIdAndTenantId(clienteId, tenantId))
        .thenReturn(Optional.of(cliente(true)));

    servico.enviar(tenantId, agendamento());

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), any(), anyString());
  }

  @Test
  @DisplayName("cliente sem telefone valido nao vira tentativa de envio")
  void semTelefoneValidoNaoManda() {
    when(canalDoCliente.resolve(any(), any(), any()))
        .thenReturn(
            new CustomerCommunicationChannelResolver.ResolvedChannel(
                ChatChannel.WHATSAPP, "123", null));

    servico.enviar(tenantId, agendamento());

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), any(), anyString());
  }

  /** O agendamento ja foi criado: derrubar tudo por uma falha de envio seria trocar um problema
      pequeno por um grande. */
  @Test
  @DisplayName("falha no envio nunca sobe para quem criou o agendamento")
  void falhaNoEnvioNaoSobe() {
    when(servicoTemplates.enviar(any(), anyString(), any(), anyString(), any(), anyString()))
        .thenThrow(new IllegalStateException("(#131037) display name"));

    servico.enviar(tenantId, agendamento());
  }
}
