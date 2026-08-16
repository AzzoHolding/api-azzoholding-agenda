package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureImpactDto;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantSpecialClosureDate;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppAppointmentNotificationService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantSpecialClosureDateRepository;

/**
 * Cobre {@link SpecialClosureService}, que substituiu o placeholder
 * {@code integration/SpecialClosureService} — aquele {@code isClosedAt} devolvia sempre
 * {@code false}, entao feriado, folga e bloqueio pontual nao bloqueavam nada.
 *
 * <p>Os quatro primeiros testes existem exatamente para travar esse comportamento: sao os quatro
 * ramos da cadeia de verificacao (all-day do salao, parcial do salao, all-day do profissional,
 * parcial do profissional), cada um provando que o servico devolve {@code true} <b>e</b> que para
 * de consultar assim que encontra um bloqueio.
 */
@ExtendWith(MockitoExtension.class)
class SpecialClosureServiceTest {

  private static final LocalDate DATA = LocalDate.of(2026, 12, 25);
  private static final LocalTime INICIO = LocalTime.of(10, 0);
  private static final LocalTime FIM = LocalTime.of(11, 0);

  private final UUID tenantId = UUID.randomUUID();
  private final UUID professionalId = UUID.randomUUID();

  @Mock private TenantSpecialClosureDateRepository closureRepository;
  @Mock private AgendamentoRepository agendamentoRepository;
  @Mock private ClienteRepository clienteRepository;
  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private AuditService auditService;
  @Mock private WhatsAppAppointmentNotificationService whatsAppNotificationService;

  private SpecialClosureService service;

  @BeforeEach
  void setUp() {
    service =
        new SpecialClosureService(
            closureRepository,
            agendamentoRepository,
            clienteRepository,
            profissionalRepository,
            auditService,
            whatsAppNotificationService);
  }

  // ---------- isClosedAt: os quatro ramos que o placeholder suprimia ----------

  @Test
  @DisplayName("fechamento all-day do salao bloqueia e curto-circuita os demais ramos")
  void allDayDoSalaoBloqueia() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(true);

    assertThat(service.isClosedAt(tenantId, professionalId, DATA, INICIO, FIM)).isTrue();

