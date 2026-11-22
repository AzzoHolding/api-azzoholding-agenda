package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

class FotosDaEquipeTest {

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
  private static final byte[] SVG = "<svg onload=alert(1)>".getBytes();

  private final UUID tenant = UUID.randomUUID();
  private UsuarioRepository usuarios;
  private ProfissionalRepository profissionais;
  private MinioStorageService storage;
  private AuthenticatedUser logado;
  private AuditService audit;
  private FotosDaEquipe fotos;
  private Usuario usuario;
  private Profissional profissional;

  @BeforeEach
  void setUp() {
    usuarios = mock(UsuarioRepository.class);
    profissionais = mock(ProfissionalRepository.class);
    storage = mock(MinioStorageService.class);
    logado = mock(AuthenticatedUser.class);
    audit = mock(AuditService.class);
    ContextoTenant contexto = mock(ContextoTenant.class);
    when(contexto.obterTenantIdOuFalhar()).thenReturn(tenant);
    fotos = new FotosDaEquipe(usuarios, profissionais, storage, contexto, logado, audit, 1024);

    usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(tenant);
    profissional = new Profissional();
    profissional.setId(UUID.randomUUID());
    profissional.setTenantId(tenant);
    profissional.setUserId(usuario.getId());

    when(logado.idOuFalhar()).thenReturn(usuario.getId());
    when(usuarios.findById(usuario.getId())).thenReturn(Optional.of(usuario));
    when(profissionais.findByTenantIdAndUserId(tenant, usuario.getId())).thenReturn(Optional.of(profissional));
    when(profissionais.findByIdAndTenantId(profissional.getId(), tenant)).thenReturn(Optional.of(profissional));
    when(storage.salvarFotoDaEquipe(any(), any(), anyString(), eq(tenant), any())).thenReturn("tenant/x/team/nova.png");
    when(storage.gerarUrlAssinadaLeitura(anyString(), eq(tenant))).thenReturn("https://app/api/v1/storage/proxy?key=k");
  }

  @Test
  void aFotoDoPerfilVaiParaOUsuarioEParaOProfissionalEApagaAAntiga() {
    usuario.setAvatar("tenant/x/team/velha.png");
    profissional.setAvatar("tenant/x/team/velha.png");

    String url = fotos.trocarAMinha(PNG, "eu.png");

    assertThat(url).startsWith("https://");
    assertThat(usuario.getAvatar()).isEqualTo("tenant/x/team/nova.png");
    assertThat(profissional.getAvatar()).isEqualTo("tenant/x/team/nova.png");
    verify(storage).removerFotoDaEquipe("tenant/x/team/velha.png", tenant);
    ArgumentCaptor<AuditEventCommand> evento = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(audit).recordSuccess(evento.capture());
    assertThat(evento.getValue().action).isEqualTo("TEAM_PHOTO_UPDATED");
  }

  @Test
  void oDonoPoeAFotoDoProfissionalEElaApareceNoUsuarioDele() {
    fotos.trocarADoProfissional(profissional.getId(), PNG, "ana.png");

    assertThat(profissional.getAvatar()).isEqualTo("tenant/x/team/nova.png");
    assertThat(usuario.getAvatar()).isEqualTo("tenant/x/team/nova.png");
    verify(storage, never()).removerFotoDaEquipe(any(), any());
  }

  @Test
  void removerLimpaOsDoisEApagaODoStorage() {
    profissional.setAvatar("tenant/x/team/velha.png");
    usuario.setAvatar("tenant/x/team/velha.png");

    fotos.removerADoProfissional(profissional.getId());

    assertThat(profissional.getAvatar()).isNull();
    assertThat(usuario.getAvatar()).isNull();
    verify(storage).removerFotoDaEquipe("tenant/x/team/velha.png", tenant);
  }

  @Test
  void recusaOQueNaoEImagemPeloConteudoEOQuePassaDoTamanho() {
    assertThatThrownBy(() -> fotos.trocarAMinha(SVG, "foto.png"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("JPG, PNG ou WEBP");
    assertThatThrownBy(() -> fotos.trocarAMinha(new byte[2048], "grande.png"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Escolha uma menor");
    assertThatThrownBy(() -> fotos.trocarAMinha(new byte[0], "vazia.png")).hasMessage("Escolha uma imagem.");
    verify(storage, never()).salvarFotoDaEquipe(any(), any(), any(), any(), any());
  }

  @Test
  void profissionalDeOutroSalaoNaoEEncontrado() {
    UUID deOutro = UUID.randomUUID();
    when(profissionais.findByIdAndTenantId(deOutro, tenant)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> fotos.trocarADoProfissional(deOutro, PNG, "x.png"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Profissional não encontrado.");
  }

  @Test
  void urlDeForaPassaEChaveViraLinkAssinado() {
    assertThat(fotos.url(tenant, "https://cdn/foto.png")).isEqualTo("https://cdn/foto.png");
    assertThat(fotos.url(tenant, "tenant/x/team/a.png")).contains("/storage/proxy");
    assertThat(fotos.url(tenant, null)).isNull();
  }
}
