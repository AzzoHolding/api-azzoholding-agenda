package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicBookingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPublicBooking;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoSalonProfile;

/** Cobre {@code modules/publicbooking/api/PublicSalonsResource.java} (JAX-RS -> Spring MVC). */
class PublicSalonsControllerTest {

  private ServicoSalonProfile servicoSalonProfile;
  private ServicoPublicBooking servicoPublicBooking;
  private PublicSalonsController controller;

  @BeforeEach
  void setUp() {
    servicoSalonProfile = mock(ServicoSalonProfile.class);
    servicoPublicBooking = mock(ServicoPublicBooking.class);
    controller = new PublicSalonsController(servicoSalonProfile, servicoPublicBooking);
  }

  @Test
  void obterSalaoDelegaAoServicoDePerfil() {
    SalonDtos.PublicSalonProfile esperado = new SalonDtos.PublicSalonProfile();
    when(servicoSalonProfile.obterPublico("salao-teste")).thenReturn(esperado);

    assertThat(controller.obterSalao("salao-teste")).isSameAs(esperado);
  }

  @Test
  void listarServicosAtivosDelega() {
    List<ServicoResponse> esperado = List.of(new ServicoResponse());
    when(servicoPublicBooking.listarServicosAtivos("salao-teste")).thenReturn(esperado);

    assertThat(controller.listarServicosAtivos("salao-teste")).isSameAs(esperado);
  }

  @Test
  void listarProfissionaisAtivosDelegaComFiltrosDeServico() {
    List<ProfissionalResponse> esperado = List.of(new ProfissionalResponse());
    when(servicoPublicBooking.listarProfissionaisAtivos("salao-teste", "svc-1", "svc-1,svc-2"))
        .thenReturn(esperado);

    assertThat(controller.listarProfissionaisAtivos("salao-teste", "svc-1", "svc-1,svc-2")).isSameAs(esperado);
  }

  @Test
  void disponibilidadeDelega() {
    PublicBookingDtos.AvailabilityResponse esperado = new PublicBookingDtos.AvailabilityResponse();
    when(servicoPublicBooking.obterDisponibilidade("salao-teste", "2026-01-01", "svc-1", null, "prof-1"))
        .thenReturn(esperado);

    assertThat(controller.disponibilidade("salao-teste", "2026-01-01", "svc-1", null, "prof-1")).isSameAs(esperado);
  }

  @Test
  void criarAgendamentoRetorna201ComCorpoDoService() {
    PublicBookingDtos.PublicAppointmentRequest request = new PublicBookingDtos.PublicAppointmentRequest();
    PublicBookingDtos.PublicAppointmentResponse esperado = new PublicBookingDtos.PublicAppointmentResponse();
    when(servicoPublicBooking.criarAgendamentoPublico("salao-teste", request)).thenReturn(esperado);

    ResponseEntity<PublicBookingDtos.PublicAppointmentResponse> response =
        controller.criarAgendamento("salao-teste", request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isSameAs(esperado);
  }

  @Test
  void listarDatasIndisponiveisRetorna200ComListaDeDatas() {
    LocalDate from = LocalDate.now();
    LocalDate to = from.plusDays(30);
    List<LocalDate> esperado = List.of(from.plusDays(3));
    when(servicoPublicBooking.listarDatasIndisponiveis("salao-teste", from, to)).thenReturn(esperado);

    ResponseEntity<List<LocalDate>> response = controller.listarDatasIndisponiveis("salao-teste", from, to);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(esperado);
  }

  @Test
  void registrarEventoFunilRetorna202ComCorpoDoService() {
    PublicBookingDtos.BookingFunnelEventRequest request = new PublicBookingDtos.BookingFunnelEventRequest();
    PublicBookingDtos.BookingFunnelEventResponse esperado = new PublicBookingDtos.BookingFunnelEventResponse();
    when(servicoPublicBooking.registrarEventoFunil("salao-teste", request)).thenReturn(esperado);

    ResponseEntity<PublicBookingDtos.BookingFunnelEventResponse> response =
        controller.registrarEventoFunil("salao-teste", request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isSameAs(esperado);
  }
}
