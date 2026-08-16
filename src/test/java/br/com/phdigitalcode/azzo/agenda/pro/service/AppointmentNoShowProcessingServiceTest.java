package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppAppointmentNotificationService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Cobre {@code modules/scheduling/application/AppointmentNoShowProcessingService.java}.
 *
 * <p>O {@link PlatformTransactionManager} e um mock: o {@code TransactionTemplate} real chama
 * {@code getTransaction}/{@code commit} nele e executa o callback de verdade, entao a logica sob
 * teste roda exatamente como em producao, sem precisar de contexto Spring.
 */
class AppointmentNoShowProcessingServiceTest {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private AgendamentoRepository agendamentoRepository;
  private WhatsAppAppointmentNotificationService whatsAppNotificationService;
  private AppointmentNoShowProcessingService service;

  @BeforeEach
  void setUp() {
    agendamentoRepository = mock(AgendamentoRepository.class);
    whatsAppNotificationService = mock(WhatsAppAppointmentNotificationService.class);
    service =
        new AppointmentNoShowProcessingService(
            agendamentoRepository,
            whatsAppNotificationService,
            mock(PlatformTransactionManager.class));
  }

  /** Agendamento que terminou ha mais de 6 horas (o suficiente para estourar a janela). */
  private Agendamento agendamentoVencido() {
    ZonedDateTime ontem = ZonedDateTime.now(ZONE_BR).minusDays(1);
    Agendamento agendamento = new Agendamento();
    agendamento.setId(UUID.randomUUID());
    agendamento.setTenantId(UUID.randomUUID());
    agendamento.setDate(ontem.toLocalDate());
    agendamento.setStartTime("09:00");
    agendamento.setEndTime("10:00");
    agendamento.setStatus(StatusAgendamento.CONFIRMED);
    return agendamento;
  }

  @Test
  void marcaComoNoShowEAvisaPorWhatsApp() {
    Agendamento agendamento = agendamentoVencido();
    when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

    assertThat(service.markAppointmentAsNoShowIfNeeded(agendamento.getId())).isEqualTo(1);

    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.NO_SHOW);
    verify(agendamentoRepository).save(agendamento);
    verify(whatsAppNotificationService).sendNoShow(agendamento);
  }

  @Test
  void naoMarcaAgendamentoDentroDaJanelaDeSeisHoras() {
    Agendamento agendamento = agendamentoVencido();
    ZonedDateTime agora = ZonedDateTime.now(ZONE_BR);
    // Terminou ha ~1 hora: ainda dentro da janela de tolerancia.
    agendamento.setDate(agora.toLocalDate());
    agendamento.setEndTime(agora.minusHours(1).toLocalTime().withSecond(0).withNano(0).toString());
    when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

    assertThat(service.markAppointmentAsNoShowIfNeeded(agendamento.getId())).isZero();

    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
    verify(agendamentoRepository, never()).save(any());
    verifyNoInteractions(whatsAppNotificationService);
  }

  @Test
  void ignoraAgendamentoJaConcluidoOuCancelado() {
    Agendamento agendamento = agendamentoVencido();
    agendamento.setStatus(StatusAgendamento.COMPLETED);
    when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

    assertThat(service.markAppointmentAsNoShowIfNeeded(agendamento.getId())).isZero();

    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.COMPLETED);
    verify(agendamentoRepository, never()).save(any());
  }

  @Test
  void horarioIlegivelNaoDerrubaOProcessamento() {
    Agendamento agendamento = agendamentoVencido();
    agendamento.setEndTime("nao-e-hora");
    when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

    assertThat(service.markAppointmentAsNoShowIfNeeded(agendamento.getId())).isZero();

    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
    verify(agendamentoRepository, never()).save(any());
  }

  @Test
  void idNuloNaoConsultaORepositorio() {
    assertThat(service.markAppointmentAsNoShowIfNeeded(null)).isZero();
    verifyNoInteractions(agendamentoRepository);
  }

  @Test
  void agendamentoInexistenteNaoConta() {
    UUID id = UUID.randomUUID();
    when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());

    assertThat(service.markAppointmentAsNoShowIfNeeded(id)).isZero();
  }

  @Test
  void falhaDoWhatsAppNaoImpedeAMarcacao() {
    Agendamento agendamento = agendamentoVencido();
    when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
    org.mockito.Mockito.doThrow(new IllegalStateException("provedor fora do ar"))
        .when(whatsAppNotificationService)
        .sendNoShow(agendamento);

    assertThat(service.markAppointmentAsNoShowIfNeeded(agendamento.getId())).isEqualTo(1);
    assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.NO_SHOW);
  }

  @Test
  void varreduraUsaDataDeSaoPauloEStatusAbertos() {
    Agendamento agendamento = agendamentoVencido();
    when(agendamentoRepository.listIdsByStatusUpToDate(anyCollection(), any(LocalDate.class)))
        .thenReturn(List.of(agendamento.getId()));
    when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

    assertThat(service.markOpenAppointmentsAsNoShow()).isEqualTo(1);

    verify(agendamentoRepository)
        .listIdsByStatusUpToDate(
            eq(List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED)),
            eq(LocalDate.now(ZONE_BR)));
  }

  @Test
  void varreduraSomaApenasOsQueRealmenteMudaram() {
    Agendamento vencido = agendamentoVencido();
    Agendamento recente = agendamentoVencido();
    recente.setDate(LocalDate.now(ZONE_BR));
    recente.setEndTime(LocalTime.now(ZONE_BR).withSecond(0).withNano(0).toString());

    when(agendamentoRepository.listIdsByStatusUpToDate(anyCollection(), any(LocalDate.class)))
        .thenReturn(List.of(vencido.getId(), recente.getId()));
    when(agendamentoRepository.findById(vencido.getId())).thenReturn(Optional.of(vencido));
    when(agendamentoRepository.findById(recente.getId())).thenReturn(Optional.of(recente));

    assertThat(service.markOpenAppointmentsAsNoShow()).isEqualTo(1);
    assertThat(vencido.getStatus()).isEqualTo(StatusAgendamento.NO_SHOW);
    assertThat(recente.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
  }

  @Test
  void varreduraSemCandidatosNaoTocaEmNada() {
    when(agendamentoRepository.listIdsByStatusUpToDate(anyCollection(), any(LocalDate.class)))
        .thenReturn(List.of());

    assertThat(service.markOpenAppointmentsAsNoShow()).isZero();

    verify(agendamentoRepository, never()).findById(any());
    verifyNoInteractions(whatsAppNotificationService);
  }
}
