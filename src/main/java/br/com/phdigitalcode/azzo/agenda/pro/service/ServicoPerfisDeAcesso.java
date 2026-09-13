package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.AcessoEfetivoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.AtribuicaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.EdicaoDeMembroRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.FuncionalidadeResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.HistoricoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.MembroResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.MensagemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.NovoMembroRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilRef;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.AcessoDtos.PerfilResumoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PerfilAcessoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AcessoPorPerfil;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.MenuRouteCache;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ResolucaoDeAcesso;
import br.com.phdigitalcode.azzo.agenda.pro.security.ResolucaoDeAcesso.ItemCatalogo;
import br.com.phdigitalcode.azzo.agenda.pro.security.TokenRevocationService;

/**
 * Perfis de acesso da equipe — {@code docs/ESPEC_PERFIS_DE_ACESSO.md}.
 *
 * <p>Regras que vivem aqui:
 *
 * <ul>
 *   <li><b>O teto do dono</b>: so se distribui o que o dono recebe, menos as exclusivas dele (R2,
 *       R4). Validado ao salvar; e o efetivo e SEMPRE recalculado com o teto de agora
 *       ({@link AcessoPorPerfil});
 *   <li><b>Varios perfis por pessoa</b> (D1), somados; <b>lista vazia = Acesso completo</b> (D5);
 *   <li><b>Membro sem agenda</b> (D4): login com papel {@code STAFF}, sem cadastro de profissional;
 *   <li><b>Sair da equipe nao apaga o usuario</b> — ha tabelas que apontam para ele com
 *       {@code RESTRICT}. O acesso e bloqueado trocando a senha e revogando os tokens.
 * </ul>
 *
 * <p>Toda mudanca grava {@code auditoria_permissao} e limpa o cache de permissoes: vale na proxima
 * requisicao de quem foi afetado (R9, R10).
 */
