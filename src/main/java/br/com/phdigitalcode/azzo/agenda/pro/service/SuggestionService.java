package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SuggestionDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FeedbackSuggestion;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FeedbackSuggestionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Espelha {@code modules/suggestions/application/SuggestionService.java}. */
@Service
public class SuggestionService {

  private static final String CATEGORY_BUG = "BUG";
  private static final String CATEGORY_MELHORIA = "MELHORIA";
  private static final String CATEGORY_FUNCIONALIDADE = "FUNCIONALIDADE";
  private static final String CATEGORY_USABILIDADE = "USABILIDADE";
  private static final String CATEGORY_OUTRO = "OUTRO";

  private final FeedbackSuggestionRepository feedbackSuggestionRepository;
  private final UsuarioRepository usuarioRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public SuggestionService(
      FeedbackSuggestionRepository feedbackSuggestionRepository,
      UsuarioRepository usuarioRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser) {
    this.feedbackSuggestionRepository = feedbackSuggestionRepository;
    this.usuarioRepository = usuarioRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  @Transactional
  public SuggestionDtos.SuggestionItemResponse create(SuggestionDtos.CreateSuggestionRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = authenticatedUser.idOuNulo();
    Usuario user = userId != null ? usuarioRepository.findById(userId).orElse(null) : null;
    if (user == null) {
      throw new ApiClientErrorException("Usuario autenticado nao encontrado.", HttpStatus.UNAUTHORIZED.value());
    }

    FeedbackSuggestion suggestion = new FeedbackSuggestion();
    suggestion.setTenantId(tenantId);
    suggestion.setUserId(user.getId());
    suggestion.setUserName(user.getName());
    suggestion.setUserRole(user.getRole() != null ? user.getRole().name() : "UNKNOWN");
    suggestion.setCategory(normalizeCategory(request.category));
    suggestion.setTitle(normalizeRequired(request.title, "Titulo da sugestao obrigatorio."));
    suggestion.setMessage(normalizeRequired(request.message, "Descricao da sugestao obrigatoria."));
    suggestion.setSourcePage(normalizeOptional(request.sourcePage));
    suggestion.setStatus("OPEN");
    // Armadilha 2 (flush): saveAndFlush para que id/createdAt/updatedAt do @PrePersist entrem na
    // resposta desta mesma chamada, como o persist() do Panache original ja fazia na hora.
    suggestion = feedbackSuggestionRepository.saveAndFlush(suggestion);
    return toItem(suggestion);
  }

  @Transactional(readOnly = true)
  public SuggestionDtos.SuggestionListResponse listByTenant(Integer limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int sanitizedLimit = sanitizeLimit(limit);
    List<FeedbackSuggestion> items =
        feedbackSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, Limit.of(sanitizedLimit));

    SuggestionDtos.SuggestionListResponse response = new SuggestionDtos.SuggestionListResponse();
    response.limit = sanitizedLimit;
    response.items = items.stream().map(this::toItem).toList();
    return response;
  }

  private SuggestionDtos.SuggestionItemResponse toItem(FeedbackSuggestion entity) {
    SuggestionDtos.SuggestionItemResponse response = new SuggestionDtos.SuggestionItemResponse();
    response.id = entity.getId() != null ? entity.getId().toString() : null;
    response.tenantId = entity.getTenantId() != null ? entity.getTenantId().toString() : null;
    response.userId = entity.getUserId() != null ? entity.getUserId().toString() : null;
    response.userName = entity.getUserName();
    response.userRole = entity.getUserRole();
    response.category = entity.getCategory();
    response.title = entity.getTitle();
    response.message = entity.getMessage();
    response.status = entity.getStatus();
    response.sourcePage = entity.getSourcePage();
    response.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    response.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;
    return response;
  }

  private int sanitizeLimit(Integer limit) {
    if (limit == null || limit < 1) return 50;
    return Math.min(limit, 200);
  }

  private String normalizeRequired(String value, String errorMessage) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      throw new ApiClientErrorException(errorMessage, HttpStatus.BAD_REQUEST.value());
    }
    return normalized;
  }

  private String normalizeOptional(String value) {
    if (value == null) return null;
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeCategory(String value) {
    if (value == null || value.isBlank()) return CATEGORY_MELHORIA;
    String normalized = value.trim().toUpperCase();
    if (CATEGORY_BUG.equals(normalized)) return CATEGORY_BUG;
    if (CATEGORY_MELHORIA.equals(normalized)) return CATEGORY_MELHORIA;
    if (CATEGORY_FUNCIONALIDADE.equals(normalized)) return CATEGORY_FUNCIONALIDADE;
    if (CATEGORY_USABILIDADE.equals(normalized)) return CATEGORY_USABILIDADE;
    if (CATEGORY_OUTRO.equals(normalized)) return CATEGORY_OUTRO;
    throw new ApiClientErrorException(
        "Categoria invalida. Use BUG, MELHORIA, FUNCIONALIDADE, USABILIDADE ou OUTRO.",
        HttpStatus.BAD_REQUEST.value());
  }
}
