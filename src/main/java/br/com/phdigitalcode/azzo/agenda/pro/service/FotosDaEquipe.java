package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * A foto de quem trabalha no salao (08/11/2026). Ate aqui o campo {@code avatar} existia no
 * profissional e no usuario, mas nao havia como enviar imagem: ninguem tinha foto.
 *
 * <p>Uma foto so por pessoa: o profissional e o usuario dele apontam para o MESMO arquivo — a foto
 * que a pessoa poe no Perfil aparece na Equipe, na agenda e no agendamento online, e a que o dono
 * poe na Equipe aparece no menu da pessoa. O arquivo vai para o MinIO (chave do salao) e sai pelo
 * proxy com URL assinada, como a logo do salao.
 */
@Service
public class FotosDaEquipe {

  private static final Logger LOG = LoggerFactory.getLogger(FotosDaEquipe.class);
  static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");

  private final UsuarioRepository usuarios;
  private final ProfissionalRepository profissionais;
  private final MinioStorageService storage;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser usuarioLogado;
  private final AuditService auditService;
  private final long tamanhoMaximo;

  public FotosDaEquipe(
      UsuarioRepository usuarios,
      ProfissionalRepository profissionais,
      MinioStorageService storage,
      ContextoTenant contextoTenant,
      AuthenticatedUser usuarioLogado,
      AuditService auditService,
      @Value("${app.team.photo.max-bytes:5242880}") long tamanhoMaximo) {
    this.usuarios = usuarios;
    this.profissionais = profissionais;
    this.storage = storage;
    this.contextoTenant = contextoTenant;
    this.usuarioLogado = usuarioLogado;
    this.auditService = auditService;
    this.tamanhoMaximo = tamanhoMaximo;
  }

  /** A propria pessoa, na tela Perfil. Vale para qualquer papel. */
  @Transactional
  public String trocarAMinha(byte[] arquivo, String nomeArquivo) {
    Usuario usuario = euMesmo();
    Profissional profissional = profissionalDo(usuario);
    return gravar(usuario.getTenantId(), usuario, profissional, arquivo, nomeArquivo, "USER", usuario.getId());
  }

  @Transactional
  public void removerAMinha() {
    Usuario usuario = euMesmo();
    remover(usuario.getTenantId(), usuario, profissionalDo(usuario), "USER", usuario.getId());
  }

