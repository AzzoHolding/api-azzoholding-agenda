package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PerfilAcessoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ResolucaoDeAcesso.AcessoEfetivo;

/**
 * Resolve o acesso de quem tem perfil: menu ({@code MenuService}) e permissoes
 * ({@code RbacPermissionCache}) saem daqui, da MESMA conta — o que a barra mostra e o que a API
 * libera nunca divergem.
 *
 * <p>Sem perfil, devolve vazio e o chamador segue o caminho de sempre (papel fixo). E o que
 * garante que o deploy nao muda o acesso de ninguem.
 */
@Component
public class AcessoPorPerfil {

  private final PerfilAcessoRepository perfilAcessoRepository;
  private final MenuRouteCache menuRouteCache;

  public AcessoPorPerfil(PerfilAcessoRepository perfilAcessoRepository, MenuRouteCache menuRouteCache) {
    this.perfilAcessoRepository = perfilAcessoRepository;
    this.menuRouteCache = menuRouteCache;
  }

  public Optional<AcessoEfetivo> resolver(UUID tenantId, UUID userId, String papel) {
    if (tenantId == null || userId == null) return Optional.empty();
    List<Object[]> perfis = perfilAcessoRepository.perfisDoUsuario(tenantId, userId);
    if (perfis.isEmpty()) return Optional.empty();

    boolean acessoTotal = perfis.stream().anyMatch(linha -> Boolean.TRUE.equals(linha[1]));
    List<UUID> ids = perfis.stream().map(linha -> PerfilAcessoRepository.uuid(linha[0])).toList();

    Set<String> rotas =
        ResolucaoDeAcesso.rotasEfetivas(
            perfilAcessoRepository.catalogoAtivo(),
            tetoDoDono(tenantId),
            new HashSet<>(perfilAcessoRepository.rotasDosPerfis(ids)),
            acessoTotal,
            papel);

    Set<String> permissoes = new TreeSet<>(perfilAcessoRepository.permissoesDasRotas(rotas));
    permissoes.addAll(ResolucaoDeAcesso.PERMISSOES_PESSOAIS);
    return Optional.of(new AcessoEfetivo(List.copyOf(rotas), List.copyOf(permissoes)));
  }

  /** O que o dono do salao recebe hoje — o limite de tudo que ele distribui. */
  public Set<String> tetoDoDono(UUID tenantId) {
    return new HashSet<>(menuRouteCache.getAllowedRoutes(tenantId, PapelUsuario.OWNER));
  }
}
