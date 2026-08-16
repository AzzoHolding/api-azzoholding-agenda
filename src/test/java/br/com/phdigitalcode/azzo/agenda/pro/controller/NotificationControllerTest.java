package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.notification.NotificationDtos.NotificationListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.NotificationService;

/**
 * Espelha {@code modules/notifications/api/NotificationsResource.java}: contrato de
 * rota/verbo/permissao e delegacao pura para {@link NotificationService}.
 */
class NotificationControllerTest {

  private NotificationService notificationService;
  private NotificationController controller;

  @BeforeEach
  void setUp() {
    notificationService = mock(NotificationService.class);
    controller = new NotificationController(notificationService);
  }

  // ─── contrato de classe ─────────────────────────────────────────────────

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(NotificationController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/notifications");
  }

  @Test
  void classeMantemRolesPermitidasDoOriginal() {
    PreAuthorize preAuthorize = NotificationController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('OWNER', 'PROFESSIONAL')");
  }

  @Test
  void cadaRotaMantemVerboECaminhoDoOriginal() throws NoSuchMethodException {
    assertThat(
            metodo(
                    "listar",
                    String.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    int.class,
                    String.class,
                    String.class)
                .getAnnotation(GetMapping.class)
                .value())
        .isEmpty();
    assertThat(
            metodo("listarMeusAgendamentos", boolean.class, int.class, String.class, String.class)
                .getAnnotation(GetMapping.class)
                .value())
        .containsExactly("/my-appointments");
    assertThat(metodo("marcarVisualizada", UUID.class).getAnnotation(PatchMapping.class).value())
        .containsExactly("/{id}/viewed");
    assertThat(metodo("marcarTodasVisualizadas").getAnnotation(PatchMapping.class).value())
        .containsExactly("/viewed/all");
    assertThat(metodo("remover", UUID.class).getAnnotation(DeleteMapping.class).value())
        .containsExactly("/{id}");
    assertThat(metodo("removerTodas").getAnnotation(DeleteMapping.class).value())
        .containsExactly("/all");
  }

  @Test
  void todasAsRotasExigemPermissaoDoOriginal() throws NoSuchMethodException {
    assertThat(
            metodo(
                    "listar",
                    String.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    int.class,
                    String.class,
                    String.class)
                .getAnnotation(RequiresPermission.class)
                .value())
        .isEqualTo("notification:read");
    assertThat(
            metodo("listarMeusAgendamentos", boolean.class, int.class, String.class, String.class)
                .getAnnotation(RequiresPermission.class)
                .value())
        .isEqualTo("notification:read");
    assertThat(metodo("marcarVisualizada", UUID.class).getAnnotation(RequiresPermission.class).value())
        .isEqualTo("notification:read");
    assertThat(metodo("marcarTodasVisualizadas").getAnnotation(RequiresPermission.class).value())
        .isEqualTo("notification:read");
    assertThat(metodo("remover", UUID.class).getAnnotation(RequiresPermission.class).value())
        .isEqualTo("notification:writer");
    assertThat(metodo("removerTodas").getAnnotation(RequiresPermission.class).value())
        .isEqualTo("notification:writer");
  }

  @Test
  void rotaMeusAgendamentosExigeRoleProfessionalAlemDaClasse() throws NoSuchMethodException {
    PreAuthorize preAuthorize =
        metodo("listarMeusAgendamentos", boolean.class, int.class, String.class, String.class)
            .getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo("hasRole('PROFESSIONAL')");
  }

