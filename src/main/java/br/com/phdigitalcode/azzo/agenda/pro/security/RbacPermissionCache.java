package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;

/**
 * Bean dedicado para o cache {@code rbac-user-permissions} (RBAC fino).
 *
 * <p>Extraido de {@link PermissionService} de proposito: o Spring Cache (como todo AOP baseado em
 * proxy do Spring) NAO intercepta chamadas {@code this.metodo(...)} dentro da mesma classe — se o
 * metodo anotado {@code @Cacheable} estivesse em {@code PermissionService} e fosse chamado por
 * {@code validarPermissao} no mesmo bean, o cache seria silenciosamente ignorado (self-invocation
 * bypass, uma pegadinha classica do Spring AOP). Separar em um bean proprio, injetado por
 * construtor, garante que a chamada passe pelo proxy e o cache funcione de fato.
 */
@Component
public class RbacPermissionCache {

  private final RbacAuthorizationRepository rbacAuthorizationRepository;
  private final AcessoPorPerfil acessoPorPerfil;

  public RbacPermissionCache(
      RbacAuthorizationRepository rbacAuthorizationRepository, AcessoPorPerfil acessoPorPerfil) {
    this.rbacAuthorizationRepository = rbacAuthorizationRepository;
    this.acessoPorPerfil = acessoPorPerfil;
  }

  /**
   * Quem tem perfil de acesso usa as permissoes dos perfis (limitadas ao teto do dono); quem nao
   * tem, as do papel fixo, como sempre. Ver {@link AcessoPorPerfil}.
   */
  @Cacheable(cacheNames = "rbac-user-permissions", key = "#tenantId + ':' + #userId")
  public Set<String> listarPermissoesUsuario(UUID tenantId, UUID userId) {
    return acessoPorPerfil
        .resolver(tenantId, userId, null)
        .<Set<String>>map(acesso -> new HashSet<>(acesso.permissoes()))
        .orElseGet(
            () ->
                new HashSet<>(
                    rbacAuthorizationRepository.listarPermissoesPorUsuarioETenant(tenantId, userId)));
  }

  @CacheEvict(cacheNames = "rbac-user-permissions", allEntries = true)
  public void limparCache() {
    // No-op: invalidacao via anotacao.
  }
}
