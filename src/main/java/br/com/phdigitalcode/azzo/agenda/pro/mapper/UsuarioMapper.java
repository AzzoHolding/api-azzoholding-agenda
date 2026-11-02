package br.com.phdigitalcode.azzo.agenda.pro.mapper;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.UsuarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/** Mapper manual Usuario -> UsuarioResponse (mesmos campos de {@code ServicoAuth#montarResposta}). */
@Component
public class UsuarioMapper {

  private final TenantRepository tenantRepository;
  private final MinioStorageService minioStorageService;

  public UsuarioMapper(
      TenantRepository tenantRepository, MinioStorageService minioStorageService) {
    this.tenantRepository = tenantRepository;
    this.minioStorageService = minioStorageService;
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
    Tenant tenant =
        usuario.getTenantId() == null
            ? null
            : tenantRepository.findById(usuario.getTenantId()).orElse(null);
    if (tenant != null) {
      user.salonName = tenant.getName();
      user.salonLogoUrl = urlDoLogo(tenant);
    }
    return user;
  }

  /** O topo do menu mostra o logo do salao. Falha aqui nao pode derrubar o login: a tela usa a inicial. */
  private String urlDoLogo(Tenant tenant) {
    String logo = tenant.getLogo();
    if (logo == null || logo.isBlank()) return null;
    if (logo.startsWith("http://") || logo.startsWith("https://")) return logo;
    try {
      return minioStorageService.gerarUrlAssinadaLeitura(logo, tenant.getId());
    } catch (RuntimeException e) {
      return null;
    }
  }
}
