package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SuggestionDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.SuggestionService;

/** Cobre {@code modules/suggestions/api/SuggestionResource.java} — delegacao pura ao service. */
class SuggestionControllerTest {

  private SuggestionService suggestionService;
  private SuggestionController controller;

  @BeforeEach
  void setUp() {
    suggestionService = mock(SuggestionService.class);
    controller = new SuggestionController(suggestionService);
  }

  @Test
  void listDelegaAoServiceComOLimiteRecebido() {
    SuggestionDtos.SuggestionListResponse esperado = new SuggestionDtos.SuggestionListResponse();
    when(suggestionService.listByTenant(10)).thenReturn(esperado);

    assertThat(controller.list(10)).isSameAs(esperado);
    verify(suggestionService).listByTenant(10);
  }

  @Test
  void listSemLimiteDelegaNuloAoService() {
    SuggestionDtos.SuggestionListResponse esperado = new SuggestionDtos.SuggestionListResponse();
    when(suggestionService.listByTenant(null)).thenReturn(esperado);

    assertThat(controller.list(null)).isSameAs(esperado);
  }

  @Test
  void createDelegaAoService() {
    SuggestionDtos.CreateSuggestionRequest request = new SuggestionDtos.CreateSuggestionRequest();
    SuggestionDtos.SuggestionItemResponse esperado = new SuggestionDtos.SuggestionItemResponse();
    when(suggestionService.create(request)).thenReturn(esperado);

    assertThat(controller.create(request)).isSameAs(esperado);
  }
}
