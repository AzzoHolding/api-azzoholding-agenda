package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/**
 * Espelha {@code br.com.phdigitalcode.azzo.agenda.pro.domain.entity.enums.PapelUsuario} do
 * projeto Quarkus original. 4 papeis (roles) usados em RBAC grosso ({@code @RolesAllowed} ->
 * {@code @PreAuthorize}).
 */
public enum PapelUsuario {
  OWNER,
  PROFESSIONAL,
  ADMIN,
  FINANCE,
  /** Membro da equipe SEM agenda (recepcao, financeiro): o acesso vem so dos perfis. */
  STAFF
}
