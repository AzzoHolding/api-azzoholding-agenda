package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SpecialClosureImpactDto;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.SpecialClosureService;

/**
 * Cobre o achado de 29/09/2026: {@code POST /confirm} chamava sempre o overload de dois
 * argumentos do service, que fixa {@code notifyClients=false} — a tela nao tinha como pedir
 * "cancelar e avisar o cliente". Ver {@link SpecialClosureController#confirmar}.
 */
class SpecialClosureControllerTest {

  private final UUID tenantId = UUID.randomUUID();

  private SpecialClosureService specialClosureService;
  private ContextoTenant contextoTenant;
  private SpecialClosureController controller;

  @BeforeEach
  void setUp() {
    specialClosureService = mock(SpecialClosureService.class);
    contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    controller = new SpecialClosureController(specialClosureService, contextoTenant);
  }

  @Test
  void confirmarRepassaNotifyClientsVerdadeiroParaOService() {
    SpecialClosureDto dto = new SpecialClosureDto();
    dto.closureDate = LocalDate.of(2026, 12, 25);
    SpecialClosureImpactDto esperado = new SpecialClosureImpactDto();
    esperado.created = true;
    when(specialClosureService.confirmar(tenantId, dto, true)).thenReturn(esperado);

    ResponseEntity<SpecialClosureImpactDto> resposta = controller.confirmar(dto, true);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody()).isSameAs(esperado);
    verify(specialClosureService).confirmar(tenantId, dto, true);
  }

  @Test
  void confirmarSemParametroUsaFalsoPorPadrao() {
    SpecialClosureDto dto = new SpecialClosureDto();
    dto.closureDate = LocalDate.of(2026, 12, 25);
    SpecialClosureImpactDto esperado = new SpecialClosureImpactDto();
    esperado.created = true;
    when(specialClosureService.confirmar(tenantId, dto, false)).thenReturn(esperado);

    controller.confirmar(dto, false);

    verify(specialClosureService).confirmar(tenantId, dto, false);
  }
}
