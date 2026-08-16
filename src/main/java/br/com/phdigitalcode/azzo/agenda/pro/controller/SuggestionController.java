package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SuggestionDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.SuggestionService;
import jakarta.validation.Valid;

/** Espelha {@code modules/suggestions/api/SuggestionResource.java} ({@code @Path("/api/v1/suggestions")}). */
@RestController
@RequestMapping("/api/v1/suggestions")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'ADMIN')")
public class SuggestionController {

  private final SuggestionService suggestionService;

  public SuggestionController(SuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  @GetMapping
  public SuggestionDtos.SuggestionListResponse list(@RequestParam(name = "limit", required = false) Integer limit) {
    return suggestionService.listByTenant(limit);
  }

  @PostMapping
  public SuggestionDtos.SuggestionItemResponse create(@Valid @RequestBody SuggestionDtos.CreateSuggestionRequest request) {
    return suggestionService.create(request);
  }
}