@Service
public class ServicoPerfisDeAcesso {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoPerfisDeAcesso.class);

  static final String NOME_DO_ACESSO_COMPLETO = "Acesso completo";
  private static final String DESCRICAO_DO_ACESSO_COMPLETO =
      "Tudo o que o dono tem, menos as configuracoes exclusivas dele.";
  private static final int HISTORICO_PADRAO = 100;
  private static final int HISTORICO_MAXIMO = 500;
  private static final String CARACTERES_DA_SENHA =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#$%";
  private static final Random ALEATORIO = new SecureRandom();

  private final PerfilAcessoRepository repositorio;
  private final AcessoPorPerfil acessoPorPerfil;
  private final MenuRouteCache menuRouteCache;
  private final RbacAuthorizationRepository rbacAuthorizationRepository;
  private final UsuarioRepository usuarioRepository;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final CredentialsEmailService credentialsEmailService;
  private final TokenRevocationService tokenRevocationService;
  private final PermissionService permissionService;
  private final AuditService auditService;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public ServicoPerfisDeAcesso(
      PerfilAcessoRepository repositorio,
      AcessoPorPerfil acessoPorPerfil,
      MenuRouteCache menuRouteCache,
      RbacAuthorizationRepository rbacAuthorizationRepository,
      UsuarioRepository usuarioRepository,
      PasswordPolicyValidator passwordPolicyValidator,
      CredentialsEmailService credentialsEmailService,
      TokenRevocationService tokenRevocationService,
      PermissionService permissionService,
      AuditService auditService,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser) {
    this.repositorio = repositorio;
    this.acessoPorPerfil = acessoPorPerfil;
    this.menuRouteCache = menuRouteCache;
    this.rbacAuthorizationRepository = rbacAuthorizationRepository;
    this.usuarioRepository = usuarioRepository;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.credentialsEmailService = credentialsEmailService;
    this.tokenRevocationService = tokenRevocationService;
    this.permissionService = permissionService;
    this.auditService = auditService;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  // ─── Funcionalidades ─────────────────────────────────────────────────────

  /** A arvore que o editor mostra: o teto do dono, sem as exclusivas e sem detalhes com parametro. */
  @Transactional(readOnly = true)
  public List<FuncionalidadeResponse> funcionalidades() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Set<String> teto = acessoPorPerfil.tetoDoDono(tenantId);
    List<FuncionalidadeResponse> resultado = new ArrayList<>();
    for (ItemCatalogo item : repositorio.catalogoAtivo()) {
      if (item.route() == null || item.comParametro() || item.exclusivoDoDono()) continue;
      if (!teto.contains(item.route())) continue;
      FuncionalidadeResponse f = new FuncionalidadeResponse();
      f.id = item.id().toString();
      f.route = item.route();
      f.label = item.label();
      f.parentId = item.parentId() != null ? item.parentId().toString() : null;
      f.displayOrder = item.displayOrder();
      f.iconKey = item.iconKey();
      f.distribuivel = item.distribuivel();
      resultado.add(f);
    }
    return resultado;
  }

  // ─── Perfis ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<PerfilResumoResponse> listarPerfis() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return repositorio.listarPerfis(tenantId).stream()
        .map(
            l -> {
              PerfilResumoResponse r = new PerfilResumoResponse();
              r.id = String.valueOf(l[0]);
              r.nome = texto(l[1]);
              r.descricao = vazioViraNulo(texto(l[2]));
              r.acessoTotal = Boolean.TRUE.equals(l[3]);
              r.updatedAt = instante(l[4]);
              r.membros = numero(l[5]);
              r.funcionalidades = numero(l[6]);
              return r;
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public PerfilResponse obterPerfil(UUID perfilId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return montarPerfil(tenantId, carregarPerfil(tenantId, perfilId));
  }

  @Transactional
  public PerfilResponse criarPerfil(PerfilRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String nome = normalizarNome(request.nome);
    if (repositorio.existeNome(tenantId, nome, null)) {
      throw conflito("Ja existe um perfil com este nome.");
    }
    List<UUID> itens = validarItens(tenantId, request.itens);

    UUID id = UUID.randomUUID();
    repositorio.inserirPerfil(id, tenantId, nome, normalizarDescricao(request.descricao), false, autor());
    repositorio.substituirItens(id, itens);
    auditar(tenantId, "PERFIL_CRIADO", null, estadoDoPerfil(nome, itens));
    permissionService.limparCachePermissoesUsuario();
    return montarPerfil(tenantId, carregarPerfil(tenantId, id));
  }

  @Transactional
  public PerfilResponse atualizarPerfil(UUID perfilId, PerfilRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Object[] perfil = carregarPerfil(tenantId, perfilId);
    if (Boolean.TRUE.equals(perfil[3])) {
      throw invalido(
          "O Acesso completo acompanha o que voce tem e nao e editavel. Crie um perfil para escolher as telas.");
    }
    String nome = normalizarNome(request.nome);
    if (repositorio.existeNome(tenantId, nome, perfilId)) {
      throw conflito("Ja existe um perfil com este nome.");
    }
    List<UUID> itens = validarItens(tenantId, request.itens);
    Map<String, Object> antes = estadoDoPerfil(texto(perfil[1]), repositorio.itensDoPerfil(perfilId));

    repositorio.atualizarPerfil(perfilId, nome, normalizarDescricao(request.descricao), autor());
    repositorio.substituirItens(perfilId, itens);
    auditar(tenantId, "PERFIL_ALTERADO", antes, estadoDoPerfil(nome, itens));
    permissionService.limparCachePermissoesUsuario();
    return montarPerfil(tenantId, carregarPerfil(tenantId, perfilId));
  }

  @Transactional
  public PerfilResponse duplicarPerfil(UUID perfilId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Object[] original = carregarPerfil(tenantId, perfilId);
    List<UUID> itens =
        Boolean.TRUE.equals(original[3])
            ? idsDistribuiveis(tenantId).stream().toList()
            : repositorio.itensDoPerfil(perfilId);

    String base = texto(original[1]) + " (copia)";
    String nome = base;
    for (int n = 2; repositorio.existeNome(tenantId, nome, null); n++) {
      nome = base + " " + n;
    }
    UUID id = UUID.randomUUID();
    repositorio.inserirPerfil(id, tenantId, recortar(nome, 80), vazioViraNulo(texto(original[2])), false, autor());
    repositorio.substituirItens(id, itens);
    auditar(tenantId, "PERFIL_DUPLICADO", null, estadoDoPerfil(nome, itens));
    return montarPerfil(tenantId, carregarPerfil(tenantId, id));
  }

  /** R6: perfil com gente nao sai — a pessoa ficaria sem saber por que perdeu o acesso. */
  @Transactional
  public void excluirPerfil(UUID perfilId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Object[] perfil = carregarPerfil(tenantId, perfilId);
    int membros = repositorio.contarMembros(perfilId);
    if (membros > 0) {
      throw conflito(
          "Este perfil tem "
              + membros
              + (membros == 1 ? " pessoa" : " pessoas")
              + ". Mova para outro perfil antes de excluir.");
    }
    Map<String, Object> antes = estadoDoPerfil(texto(perfil[1]), repositorio.itensDoPerfil(perfilId));
    repositorio.excluirPerfil(perfilId);
    auditar(tenantId, "PERFIL_EXCLUIDO", antes, null);
  }

  // ─── Equipe ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<MembroResponse> listarEquipe() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return montarEquipe(tenantId);
  }

  /** D4: recepcao, financeiro — quem trabalha no salao sem ter agenda. */
  @Transactional
  public MembroResponse cadastrarMembro(NovoMembroRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String email = request.email.trim().toLowerCase(Locale.ROOT);
    if (usuarioRepository.findByEmail(email).isPresent()) {
      throw conflito("Ja existe um usuario com este e-mail.");
    }
    List<UUID> perfis = resolverPerfisParaAtribuir(tenantId, request.perfis);

    String senha = gerarSenha();
    Usuario usuario = new Usuario();
    usuario.setTenantId(tenantId);
    usuario.setName(request.nome.trim());
    usuario.setEmail(email);
    usuario.setPhone(vazioViraNulo(request.telefone == null ? null : request.telefone.trim()));
    usuario.setRole(PapelUsuario.STAFF);
    usuario.setPasswordHash(BCrypt.withDefaults().hashToString(12, senha.toCharArray()));
    usuario = usuarioRepository.saveAndFlush(usuario);

    repositorio.substituirPerfisDoUsuario(tenantId, usuario.getId(), perfis, autor());
    String nome = usuario.getName();
    depoisDoCommit(() -> credentialsEmailService.sendProfessionalAccess(email, nome, email, senha));

    Map<String, Object> depois = new LinkedHashMap<>();
    depois.put("userId", usuario.getId().toString());
    depois.put("email", email);
    depois.put("perfis", texto(perfis));
    auditar(tenantId, "MEMBRO_CADASTRADO", null, depois);
    permissionService.limparCachePermissoesUsuario();
    return membro(tenantId, usuario.getId());
  }

  /** So o membro sem agenda: o profissional e editado no cadastro de profissionais. */
  @Transactional
  public MembroResponse editarMembro(UUID userId, EdicaoDeMembroRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String papel = papelDaEquipe(tenantId, userId);
    if (!PapelUsuario.STAFF.name().equals(papel)) {
      throw invalido("Os dados de um profissional sao editados no cadastro de profissionais.");
    }
    repositorio.atualizarDadosDoMembro(
        tenantId, userId, request.nome.trim(), vazioViraNulo(request.telefone == null ? null : request.telefone.trim()));
    Map<String, Object> depois = new LinkedHashMap<>();
    depois.put("userId", userId.toString());
    depois.put("nome", request.nome.trim());
    auditar(tenantId, "MEMBRO_ALTERADO", null, depois);
    return membro(tenantId, userId);
  }

  /** D1 (varios perfis) e D5 (nenhum escolhido = Acesso completo). */
  @Transactional
  public MembroResponse atribuirPerfis(UUID userId, AtribuicaoRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    papelDaEquipe(tenantId, userId);
    if (repositorio.estaDesligado(userId)) {
      throw conflito("Esta pessoa saiu da equipe. Religue o acesso antes de atribuir perfis.");
    }
    List<UUID> perfis = resolverPerfisParaAtribuir(tenantId, request.perfis);
    List<UUID> antes = repositorio.perfisAtribuidos(tenantId, userId);

    repositorio.substituirPerfisDoUsuario(tenantId, userId, perfis, autor());
    auditar(
        tenantId,
        "PERFIS_ATRIBUIDOS",
        Map.of("userId", userId.toString(), "perfis", texto(antes)),
        Map.of("userId", userId.toString(), "perfis", texto(perfis)));
    permissionService.limparCachePermissoesUsuario();
    return membro(tenantId, userId);
  }

  @Transactional
  public MensagemResponse redefinirSenha(UUID userId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    papelDaEquipe(tenantId, userId);
    if (repositorio.estaDesligado(userId)) {
      throw conflito("Esta pessoa saiu da equipe. Religue o acesso para gerar uma nova senha.");
    }
    Usuario usuario = usuarioDoSalao(tenantId, userId);
    enviarNovaSenha(tenantId, usuario, false);
    auditar(tenantId, "SENHA_REDEFINIDA", null, Map.of("userId", userId.toString()));
    return new MensagemResponse("Senha temporaria gerada e enviada para " + usuario.getEmail() + ".");
  }

  /**
   * Tira a pessoa da equipe: perde os perfis, a senha deixa de valer e todo token emitido ate agora
   * e revogado. A linha do usuario continua, e o historico tambem.
   */
  @Transactional
  public MembroResponse desligar(UUID userId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    papelDaEquipe(tenantId, userId);
    if (userId.equals(authenticatedUser.idOuNulo())) {
      throw invalido("Voce nao pode tirar o proprio acesso.");
    }
    List<UUID> antes = repositorio.perfisAtribuidos(tenantId, userId);
    repositorio.substituirPerfisDoUsuario(tenantId, userId, List.of(), autor());
    repositorio.bloquearCredenciais(
        tenantId, userId, BCrypt.withDefaults().hashToString(12, gerarSenha().toCharArray()));
    repositorio.registrarDesligamento(tenantId, userId, autor());
    tokenRevocationService.invalidateCache(userId);
    auditar(
        tenantId,
        "MEMBRO_DESLIGADO",
        Map.of("userId", userId.toString(), "perfis", texto(antes)),
        Map.of("userId", userId.toString(), "desligado", true));
    permissionService.limparCachePermissoesUsuario();
    return membro(tenantId, userId);
  }

  /** Volta com uma senha temporaria nova, e sem perfil: o dono escolhe de novo o que ela ve. */
  @Transactional
  public MembroResponse religar(UUID userId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    papelDaEquipe(tenantId, userId);
    if (!repositorio.estaDesligado(userId)) {
      throw conflito("Esta pessoa ja faz parte da equipe.");
    }
    repositorio.removerDesligamento(userId);
    enviarNovaSenha(tenantId, usuarioDoSalao(tenantId, userId), true);
    auditar(tenantId, "MEMBRO_RELIGADO", null, Map.of("userId", userId.toString()));
    return membro(tenantId, userId);
  }

  /** "Ver o que esta pessoa acessa": a mesma conta do menu e das permissoes. */
  @Transactional(readOnly = true)
  public AcessoEfetivoResponse efetivo(UUID userId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String papel = papelDaEquipe(tenantId, userId);
    AcessoEfetivoResponse response = new AcessoEfetivoResponse();
    Optional<ResolucaoDeAcesso.AcessoEfetivo> porPerfil = acessoPorPerfil.resolver(tenantId, userId, papel);
    if (porPerfil.isPresent()) {
      response.usaPerfis = true;
      response.rotas = porPerfil.get().rotas();
      response.permissoes = porPerfil.get().permissoes();
      return response;
    }
    response.usaPerfis = false;
    response.rotas = papelFixo(papel).map(p -> menuRouteCache.getAllowedRoutes(tenantId, p)).orElse(List.of());
    response.permissoes =
        rbacAuthorizationRepository.listarPermissoesPorUsuarioETenant(tenantId, userId).stream()
            .sorted()
            .toList();
    return response;
  }

  @Transactional(readOnly = true)
  public List<HistoricoResponse> historico(Integer limite) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int tamanho = limite == null || limite <= 0 ? HISTORICO_PADRAO : Math.min(limite, HISTORICO_MAXIMO);
    return repositorio.historico(tenantId, tamanho).stream()
        .map(
            l -> {
              HistoricoResponse h = new HistoricoResponse();
              h.id = String.valueOf(l[0]);
              h.acao = texto(l[1]);
              h.autorId = l[2] != null ? l[2].toString() : null;
              h.autorNome = texto(l[3]);
              h.antes = texto(l[4]);
              h.depois = texto(l[5]);
              h.criadoEm = instante(l[6]);
              return h;
            })
        .toList();
  }

  // ─── Regras internas ─────────────────────────────────────────────────────

  private Object[] carregarPerfil(UUID tenantId, UUID perfilId) {
    return repositorio
        .buscarPerfil(tenantId, perfilId)
        .orElseThrow(() -> new ApiClientErrorException("Perfil nao encontrado.", 404));
  }

  private PerfilResponse montarPerfil(UUID tenantId, Object[] linha) {
    UUID id = PerfilAcessoRepository.uuid(linha[0]);
    PerfilResponse r = new PerfilResponse();
    r.id = id.toString();
    r.nome = texto(linha[1]);
    r.descricao = vazioViraNulo(texto(linha[2]));
    r.acessoTotal = Boolean.TRUE.equals(linha[3]);
    r.updatedAt = instante(linha[4]);
    r.membros = repositorio.contarMembros(id);

    Set<UUID> distribuiveis = idsDistribuiveis(tenantId);
    if (r.acessoTotal) {
      r.itens = distribuiveis.stream().map(UUID::toString).toList();
      return r;
    }
    List<UUID> itens = repositorio.itensDoPerfil(id);
    r.itens = itens.stream().map(UUID::toString).toList();
    r.indisponiveis =
        itens.stream().filter(item -> !distribuiveis.contains(item)).map(UUID::toString).toList();
    return r;
  }

  private Set<UUID> idsDistribuiveis(UUID tenantId) {
    return ResolucaoDeAcesso.distribuiveis(repositorio.catalogoAtivo(), acessoPorPerfil.tetoDoDono(tenantId))
        .values()
        .stream()
        .map(ItemCatalogo::id)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** R2 e R4: so o que o dono tem, sem as exclusivas e sem as que ainda nao tem backend. */
  private List<UUID> validarItens(UUID tenantId, List<String> brutos) {
    Set<UUID> itens = new LinkedHashSet<>();
    for (String bruto : brutos == null ? List.<String>of() : brutos) {
      try {
        itens.add(UUID.fromString(bruto.trim()));
      } catch (RuntimeException e) {
        throw invalido("Funcionalidade invalida: " + bruto);
      }
    }
    if (itens.isEmpty()) {
      throw invalido("Escolha ao menos uma tela para o perfil.");
    }
    Set<UUID> distribuiveis = idsDistribuiveis(tenantId);
    for (UUID item : itens) {
      if (!distribuiveis.contains(item)) {
        throw invalido("Funcionalidade nao disponivel para o seu salao.");
      }
    }
    return new ArrayList<>(itens);
  }

  private List<UUID> resolverPerfisParaAtribuir(UUID tenantId, List<String> brutos) {
    Set<UUID> perfis = new LinkedHashSet<>();
    for (String bruto : brutos == null ? List.<String>of() : brutos) {
      try {
        perfis.add(UUID.fromString(bruto.trim()));
      } catch (RuntimeException e) {
        throw new ApiClientErrorException("Perfil nao encontrado.", 404);
      }
    }
    if (perfis.isEmpty()) {
      return List.of(garantirAcessoCompleto(tenantId));
    }
    // Id de outro salao responde igual a id inexistente: nao se revela o que existe fora.
    if (repositorio.contarPerfisDoTenant(tenantId, perfis) != perfis.size()) {
      throw new ApiClientErrorException("Perfil nao encontrado.", 404);
    }
    return new ArrayList<>(perfis);
  }

  /** D5: o perfil de sistema, criado na primeira vez que alguem segue sem escolher. */
  UUID garantirAcessoCompleto(UUID tenantId) {
    Optional<UUID> existente = repositorio.perfilDeAcessoTotal(tenantId);
    if (existente.isPresent()) return existente.get();
    String nome = NOME_DO_ACESSO_COMPLETO;
    if (repositorio.existeNome(tenantId, nome, null)) nome = NOME_DO_ACESSO_COMPLETO + " (sistema)";
    UUID id = UUID.randomUUID();
    repositorio.inserirPerfil(id, tenantId, nome, DESCRICAO_DO_ACESSO_COMPLETO, true, autor());
    auditar(tenantId, "PERFIL_CRIADO", null, Map.of("nome", nome, "acessoTotal", true));
    return id;
  }

  /**
   * O papel de quem esta na equipe deste salao. Dono e administrador do sistema nao sao equipe:
   * responde 404 como para um id de outro salao (R8).
   */
  private String papelDaEquipe(UUID tenantId, UUID userId) {
    String papel =
        repositorio
            .papelDoUsuario(tenantId, userId)
            .orElseThrow(() -> new ApiClientErrorException("Pessoa nao encontrada na equipe.", 404));
    if (PapelUsuario.OWNER.name().equals(papel) || PapelUsuario.ADMIN.name().equals(papel)) {
      throw new ApiClientErrorException("Pessoa nao encontrada na equipe.", 404);
    }
    return papel;
  }

  private Usuario usuarioDoSalao(UUID tenantId, UUID userId) {
    Usuario usuario =
        usuarioRepository
            .findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new ApiClientErrorException("Pessoa nao encontrada na equipe.", 404));
    if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
      throw invalido("Esta pessoa nao tem e-mail para receber a senha.");
    }
    return usuario;
  }

  private void enviarNovaSenha(UUID tenantId, Usuario usuario, boolean primeiroAcesso) {
    String senha = gerarSenha();
    repositorio.trocarSenha(tenantId, usuario.getId(), BCrypt.withDefaults().hashToString(12, senha.toCharArray()));
    String email = usuario.getEmail();
    String nome = usuario.getName();
    depoisDoCommit(
        () -> {
          if (primeiroAcesso) credentialsEmailService.sendProfessionalAccess(email, nome, email, senha);
          else credentialsEmailService.sendTemporaryPasswordReset(email, nome, email, senha);
        });
  }

  private List<MembroResponse> montarEquipe(UUID tenantId) {
    Map<UUID, List<PerfilRef>> perfisPorPessoa = new HashMap<>();
    for (Object[] l : repositorio.perfisDaEquipe(tenantId)) {
      PerfilRef ref = new PerfilRef();
      ref.id = String.valueOf(l[1]);
      ref.nome = texto(l[2]);
      ref.acessoTotal = Boolean.TRUE.equals(l[3]);
      perfisPorPessoa.computeIfAbsent(PerfilAcessoRepository.uuid(l[0]), k -> new ArrayList<>()).add(ref);
    }
    List<MembroResponse> equipe = new ArrayList<>();
    for (Object[] l : repositorio.listarEquipe(tenantId)) {
      UUID userId = PerfilAcessoRepository.uuid(l[0]);
      MembroResponse m = new MembroResponse();
      m.userId = userId.toString();
      m.nome = texto(l[1]);
      m.email = texto(l[2]);
      m.telefone = vazioViraNulo(texto(l[3]));
      m.papel = texto(l[4]);
      m.profissionalId = l[5] != null ? l[5].toString() : null;
      m.profissionalNome = texto(l[6]);
      m.desligado = l[7] != null;
      m.perfis = perfisPorPessoa.getOrDefault(userId, List.of());
      equipe.add(m);
    }
    return equipe;
  }

  private MembroResponse membro(UUID tenantId, UUID userId) {
    return montarEquipe(tenantId).stream()
        .filter(m -> m.userId.equals(userId.toString()))
        .findFirst()
        .orElseThrow(() -> new ApiClientErrorException("Pessoa nao encontrada na equipe.", 404));
  }

  private static Optional<PapelUsuario> papelFixo(String papel) {
    try {
      return Optional.of(PapelUsuario.valueOf(papel));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private String gerarSenha() {
    for (int tentativa = 0; tentativa < 20; tentativa++) {
      StringBuilder senha = new StringBuilder(12);
      for (int i = 0; i < 12; i++) {
        senha.append(CARACTERES_DA_SENHA.charAt(ALEATORIO.nextInt(CARACTERES_DA_SENHA.length())));
      }
      if (passwordPolicyValidator.isValid(senha.toString())) return senha.toString();
    }
    throw new IllegalStateException("Nao foi possivel gerar uma senha temporaria valida.");
  }

  /** O e-mail com senha so sai se a transacao gravou: senao a pessoa receberia uma senha inutil. */
  private static void depoisDoCommit(Runnable acao) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      acao.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              acao.run();
            } catch (RuntimeException e) {
              LOG.warn("[ACESSOS] falha ao enviar e-mail de acesso", e);
            }
          }
        });
  }

  private void auditar(UUID tenantId, String acao, Map<String, ?> antes, Map<String, ?> depois) {
    repositorio.registrarAuditoria(tenantId, autor(), acao, json(antes), json(depois));
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = autor();
      command.actorRole = authenticatedUser.roleOuNulo();
      command.module = AuditConstants.Module.RBAC;
      command.action = "ACCESS_" + acao;
      command.entityType = "ACCESS_PROFILE";
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = antes;
      command.after = depois;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // A trilha central e secundaria; a auditoria_permissao ja foi gravada na transacao.
    }
  }

  private UUID autor() {
    return authenticatedUser.idOuNulo();
  }

  private static Map<String, Object> estadoDoPerfil(String nome, Collection<UUID> itens) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("nome", nome);
    estado.put("itens", texto(itens));
    return estado;
  }

  private static String normalizarNome(String nome) {
    String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
    if (limpo.isEmpty()) throw invalido("De um nome ao perfil.");
    return recortar(limpo, 80);
  }

  private static String normalizarDescricao(String descricao) {
    if (descricao == null) return null;
    String limpo = descricao.trim();
    return limpo.isEmpty() ? null : recortar(limpo, 255);
  }

  private static String recortar(String valor, int tamanho) {
    return valor.length() > tamanho ? valor.substring(0, tamanho) : valor;
  }

  private static ApiClientErrorException invalido(String mensagem) {
    return new ApiClientErrorException(mensagem, 400);
  }

  private static ApiClientErrorException conflito(String mensagem) {
    return new ApiClientErrorException(mensagem, 409);
  }

  private static List<String> texto(Collection<UUID> ids) {
    return ids.stream().map(UUID::toString).toList();
  }

  private static String texto(Object valor) {
    return valor == null ? null : valor.toString();
  }

  private static String vazioViraNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor;
  }

  private static int numero(Object valor) {
    return valor instanceof Number n ? n.intValue() : 0;
  }

  private static String instante(Object valor) {
    if (valor == null) return null;
    if (valor instanceof Instant i) return i.toString();
    if (valor instanceof java.sql.Timestamp t) return t.toInstant().toString();
    if (valor instanceof java.time.OffsetDateTime o) return o.toInstant().toString();
    return valor.toString();
  }

  /** JSON pequeno e previsivel para a auditoria — so mapas de texto, numero, booleano e listas. */
  static String json(Map<String, ?> mapa) {
    if (mapa == null) return "null";
    StringBuilder sb = new StringBuilder("{");
    boolean primeiro = true;
    for (Map.Entry<String, ?> entrada : mapa.entrySet()) {
      if (!primeiro) sb.append(',');
      primeiro = false;
      sb.append(jsonValor(entrada.getKey())).append(':').append(jsonValor(entrada.getValue()));
    }
    return sb.append('}').toString();
  }

  private static String jsonValor(Object valor) {
    if (valor == null) return "null";
    if (valor instanceof Boolean || valor instanceof Number) return valor.toString();
    if (valor instanceof Collection<?> lista) {
      return lista.stream().map(ServicoPerfisDeAcesso::jsonValor).collect(Collectors.joining(",", "[", "]"));
    }
    StringBuilder sb = new StringBuilder("\"");
    for (char c : valor.toString().toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
          else sb.append(c);
        }
      }
    }
    return sb.append('"').toString();
  }
}
