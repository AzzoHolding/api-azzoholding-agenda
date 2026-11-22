package br.com.phdigitalcode.azzo.agenda.pro.saude;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;

/**
 * Quem ve e quem escreve o que no modulo de saude (plano, secao 6).
 *
 * <ul>
 *   <li>Dono: tudo.</li>
 *   <li>Profissional: anamnese e fotos so de clientes que ela atende (tem agendamento com ela), e com as opcoes do
 *       perfil; ficha so dos atendimentos dela, ou de outras com "ver fichas de outras profissionais".</li>
 *   <li>Equipe sem a opcao de saude (recepcao): so pendencias, nunca o conteudo.</li>
 *   <li>Alertas de seguranca: tambem para a profissional que atende, mesmo sem a opcao de saude.</li>
 * </ul>
 * Cada leitura de dado de saude ou foto fica na auditoria (sem o conteudo).
 */
@Component
public class AcessoDeSaude {

  public static final String SAUDE_VER = "saude:ver";
  public static final String SAUDE_EDITAR = "saude:editar";
  public static final String FICHA_VER_OUTROS = "ficha:ver-outros";
  public static final String FOTOS_VER = "fotos:ver";
  public static final String FOTOS_MARKETING = "fotos:marketing";
  public static final String DOCUMENTOS_GERENCIAR = "documentos:gerenciar";
  static final String EDITAR_AGENDAMENTO_DE_OUTROS = "appointment:others-edit";

  private final ContextoTenant contexto;
  private final AuthenticatedUser usuario;
  private final PermissionService permissoes;
  private final NamedParameterJdbcTemplate jdbc;
  private final AuditService auditoria;

  public AcessoDeSaude(ContextoTenant contexto, AuthenticatedUser usuario, PermissionService permissoes,
      NamedParameterJdbcTemplate jdbc, AuditService auditoria) {
    this.contexto = contexto;
    this.usuario = usuario;
    this.permissoes = permissoes;
    this.jdbc = jdbc;
    this.auditoria = auditoria;
  }

  public UUID tenant() {
    return contexto.obterTenantIdOuFalhar();
  }

  public UUID usuarioId() {
    return usuario.idOuNulo();
  }

  public boolean dono() {
    return usuario.temRole("OWNER");
  }

  public boolean profissional() {
    return usuario.isProfessional();
  }

  public boolean tem(String permissao) {
    return dono() || permissoes.possuiPermissao(permissao);
  }

  /** O cadastro de profissional do usuario logado, ou nulo. */
  public UUID profissionalDoUsuario(UUID tenantId) {
    UUID u = usuarioId();
    if (u == null) return null;
    return jdbc.query("select id from professionals where tenant_id = :t and user_id = :u",
        Map.of("t", tenantId, "u", u), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
  }

  /** A profissional logada atende (ou ja atendeu) esta cliente. */
  public boolean atende(UUID tenantId, UUID clientId) {
    UUID p = profissionalDoUsuario(tenantId);
    if (p == null) return false;
    Boolean sim = jdbc.queryForObject("select exists(select 1 from appointments where tenant_id = :t and client_id = :c"
        + " and professional_id = :p)", Map.of("t", tenantId, "c", clientId, "p", p), Boolean.class);
    return Boolean.TRUE.equals(sim);
  }

  public boolean podeVerSaude(UUID tenantId, UUID clientId) {
    if (dono()) return true;
    if (!tem(SAUDE_VER)) return false;
    return !profissional() || atende(tenantId, clientId);
  }

  public boolean podeEditarSaude(UUID tenantId, UUID clientId) {
    if (dono()) return true;
    if (!tem(SAUDE_EDITAR)) return false;
    return !profissional() || atende(tenantId, clientId);
  }

  /** Alertas de seguranca: quem ve a saude, ou a profissional que atende a cliente. */
  public boolean podeVerAlertas(UUID tenantId, UUID clientId) {
    return podeVerSaude(tenantId, clientId) || (profissional() && atende(tenantId, clientId));
  }

  public void exigirSaudeVer(UUID tenantId, UUID clientId) {
    if (!podeVerSaude(tenantId, clientId)) negar(tenantId, "HEALTH_VIEW_DENIED", clientId);
  }

  public void exigirSaudeEditar(UUID tenantId, UUID clientId) {
    if (!podeEditarSaude(tenantId, clientId)) negar(tenantId, "HEALTH_EDIT_DENIED", clientId);
  }

  public void exigirGerenciarDocumentos() {
    if (!tem(DOCUMENTOS_GERENCIAR)) throw new ApiClientErrorException("Só o dono gerencia modelos e termos.", 403);
  }

  /** Ler a ficha: dono; a profissional do atendimento; ou quem tem "ver fichas de outras profissionais". */
  public boolean podeVerFicha(UUID tenantId, UUID professionalIdDaFicha) {
    if (dono()) return true;
    if (profissional()) {
      UUID p = profissionalDoUsuario(tenantId);
      if (p != null && p.equals(professionalIdDaFicha)) return true;
    }
    return tem(FICHA_VER_OUTROS);
  }

  /** Escrever a ficha: dono; a profissional do atendimento; ou quem edita agendamentos de outras. */
  public boolean podeEscreverFicha(UUID tenantId, UUID professionalIdDaFicha) {
    if (dono()) return true;
    if (profissional()) {
      UUID p = profissionalDoUsuario(tenantId);
      if (p != null && p.equals(professionalIdDaFicha)) return true;
      return permissoes.possuiPermissao(EDITAR_AGENDAMENTO_DE_OUTROS);
    }
    return permissoes.possuiPermissao("appointment:write");
  }

  public boolean podeVerFotos(UUID tenantId, UUID professionalIdDaFicha) {
    return podeVerFicha(tenantId, professionalIdDaFicha) && tem(FOTOS_VER);
  }

  public void negar(UUID tenantId, String acao, UUID clientId) {
    auditar(tenantId, acao, clientId, Map.of(), true);
    throw new ApiClientErrorException("Você não tem acesso a estes dados de saúde.", 403);
  }

  /** Registro de acesso: quem viu qual dado de saude de qual cliente (sem o conteudo). */
  public void registrarAcesso(UUID tenantId, String recurso, UUID clientId, UUID recursoId) {
    auditar(tenantId, "HEALTH_DATA_VIEW", clientId, Saude.m("recurso", recurso, "id", recursoId), false);
  }

  /** Auditoria do modulo: so ids, situacao e hash; nunca respostas, textos de ficha ou imagens. */
  public void auditar(UUID tenantId, String acao, UUID clientId, Map<String, Object> dados, boolean negado) {
    try {
      AuditEventCommand c = new AuditEventCommand();
      c.tenantId = tenantId;
      c.module = AuditConstants.Module.CUSTOMER;
      c.action = acao;
      c.entityType = "CLIENT_HEALTH";
      c.entityId = clientId == null ? null : clientId.toString();
      c.sourceChannel = AuditConstants.SourceChannel.API;
      c.after = dados;
      if (negado) auditoria.recordDeniedIsolated(c);
      else auditoria.recordSuccess(c);
    } catch (Exception ignored) {
      // A auditoria nunca derruba a operacao.
    }
  }
}
