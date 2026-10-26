package br.com.phdigitalcode.azzo.agenda.pro.mapper;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.UsuarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/** Mapper manual Usuario -> UsuarioResponse (mesmos campos de {@code ServicoAuth#montarResposta}). */
@Component
public class UsuarioMapper {

  private final TenantRepository tenantRepository;

  public UsuarioMapper(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  public UsuarioResponse toResponse(Usuario usuario) {
    UsuarioResponse user = new UsuarioResponse();
    user.id = usuario.getId().toString();
    user.tenantId = usuario.getTenantId().toString();
    user.name = usuario.getName();
    user.email = usuario.getEmail();
    user.phone = usuario.getPhone();
    user.role = usuario.getRole().name();
    user.avatar = usuario.getAvatar();
    user.mfaEnabled = usuario.isMfaEnabled();
    user.createdAt = usuario.getCreatedAt() != null ? usuario.getCreatedAt().toString() : null;
    // O campo existia na resposta e nunca era preenchido: a tela Perfil mostrava "Salao: —" (25/10/2026).
    user.salonName = usuario.getTenantId() == null
        ? null
        : tenantRepository.findById(usuario.getTenantId()).map(t -> t.getName()).orElse(null);
    return user;
  }
}
