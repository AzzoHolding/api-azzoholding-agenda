package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.security.ResolucaoDeAcesso.ItemCatalogo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Acesso a {@code perfil_acesso}, {@code perfil_acesso_item}, {@code usuario_perfil_acesso},
 * {@code equipe_desligamento} e {@code auditoria_permissao}. SQL nativo, no mesmo estilo de
 * {@link MenuPermissionRepository}: sao tabelas de juncao e leitura agregada, sem ciclo de vida de
 * entidade que justifique um mapeamento JPA.
 *
 * <p>Toda consulta que recebe {@code tenantId} filtra por ele — o id vindo da URL nunca basta.
 */
@Repository
@SuppressWarnings("unchecked")
public class PerfilAcessoRepository {

  @PersistenceContext private EntityManager entityManager;

  // ─── Resolucao ───────────────────────────────────────────────────────────

  /** {@code [perfilId, acessoTotal]} dos perfis da pessoa no salao. */
  public List<Object[]> perfisDoUsuario(UUID tenantId, UUID userId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT p.id, p.acesso_total
            FROM usuario_perfil_acesso u
            JOIN perfil_acesso p ON p.id = u.perfil_id AND p.tenant_id = u.tenant_id
            WHERE u.tenant_id = :tenantId AND u.user_id = :userId
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("userId", userId)
        .getResultList();
  }

  public List<ItemCatalogo> catalogoAtivo() {
    List<Object[]> linhas =
        entityManager
            .createNativeQuery(
                """
                SELECT id, route, label, parent_item_menu_id, display_order, icon_key,
                       sidebar_visible, exclusivo_do_dono, distribuivel
                FROM item_menu
                WHERE is_active = TRUE
                ORDER BY display_order, label
                """)
            .getResultList();
    return linhas.stream()
        .map(
            l ->
                new ItemCatalogo(
                    uuid(l[0]),
                    texto(l[1]),
                    texto(l[2]),
                    uuid(l[3]),
                    l[4] instanceof Number n ? n.intValue() : 0,
                    texto(l[5]),
                    booleano(l[6]),
                    booleano(l[7]),
                    booleano(l[8])))
        .toList();
  }

  public List<String> rotasDosPerfis(Collection<UUID> perfilIds) {
    if (perfilIds.isEmpty()) return List.of();
    return entityManager
        .createNativeQuery(
            """
            SELECT DISTINCT im.route
            FROM perfil_acesso_item i
            JOIN item_menu im ON im.id = i.item_menu_id
            WHERE i.perfil_id IN (:perfis) AND im.is_active = TRUE
            """)
        .setParameter("perfis", perfilIds)
        .getResultList();
  }

  public List<String> permissoesDasRotas(Collection<String> rotas) {
    if (rotas.isEmpty()) return List.of();
    return entityManager
        .createNativeQuery(
            """
            SELECT DISTINCT imp.permission_code
            FROM item_menu_permissao imp
            JOIN item_menu im ON im.id = imp.item_menu_id
            WHERE im.route IN (:rotas)
            """)
        .setParameter("rotas", rotas)
        .getResultList();
  }

  // ─── Perfis ──────────────────────────────────────────────────────────────

  /** {@code [id, nome, descricao, acessoTotal, updatedAt, membros, funcionalidades]}. */
  public List<Object[]> listarPerfis(UUID tenantId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT p.id, p.nome, p.descricao, p.acesso_total, p.updated_at,
                   (SELECT COUNT(*) FROM usuario_perfil_acesso u WHERE u.perfil_id = p.id),
                   (SELECT COUNT(*) FROM perfil_acesso_item i WHERE i.perfil_id = p.id)
            FROM perfil_acesso p
            WHERE p.tenant_id = :tenantId
            ORDER BY p.acesso_total DESC, lower(p.nome)
            """)
        .setParameter("tenantId", tenantId)
        .getResultList();
  }

  /** {@code [id, nome, descricao, acessoTotal, updatedAt]}. */
  public Optional<Object[]> buscarPerfil(UUID tenantId, UUID perfilId) {
    List<Object[]> linhas =
        entityManager
            .createNativeQuery(
                """
                SELECT id, nome, descricao, acesso_total, updated_at
                FROM perfil_acesso
                WHERE tenant_id = :tenantId AND id = :id
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("id", perfilId)
            .getResultList();
    return linhas.stream().findFirst();
  }

