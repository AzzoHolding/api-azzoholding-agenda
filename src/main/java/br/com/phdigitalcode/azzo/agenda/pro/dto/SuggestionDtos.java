package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Espelha {@code modules/suggestions/api/dto/SuggestionDtos.java}. Campos publicos porque o
 * original tambem os expoe assim e o contrato JSON precisa bater com o frontend.
 */
public final class SuggestionDtos {

  private SuggestionDtos() {}

  public static class CreateSuggestionRequest {
    @Size(max = 30)
    public String category;

    @NotBlank
    @Size(max = 160)
    public String title;

    @NotBlank
    @Size(max = 5000)
    public String message;

    @Size(max = 160)
    public String sourcePage;
  }

  public static class SuggestionItemResponse {
    public String id;
    public String tenantId;
    public String userId;
    public String userName;
    public String userRole;
    public String category;
    public String title;
    public String message;
    public String status;
    public String sourcePage;
    public String createdAt;
    public String updatedAt;
  }

  public static class SuggestionListResponse {
    public List<SuggestionItemResponse> items = new ArrayList<>();
    public int limit;
  }
}