    verify(closureRepository, never()).existsPartialClosure(any(), any(), any(), any(), any());
    verify(closureRepository, never()).existsAllDayClosure(tenantId, DATA, professionalId);
  }

  @Test
  @DisplayName("fechamento parcial do salao que conflita com o intervalo bloqueia")
  void parcialDoSalaoBloqueia() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, null, DATA, INICIO, FIM))
        .thenReturn(true);

    assertThat(service.isClosedAt(tenantId, professionalId, DATA, INICIO, FIM)).isTrue();

    verify(closureRepository, never()).existsAllDayClosure(tenantId, DATA, professionalId);
  }

  @Test
  @DisplayName("fechamento all-day do profissional bloqueia")
  void allDayDoProfissionalBloqueia() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, null, DATA, INICIO, FIM))
        .thenReturn(false);
    when(closureRepository.existsAllDayClosure(tenantId, DATA, professionalId)).thenReturn(true);

    assertThat(service.isClosedAt(tenantId, professionalId, DATA, INICIO, FIM)).isTrue();

    verify(closureRepository, never())
        .existsPartialClosure(tenantId, professionalId, DATA, INICIO, FIM);
  }

  @Test
  @DisplayName("fechamento parcial do profissional bloqueia")
  void parcialDoProfissionalBloqueia() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, null, DATA, INICIO, FIM))
        .thenReturn(false);
    when(closureRepository.existsAllDayClosure(tenantId, DATA, professionalId)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, professionalId, DATA, INICIO, FIM))
        .thenReturn(true);

    assertThat(service.isClosedAt(tenantId, professionalId, DATA, INICIO, FIM)).isTrue();
  }

  @Test
  @DisplayName("sem nenhum fechamento cadastrado, o dia esta aberto")
  void semFechamentoNaoBloqueia() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, null, DATA, INICIO, FIM))
        .thenReturn(false);
    when(closureRepository.existsAllDayClosure(tenantId, DATA, professionalId)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, professionalId, DATA, INICIO, FIM))
        .thenReturn(false);

    assertThat(service.isClosedAt(tenantId, professionalId, DATA, INICIO, FIM)).isFalse();
  }

  @Test
  @DisplayName("sem profissional, so os dois ramos do salao sao avaliados")
  void semProfissionalAvaliaApenasOSalao() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(false);
    when(closureRepository.existsPartialClosure(tenantId, null, DATA, INICIO, FIM))
        .thenReturn(false);

    assertThat(service.isClosedAt(tenantId, null, DATA, INICIO, FIM)).isFalse();
  }

  @Test
  @DisplayName("sem horario informado, apenas os fechamentos all-day sao consultados")
  void semHorarioSoAvaliaAllDay() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(false);
    when(closureRepository.existsAllDayClosure(tenantId, DATA, professionalId)).thenReturn(false);

    assertThat(service.isClosedAt(tenantId, professionalId, DATA, null, null)).isFalse();

    verify(closureRepository, never()).existsPartialClosure(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("tenant ou data nulos devolvem false sem tocar no repositorio")
  void argumentosNulosNaoConsultam() {
    assertThat(service.isClosedAt(null, professionalId, DATA, INICIO, FIM)).isFalse();
    assertThat(service.isClosedAt(tenantId, professionalId, null, INICIO, FIM)).isFalse();
    verifyNoInteractions(closureRepository);
  }

  @Test
  @DisplayName("isClosedOnSpecialDate olha somente o all-day do salao")
  void isClosedOnSpecialDateOlhaSoOSalao() {
    when(closureRepository.existsAllDayClosure(tenantId, DATA, null)).thenReturn(true);

    assertThat(service.isClosedOnSpecialDate(tenantId, DATA)).isTrue();
    assertThat(service.isClosedOnSpecialDate(null, DATA)).isFalse();
    assertThat(service.isClosedOnSpecialDate(tenantId, null)).isFalse();
  }

  // ---------- criacao ----------

  @Test
  @DisplayName("sem agendamentos impactados o fechamento e criado e auditado")
  void criarSemImpactadosPersiste() {
    SpecialClosureDto dto = dtoAllDay();
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(
            invocation -> {
              TenantSpecialClosureDate e = invocation.getArgument(0);
              e.setId(UUID.randomUUID());
              return e;
            });

    SpecialClosureImpactDto result = service.criar(tenantId, dto);

    assertThat(result.created).isTrue();
    assertThat(result.closureId).isNotNull();
    assertThat(result.impactedAppointments).isNull();

    ArgumentCaptor<TenantSpecialClosureDate> captor =
        ArgumentCaptor.forClass(TenantSpecialClosureDate.class);
    verify(closureRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getClosureType()).isEqualTo("HOLIDAY");
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  @DisplayName("com impactados nada e persistido e a lista volta com nomes resolvidos")
  void criarComImpactadosNaoPersiste() {
    SpecialClosureDto dto = dtoAllDay();
    UUID clientId = UUID.randomUUID();
    Agendamento agendamento = agendamento(clientId, professionalId, StatusAgendamento.CONFIRMED);
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of(agendamento));
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId))
        .thenReturn(Optional.of(cliente(clientId, "Maria")));
    when(profissionalRepository.findByIdInAndTenantId(List.of(professionalId), tenantId))
        .thenReturn(List.of(profissional(professionalId, "Joana")));

    SpecialClosureImpactDto result = service.criar(tenantId, dto);

    assertThat(result.created).isFalse();
    assertThat(result.closureId).isNull();
    assertThat(result.impactedAppointments).hasSize(1);
    SpecialClosureImpactDto.ImpactedAppointment impactado = result.impactedAppointments.getFirst();
    assertThat(impactado.clientName).isEqualTo("Maria");
    assertThat(impactado.professionalName).isEqualTo("Joana");
    assertThat(impactado.startTime).isEqualTo("10:00");
    assertThat(impactado.status).isEqualTo("CONFIRMED");

    verify(closureRepository, never()).saveAndFlush(any());
    verifyNoInteractions(auditService);
  }

  @Test
  @DisplayName("confirmar sem notifyClients cria o fechamento mas nao cancela nem notifica")
  void confirmarSemNotificarNaoCancela() {
    SpecialClosureDto dto = dtoAllDay();
    Agendamento agendamento =
        agendamento(UUID.randomUUID(), professionalId, StatusAgendamento.CONFIRMED);
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of(agendamento));
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SpecialClosureImpactDto result = service.confirmar(tenantId, dto);

    assertThat(result.created).isTrue();
    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
    verifyNoInteractions(whatsAppNotificationService);
  }

  @Test
  @DisplayName("confirmar com notifyClients cancela o impactado e notifica o cliente")
  void confirmarComNotificarCancelaENotifica() {
    SpecialClosureDto dto = dtoAllDay();
    Agendamento agendamento =
        agendamento(UUID.randomUUID(), professionalId, StatusAgendamento.CONFIRMED);
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of(agendamento));
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.confirmar(tenantId, dto, true);

    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELLED);
    verify(whatsAppNotificationService).sendCancellation(tenantId, agendamento);
  }

  @Test
  @DisplayName("agendamento ja cancelado nao sofre transicao invalida, e a notificacao segue")
  void statusSemTransicaoValidaNaoECancelado() {
    SpecialClosureDto dto = dtoAllDay();
    Agendamento agendamento =
        agendamento(UUID.randomUUID(), professionalId, StatusAgendamento.CANCELLED);
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of(agendamento));
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.confirmar(tenantId, dto, true);

    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELLED);
    // o original notifica mesmo quando a transicao foi recusada — assimetria preservada
    verify(whatsAppNotificationService).sendCancellation(tenantId, agendamento);
  }

  @Test
  @DisplayName("fechamento parcial busca impactados pelo intervalo, em texto HH:mm")
  void fechamentoParcialUsaIntervaloEmTexto() {
    SpecialClosureDto dto = new SpecialClosureDto();
    dto.closureDate = DATA;
    dto.allDay = false;
    dto.startTime = INICIO;
    dto.endTime = FIM;
    when(agendamentoRepository.listImpactedByClosure(
            tenantId, DATA, false, "10:00", "11:00", null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.criar(tenantId, dto);

    ArgumentCaptor<TenantSpecialClosureDate> captor =
        ArgumentCaptor.forClass(TenantSpecialClosureDate.class);
    verify(closureRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getStartTime()).isEqualTo(INICIO);
    assertThat(captor.getValue().getEndTime()).isEqualTo(FIM);
  }

  @Test
  @DisplayName("all-day zera startTime/endTime mesmo se vierem preenchidos no request")
  void allDayZeraHorarios() {
    SpecialClosureDto dto = dtoAllDay();
    dto.startTime = INICIO;
    dto.endTime = FIM;
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, "10:00", "11:00", null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.criar(tenantId, dto);

    ArgumentCaptor<TenantSpecialClosureDate> captor =
        ArgumentCaptor.forClass(TenantSpecialClosureDate.class);
    verify(closureRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getStartTime()).isNull();
    assertThat(captor.getValue().getEndTime()).isNull();
  }

  // ---------- validacoes ----------

  @Test
  @DisplayName("tipo de fechamento fora da lista tem mensagem com os valores aceitos")
  void tipoInvalido() {
    SpecialClosureDto dto = dtoAllDay();
    dto.closureType = "FERIADAO";

    assertThatThrownBy(() -> service.criar(tenantId, dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Tipo de fechamento invalido. Valores aceitos: HOLIDAY, VACATION, RECESS,"
                + " INTERNAL_EVENT, MANUAL");
  }

  @Test
  @DisplayName("tipo nulo ou em branco vira MANUAL, e minusculo e normalizado para maiusculo")
  void tipoNuloViraManual() {
    SpecialClosureDto semTipo = dtoAllDay();
    semTipo.closureType = null;
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.criar(tenantId, semTipo);

    SpecialClosureDto minusculo = dtoAllDay();
    minusculo.closureType = "vacation";
    service.criar(tenantId, minusculo);

    ArgumentCaptor<TenantSpecialClosureDate> captor =
        ArgumentCaptor.forClass(TenantSpecialClosureDate.class);
    verify(closureRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues().get(0).getClosureType()).isEqualTo("MANUAL");
    assertThat(captor.getAllValues().get(1).getClosureType()).isEqualTo("VACATION");
  }

  @Test
  @DisplayName("assimetria do original: espaco em volta do tipo reprova na validacao")
  void tipoComEspacoReprova() {
    // validarTipo compara sem trim (so faz toUpperCase); normalizarTipo, que roda depois, faz trim.
    // Resultado: " vacation " nunca chega a ser normalizado — e recusado antes. Preservado.
    SpecialClosureDto dto = dtoAllDay();
    dto.closureType = " vacation ";

    assertThatThrownBy(() -> service.criar(tenantId, dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Tipo de fechamento invalido.");
  }

  @Test
  @DisplayName("data de fechamento e obrigatoria")
  void dataObrigatoria() {
    SpecialClosureDto dto = new SpecialClosureDto();

    assertThatThrownBy(() -> service.criar(tenantId, dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Data de fechamento obrigatoria");
  }

  @Test
  @DisplayName("fechamento parcial exige startTime e endTime, com start anterior a end")
  void parcialExigeIntervaloValido() {
    SpecialClosureDto semHorario = new SpecialClosureDto();
    semHorario.closureDate = DATA;
    semHorario.allDay = false;
    assertThatThrownBy(() -> service.criar(tenantId, semHorario))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Para fechamento parcial, startTime e endTime sao obrigatorios");

    SpecialClosureDto invertido = new SpecialClosureDto();
    invertido.closureDate = DATA;
    invertido.allDay = false;
    invertido.startTime = FIM;
    invertido.endTime = INICIO;
    assertThatThrownBy(() -> service.criar(tenantId, invertido))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("startTime deve ser anterior a endTime no fechamento parcial");
  }

  @Test
  @DisplayName("motivo e truncado em 160 caracteres e em branco vira null")
  void reasonNormalizado() {
    SpecialClosureDto longo = dtoAllDay();
    longo.reason = "x".repeat(200);
    SpecialClosureDto branco = dtoAllDay();
    branco.reason = "   ";
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.criar(tenantId, longo);
    service.criar(tenantId, branco);

    ArgumentCaptor<TenantSpecialClosureDate> captor =
        ArgumentCaptor.forClass(TenantSpecialClosureDate.class);
    verify(closureRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues().get(0).getReason()).hasSize(160);
    assertThat(captor.getAllValues().get(1).getReason()).isNull();
  }

  // ---------- LGPD ----------

  @Test
  @DisplayName("LGPD: a auditoria de criacao nunca leva o motivo em claro")
  @SuppressWarnings("unchecked")
  void auditoriaNaoVazaReason() {
    SpecialClosureDto dto = dtoAllDay();
    dto.reason = "Reforma da recepcao";
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.criar(tenantId, dto);

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    AuditEventCommand cmd = captor.getValue();
    assertThat(cmd.module).isEqualTo(AuditConstants.Module.SETTINGS);
    assertThat(cmd.action).isEqualTo(AuditConstants.Action.SPECIAL_CLOSURE_CREATED);
    Map<String, Object> after = (Map<String, Object>) cmd.after;
    assertThat(after).doesNotContainKey("reason");
    assertThat(after).containsEntry("reason_preenchido", true);
    assertThat(after.values()).doesNotContain("Reforma da recepcao");
  }

  @Test
  @DisplayName("auditoria de criacao aceita professional_id nulo (fechamento do salao inteiro)")
  @SuppressWarnings("unchecked")
  void auditoriaAceitaProfissionalNulo() {
    SpecialClosureDto dto = dtoAllDay();
    when(agendamentoRepository.listImpactedByClosure(tenantId, DATA, true, null, null, null))
        .thenReturn(List.of());
    when(closureRepository.saveAndFlush(any(TenantSpecialClosureDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.criar(tenantId, dto);

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    Map<String, Object> after = (Map<String, Object>) captor.getValue().after;
    assertThat(after).containsEntry("professional_id", null);
    assertThat(after).containsEntry("reason_preenchido", false);
  }

  // ---------- edicao / remocao ----------

  @Test
  @DisplayName("editar altera os campos e registra before/after sem reason")
  @SuppressWarnings("unchecked")
  void editarAtualizaERegistraDiff() {
    UUID id = UUID.randomUUID();
    TenantSpecialClosureDate entidade = entidade(id, "MANUAL", true);
    entidade.setReason("motivo antigo");
    when(closureRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(entidade));

    SpecialClosureDto dto = new SpecialClosureDto();
    dto.closureDate = DATA.plusDays(1);
    dto.closureType = "RECESS";
    dto.allDay = false;
    dto.startTime = INICIO;
    dto.endTime = FIM;
    dto.reason = "novo motivo";

    service.editar(tenantId, id, dto);

    assertThat(entidade.getClosureType()).isEqualTo("RECESS");
    assertThat(entidade.getClosureDate()).isEqualTo(DATA.plusDays(1));
    assertThat(entidade.getStartTime()).isEqualTo(INICIO);

    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    Map<String, Object> before = (Map<String, Object>) captor.getValue().before;
    Map<String, Object> after = (Map<String, Object>) captor.getValue().after;
    assertThat(before).containsEntry("closure_type", "MANUAL");
    assertThat(after).containsEntry("closure_type", "RECESS");
    assertThat(before).doesNotContainKey("reason");
    assertThat(after).doesNotContainKey("reason");
  }

  @Test
  @DisplayName("remover apaga e audita o estado anterior")
  void removerApagaEAudita() {
    UUID id = UUID.randomUUID();
    TenantSpecialClosureDate entidade = entidade(id, "HOLIDAY", true);
    when(closureRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(entidade));

    service.remover(tenantId, id);

    verify(closureRepository).delete(entidade);
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    assertThat(captor.getValue().action).isEqualTo(AuditConstants.Action.SPECIAL_CLOSURE_DELETED);
    assertThat(captor.getValue().entityId).isEqualTo(id.toString());
  }

  @Test
  @DisplayName("editar/remover de outro tenant devolve 'Fechamento nao encontrado'")
  void fechamentoDeOutroTenantNaoEEncontrado() {
    UUID id = UUID.randomUUID();
    when(closureRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remover(tenantId, id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Fechamento nao encontrado");
    assertThatThrownBy(() -> service.editar(tenantId, id, dtoAllDay()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Fechamento nao encontrado");
  }

  // ---------- listagem ----------

  @Test
  @DisplayName("listar resolve o nome do profissional em lote, uma consulta so")
  void listarResolveNomesEmLote() {
    TenantSpecialClosureDate comProfissional = entidade(UUID.randomUUID(), "VACATION", true);
    comProfissional.setProfessionalId(professionalId);
    TenantSpecialClosureDate doSalao = entidade(UUID.randomUUID(), "HOLIDAY", true);
    when(closureRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Sort.class)))
        .thenReturn(List.of(comProfissional, doSalao));
    when(profissionalRepository.findByIdInAndTenantId(List.of(professionalId), tenantId))
        .thenReturn(List.of(profissional(professionalId, "Joana")));

    List<SpecialClosureDto> result = service.listar(tenantId, null, null, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).professionalName).isEqualTo("Joana");
    assertThat(result.get(1).professionalName).isNull();
    verify(profissionalRepository).findByIdInAndTenantId(any(), eq(tenantId));
  }

  @Test
  @DisplayName("listarDatasIndisponiveis usa hoje e +3 meses como janela default")
  void listarDatasIndisponiveisUsaJanelaDefault() {
    LocalDate hoje = LocalDate.now();
    when(closureRepository.listDistinctDatesInRange(tenantId, hoje, hoje.plusMonths(3)))
        .thenReturn(List.of(DATA));

    assertThat(service.listarDatasIndisponiveis(tenantId, null, null)).containsExactly(DATA);
  }

  // ---------- helpers ----------

  private SpecialClosureDto dtoAllDay() {
    SpecialClosureDto dto = new SpecialClosureDto();
    dto.closureDate = DATA;
    dto.closureType = "HOLIDAY";
    dto.allDay = true;
    return dto;
  }

  private TenantSpecialClosureDate entidade(UUID id, String tipo, boolean allDay) {
    TenantSpecialClosureDate entidade = new TenantSpecialClosureDate();
    entidade.setId(id);
    entidade.setTenantId(tenantId);
    entidade.setClosureDate(DATA);
    entidade.setClosureType(tipo);
    entidade.setAllDay(allDay);
    return entidade;
  }

  private Agendamento agendamento(UUID clientId, UUID professionalId, StatusAgendamento status) {
    Agendamento agendamento = new Agendamento();
    agendamento.setId(UUID.randomUUID());
    agendamento.setTenantId(tenantId);
    agendamento.setClientId(clientId);
    agendamento.setProfessionalId(professionalId);
    agendamento.setDate(DATA);
    agendamento.setStartTime("10:00");
    agendamento.setEndTime("11:00");
    agendamento.setStatus(status);
    return agendamento;
  }

  private Cliente cliente(UUID id, String nome) {
    Cliente cliente = new Cliente();
    cliente.setId(id);
    cliente.setTenantId(tenantId);
    cliente.setName(nome);
    return cliente;
  }

  private Profissional profissional(UUID id, String nome) {
    Profissional profissional = new Profissional();
    profissional.setId(id);
    profissional.setTenantId(tenantId);
    profissional.setName(nome);
    return profissional;
  }
}
