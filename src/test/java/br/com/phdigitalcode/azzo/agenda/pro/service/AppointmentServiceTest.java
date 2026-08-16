package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.ManualTimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.TimeSlotResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;

/**
 * Cobre o motor de slots de {@link AppointmentService} — o algoritmo portado de
 * {@code modules/scheduling/application/AppointmentService.java}.
 *
 * <p>Todas as datas usadas sao futuras e o dia da semana da jornada e derivado da propria data, para
 * que o corte de "slots que ja passaram" (que so vale para hoje) nao torne os testes dependentes do
 * horario de execucao. Ha um teste dedicado para esse corte.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
  /** Data futura fixa, usada em todos os testes que nao exercitam a regra de "hoje". */
  private static final LocalDate DATA = LocalDate.of(2027, 3, 10);

  private final UUID tenantId = UUID.randomUUID();
  private final UUID professionalId = UUID.randomUUID();

  @Mock private AgendamentoRepository agendamentoRepository;
  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  @Mock private ClienteRepository clienteRepository;
  @Mock private ServicoRepository servicoRepository;
  @Mock private TenantOperationalSettingsService tenantOperationalSettingsService;
  @Mock private SpecialClosureService specialClosureService;

  private AppointmentService service;

  @BeforeEach
  void setUp() {
    service =
        new AppointmentService(
            agendamentoRepository,
            profissionalRepository,
            profissionalWorkingHourRepository,
            clienteRepository,
            servicoRepository,
            tenantOperationalSettingsService,
            specialClosureService);
  }

  // ---------- validacao de entrada ----------

  @Test
  @DisplayName("tenantId/professionalId/date nulos e duracao/buffer invalidos sao rejeitados")
  void validaEntrada() {
    assertThatThrownBy(() -> service.findAvailableSlots(null, professionalId, DATA, 30, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tenantId obrigatorio");
    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, null, DATA, 30, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("professionalId obrigatorio");
    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, professionalId, null, 30, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("date obrigatoria");
    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, professionalId, DATA, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("serviceDurationMinutes invalido");
    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, professionalId, DATA, 30, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bufferMinutes invalido");

    verify(specialClosureService, never()).isClosedAt(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("profissional de outro tenant nao e encontrado")
  void profissionalDeOutroTenant() {
    janelaSalao("09:00", "19:00");
    when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Profissional nao encontrado para o tenant informado");
  }

  @Test
  @DisplayName("profissional sem jornada na data cadastrada lanca IllegalStateException")
  void profissionalSemJornada() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Profissional sem horario de trabalho para a data informada");
  }

  // ---------- portoes que zeram a agenda ----------

  @Test
  @DisplayName("fechamento especial no dia zera os slots sem nem consultar profissional")
  void fechamentoEspecialZeraAgenda() {
    when(specialClosureService.isClosedAt(tenantId, professionalId, DATA, null, null))
        .thenReturn(true);

    assertThat(service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0)).isEmpty();
    verify(profissionalRepository, never()).findByIdAndTenantId(any(), any());
    verify(tenantOperationalSettingsService, never()).getBusinessHourForDate(any(), any());
  }

  @Test
  @DisplayName("salao fechado no dia da semana (enabled=false) zera os slots")
  void salaoFechadoZeraAgenda() {
    SalonDtos.BusinessHour fechado =
        businessHour("Domingo", false, "09:00", "13:00");
    when(tenantOperationalSettingsService.getBusinessHourForDate(tenantId, DATA))
        .thenReturn(fechado);

    assertThat(service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0)).isEmpty();
    verify(profissionalRepository, never()).findByIdAndTenantId(any(), any());
  }

  @Test
  @DisplayName("janela do salao invertida (abre depois de fechar) e tratada como fechado")
  void janelaSalaoInvertida() {
    janelaSalao("19:00", "09:00");

    assertThat(service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0)).isEmpty();
  }

  @Test
  @DisplayName("jornada fora da janela do salao resulta em zero slots, sem erro")
  void jornadaForaDaJanelaDoSalao() {
    janelaSalao("09:00", "12:00");
    profissionalExiste();
    jornada("14:00", "18:00");

    assertThat(service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0)).isEmpty();
  }

  // ---------- geracao de slots ----------

  @Test
  @DisplayName("agenda livre gera slots de 5 em 5 minutos dentro da jornada")
  void agendaLivreGeraSlots() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "10:00");
    semAgendamentos();

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0);

    // Janela livre 09:00-10:00, servico de 30min, passo de 5min: 09:00..09:30 seriam 7 inicios,
    // mas os que deixam uma sobra menor que 15min em qualquer ponta sao descartados
    // (geraLacunaRuim). Restam 09:00, 09:15 e 09:30.
    assertThat(slots).extracting(slot -> slot.startTime)
        .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(9, 15));
    assertThat(slots).extracting(slot -> slot.endTime)
        .containsExactly(LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(9, 45));
  }

  @Test
  @DisplayName("slot que preenche a lacuna inteira recebe score 300 e vem primeiro")
  void slotExatoRecebeScoreMaximo() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "09:30");
    semAgendamentos();

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0);

    assertThat(slots).hasSize(1);
    assertThat(slots.get(0).startTime).isEqualTo(LocalTime.of(9, 0));
    assertThat(slots.get(0).optimizationScore).isEqualTo(300);
  }

  @Test
  @DisplayName("ordenacao e por score desc e, em empate, por horario asc")
  void ordenacaoPorScoreDepoisHorario() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "10:00");
    semAgendamentos();

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0);

    // 09:00 e 09:30 encostam numa das bordas da lacuna (score 180); 09:15 nao encosta em nenhuma
    // (score 120). Entre os dois de 180, o mais cedo vem antes.
    assertThat(slots.get(0).optimizationScore).isEqualTo(180);
    assertThat(slots.get(1).optimizationScore).isEqualTo(180);
    assertThat(slots.get(0).startTime).isBefore(slots.get(1).startTime);
    assertThat(slots.get(2).optimizationScore).isEqualTo(120);
  }

  @Test
  @DisplayName("agendamento existente parte a jornada em duas lacunas")
  void agendamentoOcupaFaixa() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "11:00");
    agendamentos(agendamento("09:30", "10:00", StatusAgendamento.CONFIRMED));

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0);

    assertThat(slots).isNotEmpty();
    // Nenhum slot pode invadir 09:30-10:00.
    assertThat(slots)
        .allSatisfy(
            slot ->
                assertThat(
                        slot.startTime.isBefore(LocalTime.of(10, 0))
                            && slot.endTime.isAfter(LocalTime.of(9, 30)))
                    .isFalse());
    assertThat(slots).extracting(slot -> slot.startTime).contains(LocalTime.of(9, 0));
    assertThat(slots).extracting(slot -> slot.startTime).contains(LocalTime.of(10, 0));
  }

  @Test
  @DisplayName("buffer amplia a faixa ocupada para os dois lados")
  void bufferAmpliaFaixaOcupada() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "12:00");
    agendamentos(agendamento("10:00", "10:30", StatusAgendamento.PENDING));

    List<TimeSlotResponse> comBuffer =
        service.findAvailableSlots(tenantId, professionalId, DATA, 30, 15);

    // Com buffer de 15min o bloqueio efetivo vira 09:45-10:45.
    assertThat(comBuffer)
        .allSatisfy(
            slot ->
                assertThat(
                        slot.startTime.isBefore(LocalTime.of(10, 45))
                            && slot.endTime.isAfter(LocalTime.of(9, 45)))
                    .isFalse());
    assertThat(comBuffer).extracting(slot -> slot.startTime).doesNotContain(LocalTime.of(9, 30));
  }

  @Test
  @DisplayName("CANCELLED, NO_SHOW e COMPLETED sao excluidos da consulta de ocupacao")
  void statusQueNaoOcupamHorario() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "10:00");
    semAgendamentos();

    service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<StatusAgendamento>> captor =
        ArgumentCaptor.forClass(Collection.class);
    verify(agendamentoRepository)
        .listActiveByProfessionalAndDate(eq(tenantId), eq(professionalId), eq(DATA), captor.capture());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            StatusAgendamento.CANCELLED, StatusAgendamento.NO_SHOW, StatusAgendamento.COMPLETED);
  }

  @Test
  @DisplayName("agendamento com horario ilegivel ou invertido e ignorado, nao derruba o calculo")
  void agendamentoComHorarioInvalidoEIgnorado() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "09:30");
    agendamentos(
        agendamento("nao-e-hora", "10:00", StatusAgendamento.CONFIRMED),
        agendamento("11:00", "10:00", StatusAgendamento.CONFIRMED));

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0);

    assertThat(slots).hasSize(1);
    assertThat(slots.get(0).optimizationScore).isEqualTo(300);
  }

  @Test
  @DisplayName("jornadas contiguas do mesmo dia sao fundidas numa faixa so")
  void jornadasContiguasSaoFundidas() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(
            List.of(
                workingHour(DATA.getDayOfWeek().getValue(), "09:00", "09:20", true),
                workingHour(DATA.getDayOfWeek().getValue(), "09:20", "09:40", true)));
    semAgendamentos();

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, DATA, 40, 0);

    // Nenhuma das duas faixas isoladas comporta 40min; fundidas (09:00-09:40) comportam exatamente.
    assertThat(slots).hasSize(1);
    assertThat(slots.get(0).startTime).isEqualTo(LocalTime.of(9, 0));
    assertThat(slots.get(0).endTime).isEqualTo(LocalTime.of(9, 40));
  }

  @Test
  @DisplayName("jornada marcada como nao-trabalhada ou de outro dia da semana e descartada")
  void jornadaDeOutroDiaOuInativaEDescartada() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    int hoje = DATA.getDayOfWeek().getValue();
    int outroDia = hoje == 7 ? 1 : hoje + 1;
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(
            List.of(
                workingHour(hoje, "09:00", "12:00", false),
                workingHour(outroDia, "09:00", "12:00", true)));

    assertThatThrownBy(() -> service.findAvailableSlots(tenantId, professionalId, DATA, 30, 0))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("domingo cadastrado na convencao JavaScript (0) casa com o domingo ISO (7)")
  void domingoNaConvencaoJavaScript() {
    LocalDate domingo = LocalDate.of(2027, 3, 14);
    assertThat(domingo.getDayOfWeek().getValue()).isEqualTo(7);

    when(tenantOperationalSettingsService.getBusinessHourForDate(tenantId, domingo))
        .thenReturn(
            businessHour("Domingo", true, "09:00", "19:00"));
    profissionalExiste();
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(List.of(workingHour(0, "09:00", "09:30", true)));
    when(agendamentoRepository.listActiveByProfessionalAndDate(
            eq(tenantId), eq(professionalId), eq(domingo), any()))
        .thenReturn(List.of());

    List<TimeSlotResponse> slots =
        service.findAvailableSlots(tenantId, professionalId, domingo, 30, 0);

    assertThat(slots).hasSize(1);
  }

  @Test
  @DisplayName("no dia corrente, slots que ja comecaram sao removidos")
  void slotsPassadosDeHojeSaoRemovidos() {
    LocalDate hoje = LocalDate.now(ZONE_BR);
    LocalTime agora = LocalTime.now(ZONE_BR);

    when(tenantOperationalSettingsService.getBusinessHourForDate(tenantId, hoje))
        .thenReturn(
            businessHour("Hoje", true, "00:00", "23:59"));
    profissionalExiste();
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(List.of(workingHour(hoje.getDayOfWeek().getValue(), "00:00", "23:59", true)));
    when(agendamentoRepository.listActiveByProfessionalAndDate(
            eq(tenantId), eq(professionalId), eq(hoje), any()))
        .thenReturn(List.of());

    List<TimeSlotResponse> slots = service.findAvailableSlots(tenantId, professionalId, hoje, 30, 0);

    assertThat(slots).allSatisfy(slot -> assertThat(slot.startTime).isAfter(agora.minusMinutes(1)));
  }

  // ---------- slots manuais ----------

  @Test
  @DisplayName("sem includeConflictSlots, os slots manuais sao exatamente os disponiveis")
  void slotsManuaisSemConflito() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "09:30");
    semAgendamentos();

    List<ManualTimeSlotResponse> slots =
        service.findManualSlots(tenantId, professionalId, DATA, 30, 0, false);

    assertThat(slots).hasSize(1);
    assertThat(slots.get(0).slotType).isEqualTo("AVAILABLE");
    assertThat(slots.get(0).conflicting).isFalse();
    assertThat(slots.get(0).badge).isNull();
    assertThat(slots.get(0).conflicts).isEmpty();
  }

  @Test
  @DisplayName("com includeConflictSlots, o horario ocupado vira slot CONFLICT com o resumo do agendamento")
  void slotsManuaisComConflito() {
    UUID clientId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();

    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "09:30");

    Agendamento ocupado = agendamento("09:00", "09:30", StatusAgendamento.CONFIRMED);
    ocupado.setClientId(clientId);
    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setName("Corte Masculino");
    AgendamentoItem item = new AgendamentoItem();
    item.setId(UUID.randomUUID());
    item.setServiceId(serviceId);
    item.setService(servico);
    ocupado.setItems(new ArrayList<>(List.of(item)));
    agendamentos(ocupado);

    Cliente cliente = new Cliente();
    cliente.setId(clientId);
    cliente.setName("Maria");
    when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));

    List<ManualTimeSlotResponse> slots =
        service.findManualSlots(tenantId, professionalId, DATA, 30, 0, true);

    assertThat(slots).hasSize(1);
    ManualTimeSlotResponse slot = slots.get(0);
    assertThat(slot.slotType).isEqualTo("CONFLICT");
    assertThat(slot.conflicting).isTrue();
    assertThat(slot.badge).isEqualTo("Conflito");
    assertThat(slot.optimizationScore).isZero();
    assertThat(slot.conflicts).hasSize(1);
    assertThat(slot.conflicts.get(0).clientName).isEqualTo("Maria");
    assertThat(slot.conflicts.get(0).serviceName).isEqualTo("Corte Masculino");
    assertThat(slot.conflicts.get(0).serviceId).isEqualTo(serviceId.toString());
    assertThat(slot.conflicts.get(0).status).isEqualTo("CONFIRMED");
    assertThat(slot.conflicts.get(0).startTime).isEqualTo("09:00");
    // O servico veio da associacao ja carregada do item — sem ida extra ao repositorio.
    verify(servicoRepository, never()).findByIdAndTenantId(any(), any());
  }

  @Test
  @DisplayName("slot disponivel tem precedencia sobre slot de conflito na mesma chave")
  void slotDisponivelVencePorChave() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "10:00");
    semAgendamentos();

    List<ManualTimeSlotResponse> slots =
        service.findManualSlots(tenantId, professionalId, DATA, 30, 0, true);

    assertThat(slots).isNotEmpty();
    assertThat(slots).allSatisfy(slot -> assertThat(slot.conflicting).isFalse());
  }

  @Test
  @DisplayName("slots manuais vem com os disponiveis antes dos conflitantes, cada bloco por horario")
  void ordenacaoDosSlotsManuais() {
    janelaSalao("09:00", "19:00");
    profissionalExiste();
    jornada("09:00", "10:00");
    agendamentos(agendamento("09:00", "09:30", StatusAgendamento.CONFIRMED));
    when(clienteRepository.findByIdAndTenantId(any(), eq(tenantId))).thenReturn(Optional.empty());

    List<ManualTimeSlotResponse> slots =
        service.findManualSlots(tenantId, professionalId, DATA, 30, 0, true);

    int primeiroConflitante = -1;
    for (int i = 0; i < slots.size(); i++) {
      if (Boolean.TRUE.equals(slots.get(i).conflicting)) {
        primeiroConflitante = i;
        break;
      }
    }
    assertThat(primeiroConflitante).isPositive();
    for (int i = 0; i < primeiroConflitante; i++) {
      assertThat(slots.get(i).conflicting).isFalse();
    }
    for (int i = primeiroConflitante; i < slots.size(); i++) {
      assertThat(slots.get(i).conflicting).isTrue();
    }
  }

  // ---------- helpers ----------

  /**
   * {@code SalonDtos.BusinessHour} nao tem construtor (campos publicos, igual ao original) — este
   * helper substitui o construtor que o placeholder {@code integration/TenantOperationalSettings
   * Service.BusinessHour} oferecia antes da migracao do modulo {@code settings}.
   */
  private static SalonDtos.BusinessHour businessHour(
      String day, boolean enabled, String open, String close) {
    SalonDtos.BusinessHour hour = new SalonDtos.BusinessHour();
    hour.day = day;
    hour.enabled = enabled;
    hour.open = open;
    hour.close = close;
    return hour;
  }

  private void janelaSalao(String abre, String fecha) {
    lenient()
        .when(tenantOperationalSettingsService.getBusinessHourForDate(eq(tenantId), any()))
        .thenReturn(
            businessHour("Dia", true, abre, fecha));
  }

  private void profissionalExiste() {
    Profissional profissional = new Profissional();
    profissional.setId(professionalId);
    profissional.setTenantId(tenantId);
    lenient()
        .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
        .thenReturn(Optional.of(profissional));
  }

  private void jornada(String inicio, String fim) {
    lenient()
        .when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(List.of(workingHour(DATA.getDayOfWeek().getValue(), inicio, fim, true)));
  }

  private ProfissionalWorkingHour workingHour(
      int dayOfWeek, String inicio, String fim, boolean trabalhando) {
    ProfissionalWorkingHour hour = new ProfissionalWorkingHour();
    hour.setId(UUID.randomUUID());
    hour.setTenantId(tenantId);
    hour.setProfessionalId(professionalId);
    hour.setDayOfWeek(dayOfWeek);
    hour.setStartTime(LocalTime.parse(inicio));
    hour.setEndTime(LocalTime.parse(fim));
    hour.setWorking(trabalhando);
    return hour;
  }

  private void semAgendamentos() {
    lenient()
        .when(
            agendamentoRepository.listActiveByProfessionalAndDate(
                eq(tenantId), eq(professionalId), any(), any()))
        .thenReturn(List.of());
  }

  private void agendamentos(Agendamento... itens) {
    lenient()
        .when(
            agendamentoRepository.listActiveByProfessionalAndDate(
                eq(tenantId), eq(professionalId), any(), any()))
        .thenReturn(List.of(itens));
  }

  private Agendamento agendamento(String inicio, String fim, StatusAgendamento status) {
    Agendamento agendamento = new Agendamento();
    agendamento.setId(UUID.randomUUID());
    agendamento.setTenantId(tenantId);
    agendamento.setProfessionalId(professionalId);
    agendamento.setClientId(UUID.randomUUID());
    agendamento.setDate(DATA);
    agendamento.setStartTime(inicio);
    agendamento.setEndTime(fim);
    agendamento.setStatus(status);
    return agendamento;
  }
}
