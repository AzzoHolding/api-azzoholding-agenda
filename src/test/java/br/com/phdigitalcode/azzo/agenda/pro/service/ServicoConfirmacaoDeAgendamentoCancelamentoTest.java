package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AGD-008: o cliente cujo horario o salao cancelou recebe o aviso (antes, o cancelamento so
 * logava "NAO enviada"). Os portoes sao os da confirmacao: tenant com notificacao ligada, WhatsApp
 * conectado, envio proativo permitido, opt-out de LGPD e telefone valido.
 */
class ServicoConfirmacaoDeAgendamentoCancelamentoTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final String FONE = "11999990000";

  private ServicoTemplatesDoWhatsapp servicoTemplates;
  private ClienteRepository clienteRepository;
  private TenantOperationalSettingsRepository operationalSettingsRepository;
  private TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private CustomerCommunicationChannelResolver canalDoCliente;
  private ServicoConfirmacaoDeAgendamento service;

  private TenantOperationalSettings configuracoes;
  private TenantWhatsAppConfig whatsapp;
  private Cliente cliente;
  private Agendamento agendamento;

  @BeforeEach
  void preparar() {
    servicoTemplates = mock(ServicoTemplatesDoWhatsapp.class);
    clienteRepository = mock(ClienteRepository.class);
    operationalSettingsRepository = mock(TenantOperationalSettingsRepository.class);
    tenantWhatsAppConfigRepository = mock(TenantWhatsAppConfigRepository.class);
    canalDoCliente = mock(CustomerCommunicationChannelResolver.class);
    service =
        new ServicoConfirmacaoDeAgendamento(
            servicoTemplates,
            clienteRepository,
            mock(AgendamentoItemRepository.class),
            mock(ServicoRepository.class),
            mock(ProfissionalRepository.class),
            mock(TenantRepository.class),
            tenantWhatsAppConfigRepository,
            operationalSettingsRepository,
            canalDoCliente);

    configuracoes = new TenantOperationalSettings();
    configuracoes.setWhatsappNotifications(true);
    when(operationalSettingsRepository.findByTenantIdOrCreate(TENANT)).thenReturn(configuracoes);

    whatsapp = new TenantWhatsAppConfig();
    whatsapp.setWhatsappEnabled(true);
    whatsapp.setWhatsappUsageProfile("COMPLETE");
    when(tenantWhatsAppConfigRepository.findByTenantIdOrCreate(TENANT)).thenReturn(whatsapp);

    cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(TENANT);
    cliente.setName("Marina");
    cliente.setPhone(FONE);
    cliente.setWhatsappOptOut(false);
    when(clienteRepository.findByIdAndTenantId(cliente.getId(), TENANT))
        .thenReturn(Optional.of(cliente));
    when(canalDoCliente.resolve(TENANT, cliente, FONE))
        .thenReturn(
            new CustomerCommunicationChannelResolver.ResolvedChannel(
                ChatChannel.WHATSAPP, "5511999990000", null));

    agendamento = new Agendamento();
    agendamento.setId(UUID.randomUUID());
    agendamento.setTenantId(TENANT);
    agendamento.setClientId(cliente.getId());
    agendamento.setDate(LocalDate.of(2026, 10, 7));
    agendamento.setStartTime("14:30");

    when(servicoTemplates.enviar(any(), anyString(), any(), anyString(), anyMap(), anyString()))
        .thenReturn(ChannelSendResult.sent("wamid.X"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void cancelamentoSaiPeloTemplateDeCancelamentoComDataEHora() {
    service.enviarCancelamento(TENANT, agendamento);

    ArgumentCaptor<Map<String, String>> valores = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
    verify(servicoTemplates)
        .enviar(
            eq(TENANT),
            eq(WhatsAppTemplateEntity.CANCELAMENTO),
            eq(ChatChannel.WHATSAPP),
            eq("5511999990000"),
            valores.capture(),
            texto.capture());
    assertThat(valores.getValue())
        .containsEntry("cliente", "Marina")
        .containsEntry("data", "07/10/2026")
        .containsEntry("hora", "14:30");
    assertThat(texto.getValue()).contains("Marina", "07/10/2026", "14:30", "cancelado");
  }

  /** LGPD: nao existe variavel de motivo, e o texto de reserva nao fala em motivo. */
  @Test
  @SuppressWarnings("unchecked")
  void cancelamentoNuncaLevaOMotivo() {
    service.enviarCancelamento(TENANT, agendamento);

    ArgumentCaptor<Map<String, String>> valores = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
    verify(servicoTemplates)
        .enviar(any(), anyString(), any(), anyString(), valores.capture(), texto.capture());
    assertThat(valores.getValue().keySet())
        .doesNotContain("motivo", "reason", "motivoDoFechamento");
    assertThat(texto.getValue().toLowerCase()).doesNotContain("motivo", "feriado", "fechamento");
  }

  @Test
  void naoEnviaQuandoOTenantDesligouAsNotificacoesDeWhatsApp() {
    configuracoes.setWhatsappNotifications(false);

    service.enviarCancelamento(TENANT, agendamento);

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
  }

  @Test
  void naoEnviaQuandoOWhatsAppNaoEstaConectado() {
    whatsapp.setWhatsappEnabled(false);

    service.enviarCancelamento(TENANT, agendamento);

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
  }

  @Test
  void naoEnviaQuandoOEnvioProativoNaoEPermitido() {
    whatsapp.setWhatsappUsageProfile("ASSISTANT_ONLY");

    service.enviarCancelamento(TENANT, agendamento);

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
  }

  @Test
  void respeitaOOptOutDeLgpdDoCliente() {
    cliente.setWhatsappOptOut(true);

    service.enviarCancelamento(TENANT, agendamento);

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
  }

  @Test
  void semTelefoneValidoNaoEnvia() {
    when(canalDoCliente.resolve(TENANT, cliente, FONE))
        .thenReturn(
            new CustomerCommunicationChannelResolver.ResolvedChannel(
                ChatChannel.WHATSAPP, "123", null));

    service.enviarCancelamento(TENANT, agendamento);

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
  }

  @Test
  void agendamentoSemClienteNaoEnvia() {
    agendamento.setClientId(null);

    service.enviarCancelamento(TENANT, agendamento);

    verify(servicoTemplates, never()).enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
  }

  /** O cancelamento ja aconteceu: falha de envio nunca pode derrubar quem chamou. */
  @Test
  void falhaDeEnvioNuncaPropaga() {
    when(servicoTemplates.enviar(any(), anyString(), any(), anyString(), anyMap(), anyString()))
        .thenThrow(new IllegalStateException("Meta fora do ar"));
    assertThatCode(() -> service.enviarCancelamento(TENANT, agendamento)).doesNotThrowAnyException();

    org.mockito.Mockito.doReturn(new ChannelSendResult(false, null, "131026", "Message undeliverable"))
        .when(servicoTemplates)
        .enviar(any(), anyString(), any(), anyString(), anyMap(), anyString());
    assertThatCode(() -> service.enviarCancelamento(TENANT, agendamento)).doesNotThrowAnyException();
  }

  /** Regressao do refactor: a confirmacao continua saindo pelo template de confirmacao. */
  @Test
  void aConfirmacaoContinuaUsandoOTemplateDeConfirmacao() {
    service.enviar(TENANT, agendamento);

    verify(servicoTemplates)
        .enviar(
            eq(TENANT),
            eq(WhatsAppTemplateEntity.CONFIRMACAO),
            eq(ChatChannel.WHATSAPP),
            eq("5511999990000"),
            anyMap(),
            anyString());
    verify(servicoTemplates, never())
        .enviar(any(), eq(WhatsAppTemplateEntity.CANCELAMENTO), any(), anyString(), anyMap(), anyString());
  }
}