  public List<UUID> itensDoPerfil(UUID perfilId) {
    List<Object> linhas =
        entityManager
            .createNativeQuery("SELECT item_menu_id FROM perfil_acesso_item WHERE perfil_id = :id")
            .setParameter("id", perfilId)
            .getResultList();
    return linhas.stream().map(PerfilAcessoRepository::uuid).toList();
  }

  public boolean existeNome(UUID tenantId, String nome, UUID excetoId) {
    Number total =
        (Number)
            entityManager
                .createNativeQuery(
                    """
                    SELECT COUNT(*) FROM perfil_acesso
                    WHERE tenant_id = :tenantId AND lower(nome) = lower(:nome)
                      AND (CAST(:exceto AS uuid) IS NULL OR id <> CAST(:exceto AS uuid))
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("nome", nome)
                .setParameter("exceto", excetoId != null ? excetoId.toString() : null)
                .getSingleResult();
    return total.longValue() > 0;
  }

  public void inserirPerfil(
      UUID id, UUID tenantId, String nome, String descricao, boolean acessoTotal, UUID autor) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO perfil_acesso (id, tenant_id, nome, descricao, acesso_total, created_at, updated_at, updated_by)
            VALUES (:id, :tenantId, :nome, :descricao, :total, NOW(), NOW(), CAST(:autor AS uuid))
            """)
        .setParameter("id", id)
        .setParameter("tenantId", tenantId)
        .setParameter("nome", nome)
        .setParameter("descricao", descricao == null ? "" : descricao)
        .setParameter("total", acessoTotal)
        .setParameter("autor", autor != null ? autor.toString() : null)
        .executeUpdate();
  }

  public void atualizarPerfil(UUID id, String nome, String descricao, UUID autor) {
    entityManager
        .createNativeQuery(
            """
            UPDATE perfil_acesso
            SET nome = :nome, descricao = :descricao, updated_at = NOW(), updated_by = CAST(:autor AS uuid)
            WHERE id = :id
            """)
        .setParameter("id", id)
        .setParameter("nome", nome)
        .setParameter("descricao", descricao == null ? "" : descricao)
        .setParameter("autor", autor != null ? autor.toString() : null)
        .executeUpdate();
  }

  public void substituirItens(UUID perfilId, Collection<UUID> itens) {
    entityManager
        .createNativeQuery("DELETE FROM perfil_acesso_item WHERE perfil_id = :id")
        .setParameter("id", perfilId)
        .executeUpdate();
    for (UUID item : itens) {
      entityManager
          .createNativeQuery(
              "INSERT INTO perfil_acesso_item (perfil_id, item_menu_id) VALUES (:perfil, :item)")
          .setParameter("perfil", perfilId)
          .setParameter("item", item)
          .executeUpdate();
    }
  }

  public void excluirPerfil(UUID perfilId) {
    entityManager
        .createNativeQuery("DELETE FROM perfil_acesso WHERE id = :id")
        .setParameter("id", perfilId)
        .executeUpdate();
  }

  public int contarMembros(UUID perfilId) {
    Number total =
        (Number)
            entityManager
                .createNativeQuery("SELECT COUNT(*) FROM usuario_perfil_acesso WHERE perfil_id = :id")
                .setParameter("id", perfilId)
                .getSingleResult();
    return total.intValue();
  }

  public Optional<UUID> perfilDeAcessoTotal(UUID tenantId) {
    List<Object> linhas =
        entityManager
            .createNativeQuery(
                "SELECT id FROM perfil_acesso WHERE tenant_id = :tenantId AND acesso_total = TRUE")
            .setParameter("tenantId", tenantId)
            .getResultList();
    return linhas.stream().findFirst().map(PerfilAcessoRepository::uuid);
  }

