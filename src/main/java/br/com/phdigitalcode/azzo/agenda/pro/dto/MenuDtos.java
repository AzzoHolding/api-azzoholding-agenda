package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Espelha os 9 DTOs de {@code application/dto/menu/*} do original (um arquivo por classe la,
 * agrupados aqui em um so, seguindo a convencao do projeto Spring — ver {@code FiscalDtos},
 * {@code SystemAdminDtos}). Classes de campo publico e sem getter, como no original: o contrato
 * JSON depende do nome do campo, renomear qualquer um quebra o frontend real que ja consome
 * {@code GET /api/v1/config/menus/current}.
 */
public final class MenuDtos {

  private MenuDtos() {}

  public static class MenuConfigItemResponse {
    public String id;
    public String route;
    public String label;
    public String parentId;
    public int displayOrder;
    public String iconKey;
    public boolean active;
    public boolean sidebarVisible = true;
  }

  public static class MenuConfigResponse {
    public String role;
    public List<String> allowedRoutes = new ArrayList<>();
    public List<MenuConfigItemResponse> items = new ArrayList<>();
  }

  public static class MenuOverrideRequest {
    public String tenantId;
    public String scope;

    @NotBlank
    public String role;

    @NotBlank
    public String route;

    @NotNull
    public Boolean enabled;

    public String reason;
  }

  public static class BulkMenuOverrideRequest {
    public String tenantId;
    public String scope;

    @NotBlank
    public String role;

    @Valid
    @NotEmpty
    public List<MenuOverrideItem> items = new ArrayList<>();

    public String reason;

    public static class MenuOverrideItem {
      @NotBlank
      public String route;
      public boolean enabled;
    }
  }

  public static class MenuRoleRouteItemResponse {
    public String route;
    public boolean enabled;
    public boolean overridden;
    public String reason;
  }

  public static class MenuRoleRoutesResponse {
    public String tenantId;
    public String scope;
    public String role;
    public List<MenuRoleRouteItemResponse> items = new ArrayList<>();
  }

  public static class MenuCatalogItemRequest {
    public String id;
    @NotBlank public String route;
    @NotBlank public String label;
    public String parentId;
    @NotNull public Integer displayOrder;
    public String iconKey;
    @NotNull public Boolean active;
    public Boolean sidebarVisible;
    @Valid public List<RoleVisibilityRequest> roleVisibilities = new ArrayList<>();

    public static class RoleVisibilityRequest {
      @NotBlank public String role;
      @NotNull public Boolean enabled;
    }
  }

  public static class MenuCatalogItemResponse {
    public String id;
    public String route;
    public String label;
    public String parentId;
    public String parentRoute;
    public String parentLabel;
    public int displayOrder;
    public String iconKey;
    public boolean active;
    public boolean sidebarVisible = true;
    public int childrenCount;
    public List<RoleVisibilityResponse> roleVisibilities = new ArrayList<>();

    public static class RoleVisibilityResponse {
      public String role;
      public boolean enabled;
    }
  }

  public static class MenuCatalogResponse {
    public List<MenuCatalogItemResponse> items = new ArrayList<>();
  }
}
