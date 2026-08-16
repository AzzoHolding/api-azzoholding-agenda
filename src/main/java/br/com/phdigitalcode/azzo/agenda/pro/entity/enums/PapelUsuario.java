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
  FINANCE
}
