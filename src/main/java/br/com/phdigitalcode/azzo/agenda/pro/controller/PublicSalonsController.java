package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PublicBookingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoPublicBooking;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoSalonProfile;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/publicbooking/api/PublicSalonsResource.java}
 * ({@code @Path("/api/v1/public/salons")}). Rota publica ja coberta pela allowlist
 * {@code /api/v1/public/**} em {@code SecurityConfig} — sem {@code @PreAuthorize} nenhum, igual ao
 * original (nenhum metodo do original tem {@code @RolesAllowed}).
 */
@RestController
@RequestMapping("/api/v1/public/salons")
public class PublicSalonsController {

  private final ServicoSalonProfile servicoSalonProfile;
  private final ServicoPublicBooking servicoPublicBooking;

  public PublicSalonsController(ServicoSalonProfile servicoSalonProfile, ServicoPublicBooking servicoPublicBooking) {
    this.servicoSalonProfile = servicoSalonProfile;
    this.servicoPublicBooking = servicoPublicBooking;
  }

  @GetMapping("/{slug}")
  public SalonDtos.PublicSalonProfile obterSalao(@PathVariable("slug") String slug) {
    return servicoSalonProfile.obterPublico(slug);
  }

  @GetMapping("/{slug}/services")
  public List<ServicoResponse> listarServicosAtivos(@PathVariable("slug") String slug) {
    return servicoPublicBooking.listarServicosAtivos(slug);
  }

  @GetMapping("/{slug}/professionals")
  public List<ProfissionalResponse> listarProfissionaisAtivos(
      @PathVariable("slug") String slug,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "serviceIds", required = false) String serviceIds) {
    return servicoPublicBooking.listarProfissionaisAtivos(slug, serviceId, serviceIds);
  }

  @GetMapping("/{slug}/availability")
  public PublicBookingDtos.AvailabilityResponse disponibilidade(
      @PathVariable("slug") String slug,
      @RequestParam(name = "date", required = false) String date,
      @RequestParam(name = "serviceId", required = false) String serviceId,
      @RequestParam(name = "serviceIds", required = false) String serviceIds,
      @RequestParam(name = "professionalId", required = false) String professionalId) {
    return servicoPublicBooking.obterDisponibilidade(slug, date, serviceId, serviceIds, professionalId);
  }

  @PostMapping("/{slug}/appointments")
  public ResponseEntity<PublicBookingDtos.PublicAppointmentResponse> criarAgendamento(
      @PathVariable("slug") String slug, @Valid @RequestBody PublicBookingDtos.PublicAppointmentRequest request) {
    PublicBookingDtos.PublicAppointmentResponse resp = servicoPublicBooking.criarAgendamentoPublico(slug, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(resp);
  }

  /**
   * Lista datas indisponiveis do salao para o calendario publico de booking. Retorna apenas
   * {@code LocalDate} — sem motivo, sem professionalId, sem dados pessoais.
   */
  @GetMapping("/{slug}/closures")
  public ResponseEntity<List<LocalDate>> listarDatasIndisponiveis(
      @PathVariable("slug") String slug,
      @RequestParam(name = "from", required = false) LocalDate from,
      @RequestParam(name = "to", required = false) LocalDate to) {
    List<LocalDate> datas = servicoPublicBooking.listarDatasIndisponiveis(slug, from, to);
    return ResponseEntity.ok(datas);
  }

  @PostMapping("/{slug}/booking-funnel/events")
  public ResponseEntity<PublicBookingDtos.BookingFunnelEventResponse> registrarEventoFunil(
      @PathVariable("slug") String slug,
      @Valid @RequestBody PublicBookingDtos.BookingFunnelEventRequest request) {
    PublicBookingDtos.BookingFunnelEventResponse resp = servicoPublicBooking.registrarEventoFunil(slug, request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
  }
}