  /** Quantos dos ids sao perfis deste salao — para recusar id de outro salao sem revela-lo. */
  public int contarPerfisDoTenant(UUID tenantId, Collection<UUID> perfilIds) {
    if (perfilIds.isEmpty()) return 0;
    Number total =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT COUNT(*) FROM perfil_acesso WHERE tenant_id = :tenantId AND id IN (:ids)")
                .setParameter("tenantId", tenantId)
                .setParameter("ids", perfilIds)
                .getSingleResult();
    return total.intValue();
  }

  // ─── Equipe ──────────────────────────────────────────────────────────────

  /**
   * {@code [userId, nome, email, telefone, papel, profissionalId, profissionalNome, desligadoEm]} de
   * quem tem login no salao e nao e dono nem administrador do sistema.
   */
  public List<Object[]> listarEquipe(UUID tenantId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT u.id, u.name, u.email, u.phone, u.role, pr.id, pr.name, d.desligado_em
            FROM users u
            LEFT JOIN professionals pr ON pr.user_id = u.id AND pr.tenant_id = u.tenant_id
            LEFT JOIN equipe_desligamento d ON d.user_id = u.id
            WHERE u.tenant_id = :tenantId AND u.role NOT IN ('OWNER', 'ADMIN')
            ORDER BY lower(u.name)
            """)
        .setParameter("tenantId", tenantId)
        .getResultList();
  }

  /** {@code [userId, perfilId, nome, acessoTotal]} de toda a equipe do salao. */
  public List<Object[]> perfisDaEquipe(UUID tenantId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT u.user_id, p.id, p.nome, p.acesso_total
            FROM usuario_perfil_acesso u
            JOIN perfil_acesso p ON p.id = u.perfil_id
            WHERE u.tenant_id = :tenantId
            ORDER BY lower(p.nome)
            """)
        .setParameter("tenantId", tenantId)
        .getResultList();
  }

  /** O papel da pessoa, se ela for deste salao. */
  public Optional<String> papelDoUsuario(UUID tenantId, UUID userId) {
    List<Object> linhas =
        entityManager
            .createNativeQuery("SELECT role FROM users WHERE tenant_id = :tenantId AND id = :id")
            .setParameter("tenantId", tenantId)
            .setParameter("id", userId)
            .getResultList();
    return linhas.stream().findFirst().map(String::valueOf);
  }

  public List<UUID> perfisAtribuidos(UUID tenantId, UUID userId) {
    return perfisDoUsuario(tenantId, userId).stream().map(l -> uuid(l[0])).toList();
  }

  public List<UUID> membrosDoPerfil(UUID perfilId) {
    List<Object> linhas =
        entityManager
            .createNativeQuery("SELECT user_id FROM usuario_perfil_acesso WHERE perfil_id = :id")
            .setParameter("id", perfilId)
            .getResultList();
    return linhas.stream().map(PerfilAcessoRepository::uuid).toList();
  }

  public void substituirPerfisDoUsuario(
      UUID tenantId, UUID userId, Collection<UUID> perfilIds, UUID autor) {
    entityManager
        .createNativeQuery("DELETE FROM usuario_perfil_acesso WHERE tenant_id = :tenantId AND user_id = :userId")
        .setParameter("tenantId", tenantId)
        .setParameter("userId", userId)
        .executeUpdate();
    for (UUID perfilId : perfilIds) {
      entityManager
          .createNativeQuery(
              """
              INSERT INTO usuario_perfil_acesso (user_id, perfil_id, tenant_id, atribuido_por, atribuido_em)
              VALUES (:userId, :perfilId, :tenantId, CAST(:autor AS uuid), NOW())
              """)
          .setParameter("userId", userId)
          .setParameter("perfilId", perfilId)
          .setParameter("tenantId", tenantId)
          .setParameter("autor", autor != null ? autor.toString() : null)
          .executeUpdate();
    }
  }

  public void atualizarDadosDoMembro(UUID tenantId, UUID userId, String nome, String telefone) {
    entityManager
        .createNativeQuery("UPDATE users SET name = :nome, phone = :telefone WHERE tenant_id = :tenantId AND id = :id")
        .setParameter("nome", nome)
        .setParameter("telefone", telefone == null ? "" : telefone)
        .setParameter("tenantId", tenantId)
        .setParameter("id", userId)
        .executeUpdate();
  }

  /** Troca a senha e revoga todo token emitido ate agora: a pessoa sai na proxima requisicao. */
  public void bloquearCredenciais(UUID tenantId, UUID userId, String novoHash) {
    entityManager
        .createNativeQuery(
            """
            UPDATE users SET password_hash = :hash, tokens_revoked_before = :agora
            WHERE tenant_id = :tenantId AND id = :id
            """)
        .setParameter("hash", novoHash)
        .setParameter("agora", Instant.now())
        .setParameter("tenantId", tenantId)
        .setParameter("id", userId)
        .executeUpdate();
  }

  public void trocarSenha(UUID tenantId, UUID userId, String novoHash) {
    entityManager
        .createNativeQuery("UPDATE users SET password_hash = :hash WHERE tenant_id = :tenantId AND id = :id")
        .setParameter("hash", novoHash)
        .setParameter("tenantId", tenantId)
        .setParameter("id", userId)
        .executeUpdate();
  }

  public boolean estaDesligado(UUID userId) {
    Number total =
        (Number)
            entityManager
                .createNativeQuery("SELECT COUNT(*) FROM equipe_desligamento WHERE user_id = :id")
                .setParameter("id", userId)
                .getSingleResult();
    return total.longValue() > 0;
  }

  public void registrarDesligamento(UUID tenantId, UUID userId, UUID autor) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO equipe_desligamento (user_id, tenant_id, desligado_em, desligado_por)
            VALUES (:userId, :tenantId, NOW(), CAST(:autor AS uuid))
            ON CONFLICT (user_id) DO UPDATE SET desligado_em = NOW(), desligado_por = EXCLUDED.desligado_por
            """)
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .setParameter("autor", autor != null ? autor.toString() : null)
        .executeUpdate();
  }

  public void removerDesligamento(UUID userId) {
    entityManager
        .createNativeQuery("DELETE FROM equipe_desligamento WHERE user_id = :id")
        .setParameter("id", userId)
        .executeUpdate();
  }

  // ─── Auditoria ───────────────────────────────────────────────────────────

  /** {@code antesJson}/{@code depoisJson} sao JSON prontos; "null" grava o nulo do JSON. */
  public void registrarAuditoria(
      UUID tenantId, UUID autor, String acao, String antesJson, String depoisJson) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO auditoria_permissao (id, tenant_id, changed_by, action, before_data, after_data, created_at)
            VALUES (gen_random_uuid(), :tenantId, CAST(:autor AS uuid), :acao,
                    CAST(:antes AS jsonb), CAST(:depois AS jsonb), NOW())
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("autor", autor != null ? autor.toString() : null)
        .setParameter("acao", acao)
        .setParameter("antes", antesJson == null ? "null" : antesJson)
        .setParameter("depois", depoisJson == null ? "null" : depoisJson)
        .executeUpdate();
  }

  /** {@code [id, acao, autorId, autorNome, antes, depois, criadoEm]}, mais recente primeiro. */
  public List<Object[]> historico(UUID tenantId, int limite) {
    return entityManager
        .createNativeQuery(
            """
            SELECT a.id, a.action, a.changed_by, u.name, CAST(a.before_data AS text),
                   CAST(a.after_data AS text), a.created_at
            FROM auditoria_permissao a
            LEFT JOIN users u ON u.id = a.changed_by
            WHERE a.tenant_id = :tenantId
            ORDER BY a.created_at DESC
            LIMIT :limite
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("limite", limite)
        .getResultList();
  }

  // ─── Conversao ───────────────────────────────────────────────────────────

  public static UUID uuid(Object valor) {
    if (valor == null) return null;
    if (valor instanceof UUID id) return id;
    return UUID.fromString(valor.toString());
  }

  private static String texto(Object valor) {
    return valor == null ? null : valor.toString();
  }

  private static boolean booleano(Object valor) {
    return valor instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(valor));
  }
}
