package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.LgpdRequestDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoAnonimizacaoTitular;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoLgpdTitular;

/** Espelha o contrato de {@code modules/lgpd/api/LgpdTitularResource.java}. */
class LgpdTitularControllerTest {

  private ServicoLgpdTitular servicoLgpdTitular;
  private ServicoAnonimizacaoTitular servicoAnonimizacao;
  private LgpdTitularController controller;

  @BeforeEach
  void setUp() {
    servicoLgpdTitular = mock(ServicoLgpdTitular.class);
    servicoAnonimizacao = mock(ServicoAnonimizacaoTitular.class);
    controller = new LgpdTitularController(servicoLgpdTitular, servicoAnonimizacao);
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(LgpdTitularController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/lgpd/requests");
  }

  @Test
  void classeExigeOwner() {
    PreAuthorize preAuthorize = LgpdTitularController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains("'OWNER'");
  }

  @Test
  void criarDelegaAoService() {
    LgpdRequestDtos.CreateRequest request = new LgpdRequestDtos.CreateRequest();
    LgpdRequestDtos.ItemResponse expected = new LgpdRequestDtos.ItemResponse();
    when(servicoLgpdTitular.criar(request)).thenReturn(expected);

    assertThat(controller.criar(request)).isSameAs(expected);
  }

  @Test
  void listarDelegaAoServiceComOsFiltros() {
    List<LgpdRequestDtos.ItemResponse> expected = List.of(new LgpdRequestDtos.ItemResponse());
    when(servicoLgpdTitular.listar("ABERTO", "ACESSO", 10)).thenReturn(expected);

    assertThat(controller.listar("ABERTO", "ACESSO", 10)).isSameAs(expected);
  }

  @Test
  void summaryDelegaAoService() {
    LgpdRequestDtos.SummaryResponse expected = new LgpdRequestDtos.SummaryResponse();
    when(servicoLgpdTitular.resumirOperacao(20)).thenReturn(expected);

    assertThat(controller.summary(20)).isSameAs(expected);
  }

  @Test
  void detalharDelegaAoService() {
    UUID id = UUID.randomUUID();
    LgpdRequestDtos.DetailResponse expected = new LgpdRequestDtos.DetailResponse();
    when(servicoLgpdTitular.detalhar(id)).thenReturn(expected);

    assertThat(controller.detalhar(id)).isSameAs(expected);
  }

  @Test
  void detalharPorProtocoloDelegaAoService() {
    LgpdRequestDtos.DetailResponse expected = new LgpdRequestDtos.DetailResponse();
    when(servicoLgpdTitular.detalharPorProtocolo("LGPD-1")).thenReturn(expected);

    assertThat(controller.detalharPorProtocolo("LGPD-1")).isSameAs(expected);
  }

  @Test
  void atualizarStatusDelegaAoService() {
    UUID id = UUID.randomUUID();
    LgpdRequestDtos.UpdateStatusRequest request = new LgpdRequestDtos.UpdateStatusRequest();
    LgpdRequestDtos.ItemResponse expected = new LgpdRequestDtos.ItemResponse();
    when(servicoLgpdTitular.atualizarStatus(id, request)).thenReturn(expected);

    assertThat(controller.atualizarStatus(id, request)).isSameAs(expected);
  }

  @Test
  void anonimizarDelegaAoServicoDeAnonimizacao() {
    UUID clientId = UUID.randomUUID();
    ServicoAnonimizacaoTitular.AnonimizacaoResponse expected =
        new ServicoAnonimizacaoTitular.AnonimizacaoResponse(clientId.toString(), "LGPD-1", "2026-01-01T00:00:00Z", 2);
    when(servicoAnonimizacao.anonimizar(clientId)).thenReturn(expected);

    assertThat(controller.anonimizar(clientId)).isSameAs(expected);
    verify(servicoAnonimizacao).anonimizar(clientId);
  }
}
