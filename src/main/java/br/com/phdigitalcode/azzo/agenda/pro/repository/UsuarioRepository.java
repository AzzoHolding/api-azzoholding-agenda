package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;

/** Espelha {@code modules/auth/domain/repository/UsuarioRepository.java} (Panache -> Spring Data JPA). */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

  Optional<Usuario> findByTenantIdAndEmail(UUID tenantId, String email);

  Optional<Usuario> findByEmail(String email);

  /** Usado por {@code ServicoEstoque.cancelarInventario} para reconferir a senha do usuario logado. */
  Optional<Usuario> findByIdAndTenantId(UUID id, UUID tenantId);

  List<Usuario> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

  default Map<UUID, String> mapNamesByTenantAndIds(UUID tenantId, List<UUID> userIds) {
    if (tenantId == null || userIds == null || userIds.isEmpty()) return Map.of();
    return findByTenantIdAndIdIn(tenantId, userIds).stream()
        .collect(Collectors.toMap(Usuario::getId, Usuario::getName));
  }
}