  @Test
  void rotasDeExclusaoExigemRoleOwnerAlemDaClasse() throws NoSuchMethodException {
    assertThat(metodo("remover", UUID.class).getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasRole('OWNER')");
    assertThat(metodo("removerTodas").getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasRole('OWNER')");
  }

  private Method metodo(String nome, Class<?>... parametros) throws NoSuchMethodException {
    return NotificationController.class.getDeclaredMethod(nome, parametros);
  }

  // ─── listar ───────────────────────────────────────────────────────────────

  @Test
  void listarDelegaParaServiceSemCursor() {
    NotificationListResponse response = new NotificationListResponse();
    when(notificationService.listar("PENDING", "whatsapp", false, true, 50, null, null))
        .thenReturn(response);

    NotificationListResponse result =
        controller.listar("PENDING", "whatsapp", false, true, 50, null, null);

    assertThat(result).isSameAs(response);
  }

  @Test
  void listarParseiaCursorValidoEDelegaParaService() {
    Instant cursorCreatedAt = Instant.parse("2026-01-01T10:00:00Z");
    UUID cursorId = UUID.randomUUID();
    NotificationListResponse response = new NotificationListResponse();
    when(notificationService.listar(
            isNull(), isNull(), eq(false), eq(false), eq(0), eq(cursorCreatedAt), eq(cursorId)))
        .thenReturn(response);

    NotificationListResponse result =
        controller.listar(
            null, null, false, false, 0, cursorCreatedAt.toString(), cursorId.toString());

    assertThat(result).isSameAs(response);
  }

  @Test
  void listarRejeitaCursorCreatedAtInvalidoCom400() {
    assertThatThrownBy(
            () -> controller.listar(null, null, false, false, 0, "nao-e-uma-data", UUID.randomUUID().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(
            e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
  }

  @Test
  void listarRejeitaCursorIdInvalidoCom400() {
    assertThatThrownBy(
            () -> controller.listar(null, null, false, false, 0, Instant.now().toString(), "nao-e-um-uuid"))
        .isInstanceOf(ApiClientErrorException.class);
  }

  @Test
  void listarRejeitaCursorParcialCom400() {
    assertThatThrownBy(
            () -> controller.listar(null, null, false, false, 0, Instant.now().toString(), null))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(
            e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()));

    assertThatThrownBy(
            () -> controller.listar(null, null, false, false, 0, null, UUID.randomUUID().toString()))
        .isInstanceOf(ApiClientErrorException.class);
  }

  // ─── listarMeusAgendamentos ─────────────────────────────────────────────

  @Test
  void listarMeusAgendamentosDelegaParaService() {
    NotificationListResponse response = new NotificationListResponse();
    when(notificationService.listarMeusAgendamentos(true, 10, null, null)).thenReturn(response);

    assertThat(controller.listarMeusAgendamentos(true, 10, null, null)).isSameAs(response);
  }

  // ─── marcarVisualizada ──────────────────────────────────────────────────

  @Test
  void marcarVisualizadaRetorna200QuandoAtualizado() {
    UUID id = UUID.randomUUID();
    when(notificationService.marcarComoVisualizada(id)).thenReturn(true);

    ResponseEntity<Map<String, Object>> result = controller.marcarVisualizada(id);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(Map.of("updated", true));
  }

  @Test
  void marcarVisualizadaLanca404QuandoNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(notificationService.marcarComoVisualizada(id)).thenReturn(false);

    assertThatThrownBy(() -> controller.marcarVisualizada(id))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(
            e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  void marcarTodasVisualizadasDelegaERetornaContagem() {
    when(notificationService.marcarTodasComoVisualizadas()).thenReturn(7L);

    ResponseEntity<Map<String, Object>> result = controller.marcarTodasVisualizadas();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(Map.of("updated", 7L));
  }

  // ─── remover / removerTodas ─────────────────────────────────────────────

  @Test
  void removerRetorna204QuandoRemovido() {
    UUID id = UUID.randomUUID();
    when(notificationService.remover(id)).thenReturn(true);

    ResponseEntity<Void> result = controller.remover(id);

    verify(notificationService).remover(id);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void removerLanca404QuandoNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(notificationService.remover(id)).thenReturn(false);

    assertThatThrownBy(() -> controller.remover(id))
        .isInstanceOf(ApiClientErrorException.class)
        .satisfies(
            e -> assertThat(((ApiClientErrorException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  void removerTodasDelegaERetornaContagem() {
    when(notificationService.removerTodasDoTenant()).thenReturn(3L);

    ResponseEntity<Map<String, Object>> result = controller.removerTodas();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(Map.of("deleted", 3L));
  }
}
