package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/** Espelha {@code modules/auth/domain/repository/RbacAuthorizationRepository.java}. */
@Repository
public class RbacAuthorizationRepository {

  @PersistenceContext
  private EntityManager entityManager;

  @SuppressWarnings("unchecked")
  public List<String> listarPermissoesPorUsuarioETenant(UUID tenantId, UUID userId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT DISTINCT p.code
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            JOIN role_permissions rp ON rp.role_id = r.id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE ur.user_id = :userId
            ORDER BY p.code
            """)
        .setParameter("userId", userId)
        .getResultList();
  }

  /**
   * Concede a role PROFESSIONAL o conjunto fixo de permissoes padrao — espelha
   * {@code ServicoProfissionais.grantDefaultProfessionalPermissions}.
   */
  public void grantDefaultProfessionalPermissions(UUID roleId) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT :roleId, p.id
            FROM permissions p
            WHERE p.code IN ('professional:read', 'appointment:read', 'appointment:write', 'dashboard:view')
            ON CONFLICT (role_id, permission_id) DO NOTHING
            """)
        .setParameter("roleId", roleId)
        .executeUpdate();
  }

  /** Concede a role ao conjunto atual de permissoes (usado no bootstrap de owner no registro). */
  public void grantAllPermissionsToRole(UUID roleId) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT :roleId, p.id
            FROM permissions p
            ON CONFLICT (role_id, permission_id) DO NOTHING
            """)
        .setParameter("roleId", roleId)
        .executeUpdate();
  }
}