  /** O dono (ou quem tem {@code professional:write}), na tela Equipe. */
  @Transactional
  public String trocarADoProfissional(UUID profissionalId, byte[] arquivo, String nomeArquivo) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional profissional = profissionalDoSalao(profissionalId, tenantId);
    Usuario usuario = usuarioDo(profissional);
    return gravar(tenantId, usuario, profissional, arquivo, nomeArquivo, "PROFESSIONAL", profissional.getId());
  }

  @Transactional
  public void removerADoProfissional(UUID profissionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Profissional profissional = profissionalDoSalao(profissionalId, tenantId);
    remover(tenantId, usuarioDo(profissional), profissional, "PROFESSIONAL", profissional.getId());
  }

  /**
   * URL que o navegador abre: a de fora passa como veio; a chave do storage vira link assinado do
   * proxy. Falha aqui nao derruba tela nenhuma — sem URL, a tela mostra as iniciais.
   */
  public String url(UUID tenantId, String foto) {
    if (foto == null || foto.isBlank()) return null;
    if (foto.startsWith("http://") || foto.startsWith("https://")) return foto;
    try {
      return storage.gerarUrlAssinadaLeitura(foto, tenantId);
    } catch (RuntimeException e) {
      LOG.warn("Falha ao resolver URL da foto (tenantId={}).", tenantId, e);
      return null;
    }
  }

  // ---------------------------------------------------------------------------------------------

  private String gravar(
      UUID tenantId, Usuario usuario, Profissional profissional, byte[] arquivo, String nomeArquivo,
      String tipo, UUID id) {
    String contentType = validar(arquivo);
    UUID dono = profissional != null ? profissional.getId() : usuario.getId();
    String nova;
    try {
      nova = storage.salvarFotoDaEquipe(arquivo, nomeArquivo, contentType, tenantId, dono);
    } catch (IllegalStateException e) {
      throw new ApiClientErrorException("Storage de imagens indisponível. Tente de novo em instantes.", 503);
    }
    String anterior = fotoAtual(usuario, profissional);
    if (usuario != null) {
      usuario.setAvatar(nova);
      usuarios.save(usuario);
    }
    if (profissional != null) {
      profissional.setAvatar(nova);
      profissionais.save(profissional);
    }
    apagarSeDoStorage(anterior, nova, tenantId);
    auditar(tenantId, "TEAM_PHOTO_UPDATED", tipo, id, anterior != null);
    return url(tenantId, nova);
  }

  private void remover(UUID tenantId, Usuario usuario, Profissional profissional, String tipo, UUID id) {
    String anterior = fotoAtual(usuario, profissional);
    if (usuario != null) {
      usuario.setAvatar(null);
      usuarios.save(usuario);
    }
    if (profissional != null) {
      profissional.setAvatar(null);
      profissionais.save(profissional);
    }
    apagarSeDoStorage(anterior, null, tenantId);
    auditar(tenantId, "TEAM_PHOTO_REMOVED", tipo, id, anterior != null);
  }

  /** Tipo pelo CONTEUDO, nunca pelo nome (SEG-021): HTML ou SVG chamado foto.png nao passa. */
  String validar(byte[] arquivo) {
    if (arquivo == null || arquivo.length == 0) {
      throw new ApiClientErrorException("Escolha uma imagem.", 400);
    }
    if (arquivo.length > tamanhoMaximo) {
      throw new ApiClientErrorException(
          "A imagem passa de " + (tamanhoMaximo / (1024 * 1024)) + " MB. Escolha uma menor.", 400);
    }
    String tipo = br.com.phdigitalcode.azzo.agenda.pro.controller.SalonController.detectarContentType(arquivo, null);
    if (!TIPOS_ACEITOS.contains(tipo)) {
      throw new ApiClientErrorException("Formato não suportado. Use JPG, PNG ou WEBP.", 400);
    }
    return tipo;
  }

  private static String fotoAtual(Usuario usuario, Profissional profissional) {
    if (profissional != null && profissional.getAvatar() != null && !profissional.getAvatar().isBlank()) {
      return profissional.getAvatar();
    }
    return usuario == null || usuario.getAvatar() == null || usuario.getAvatar().isBlank() ? null : usuario.getAvatar();
  }

  private void apagarSeDoStorage(String anterior, String nova, UUID tenantId) {
    if (anterior == null || anterior.startsWith("http://") || anterior.startsWith("https://")) return;
    if (Objects.equals(anterior, nova)) return;
    storage.removerFotoDaEquipe(anterior, tenantId);
  }

  private Usuario euMesmo() {
    UUID id = usuarioLogado.idOuFalhar();
    return usuarios.findById(id).orElseThrow(() -> new ApiClientErrorException("Usuário não encontrado", 401));
  }

  private Profissional profissionalDo(Usuario usuario) {
    if (usuario.getTenantId() == null) return null;
    return profissionais.findByTenantIdAndUserId(usuario.getTenantId(), usuario.getId()).orElse(null);
  }

  private Profissional profissionalDoSalao(UUID id, UUID tenantId) {
    return profissionais.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Profissional não encontrado.", 404));
  }

  private Usuario usuarioDo(Profissional profissional) {
    if (profissional.getUserId() == null) return null;
    return usuarios.findById(profissional.getUserId())
        .filter(u -> Objects.equals(u.getTenantId(), profissional.getTenantId()))
        .orElse(null);
  }

  private void auditar(UUID tenantId, String acao, String tipo, UUID id, boolean tinhaFoto) {
    if (tenantId == null) return;
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.PROFESSIONAL;
    command.action = acao;
    command.entityType = tipo;
    command.entityId = id.toString();
    command.sourceChannel = AuditConstants.SourceChannel.API;
    Map<String, Object> detalhes = new LinkedHashMap<>();
    detalhes.put("tinhaFoto", tinhaFoto);
    command.metadata = detalhes;
    auditService.recordSuccess(command);
  }
}
