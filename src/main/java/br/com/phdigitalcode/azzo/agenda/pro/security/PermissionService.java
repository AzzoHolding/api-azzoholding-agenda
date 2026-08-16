package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Equivalente Spring de {@code modules/auth/application/PermissionService.java}. RBAC fino:
 * resolve as permissoes do usuario autenticado (via {@code JOIN} de {@code user_roles} /
 * {@code role_permissions} / {@code permissions}) e nega acesso ({@link AccessDeniedException},
 * mapeado para 403 pelo Spring Security) se a permissao exigida nao estiver no conjunto. Cache
 * nomeado {@code rbac-user-permissions} (TTL/tamanho configurados em {@code CacheConfig}, mesmos
 * valores do Caffeine cache do Quarkus original: 5 min / 200k entradas).
 */
@Service
public class PermissionService {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionService.class);

  private final ContextoTenant contextoTenant;
  private final RbacPermissionCache rbacPermissionCache;

  public PermissionService(ContextoTenant contextoTenant, RbacPermissionCache rbacPermissionCache) {
    this.contextoTenant = contextoTenant;
    this.rbacPermissionCache = rbacPermissionCache;
  }

  public void validarPermissao(String permissionCode) {
    UUID tenantId = obterTenantIdOuFalhar();
    UUID userId = obterUserIdOuFalhar();

    Set<String> permissions = rbacPermissionCache.listarPermissoesUsuario(tenantId, userId);
    if (!permissions.contains(permissionCode)) {
      LOG.warn(CorrelatedLogging.context(
          "Acesso negado por permissao",
          "userId", userId,
          "tenantId", tenantId,
          "permission", permissionCode));
      throw new AccessDeniedException("Acesso negado");
    }
  }

  public void limparCachePermissoesUsuario() {
    rbacPermissionCache.limparCache();
  }

  private UUID obterTenantIdOuFalhar() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal
        && jwtPrincipal.tenantId() != null) {
      return jwtPrincipal.tenantId();
    }
    return contextoTenant.obterTenantIdOuFalhar();
  }

  private UUID obterUserIdOuFalhar() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal
        && jwtPrincipal.userId() != null) {
      return jwtPrincipal.userId();
    }
    throw new IllegalStateException("UserId ausente no JWT");
  }
}
