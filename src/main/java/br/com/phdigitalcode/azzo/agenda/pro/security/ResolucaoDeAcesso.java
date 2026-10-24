package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A regra dos perfis de acesso, sem banco nem Spring — por isso testavel de ponta a ponta.
 *
 * <p><b>Acesso efetivo = funcionalidades dos perfis ∩ teto do dono.</b> O teto (o que o dono recebe
 * no salao) e recalculado sempre e nunca fica gravado no perfil: se o salao perde uma tela (plano,
 * fiscal, catalogo), quem a tinha no perfil perde junto, sem ninguem editar nada.
 *
 * <p>Ver {@code docs/ESPEC_PERFIS_DE_ACESSO.md}.
 */
public final class ResolucaoDeAcesso {

  /** O que toda pessoa com perfil ve, independente do perfil: e dela, nao do salao. */
  static final Set<String> ROTAS_PESSOAIS = Set.of("/perfil-usuario", "/notificacoes", "/sugestoes");

  /** A producao do proprio profissional existe so para quem atende. */
  static final String ROTA_MINHA_PRODUCAO = "/minha-producao";

  /** O sino do cabecalho le notificacoes em todas as telas. */
  public static final Set<String> PERMISSOES_PESSOAIS = Set.of("notification:read");

  private static final String RAIZ_DE_RELATORIOS = "/relatorio";

  private ResolucaoDeAcesso() {}

  /**
   * Uma linha do catalogo {@code item_menu}.
   *
   * @param acompanhaRota {@code item_menu.acompanha_rota}: o item nao e escolhido, vem junto com
   *     essa rota (V128 — o fiscal, espalhado em varias rotas, e UMA escolha)
   */
  public record ItemCatalogo(
      UUID id,
      String route,
      String label,
      UUID parentId,
      int displayOrder,
      String iconKey,
      boolean sidebarVisible,
      boolean exclusivoDoDono,
      boolean distribuivel,
      String acompanhaRota) {

    public ItemCatalogo(
        UUID id,
        String route,
        String label,
        UUID parentId,
        int displayOrder,
        String iconKey,
        boolean sidebarVisible,
        boolean exclusivoDoDono,
        boolean distribuivel) {
      this(id, route, label, parentId, displayOrder, iconKey, sidebarVisible, exclusivoDoDono, distribuivel, null);
    }

    /** Vem junto com outra tela em vez de ser escolhido. */
    public boolean acompanha() {
      return acompanhaRota != null && !acompanhaRota.isBlank();
    }

    /** {@code /clientes/:id} e o detalhe de {@code /clientes}: vem junto, nao e escolhido. */
    public boolean comParametro() {
      return route != null && route.contains("/:");
    }

    public String rotaBase() {
      int indice = route.indexOf("/:");
      return indice < 0 ? route : route.substring(0, indice);
    }
  }

  public record AcessoEfetivo(List<String> rotas, List<String> permissoes) {}

  /**
   * O que o dono pode distribuir: esta no teto, nao e exclusivo dele, ja tem backend por permissao
   * e nao e detalhe com parametro nem acompanha outra tela.
   */
  public static Map<String, ItemCatalogo> distribuiveis(Collection<ItemCatalogo> catalogo, Set<String> teto) {
    Map<String, ItemCatalogo> resultado = new LinkedHashMap<>();
    for (ItemCatalogo item : catalogo) {
      if (item.route() == null || item.comParametro() || item.acompanha()) continue;
      if (item.exclusivoDoDono() || !item.distribuivel()) continue;
      if (!teto.contains(item.route())) continue;
      resultado.put(item.route(), item);
    }
    return resultado;
  }

  /**
   * As rotas que a pessoa enxerga.
   *
   * @param rotasDosPerfis rotas gravadas nos perfis dela (a uniao, decisao D1)
   * @param acessoTotal algum dos perfis e o "Acesso completo" (decisao D5)
   * @param papel papel fixo do usuario; decide as rotas pessoais
   */
  public static Set<String> rotasEfetivas(
      Collection<ItemCatalogo> catalogo,
      Set<String> teto,
      Set<String> rotasDosPerfis,
      boolean acessoTotal,
      String papel) {
    Map<String, ItemCatalogo> distribuiveis = distribuiveis(catalogo, teto);
    Set<String> liberadas = new TreeSet<>();
    for (String rota : distribuiveis.keySet()) {
      if (acessoTotal || rotasDosPerfis.contains(rota)) liberadas.add(rota);
    }

    // Itens que acompanham outra tela (item_menu.acompanha_rota) vem junto com ela. Antes dos
    // detalhes com parametro: /fiscal/nfse/:id segue /fiscal/nfse, que acompanha /fiscal.
    for (ItemCatalogo item : catalogo) {
      if (item.route() == null || !item.acompanha()) continue;
      if (item.exclusivoDoDono() || !item.distribuivel() || !teto.contains(item.route())) continue;
      if (liberadas.contains(item.acompanhaRota())) liberadas.add(item.route());
    }

    // Detalhes com parametro seguem a tela de origem, se o dono tambem os tem.
    for (ItemCatalogo item : catalogo) {
      if (item.route() == null || !item.comParametro()) continue;
      if (item.exclusivoDoDono() || !item.distribuivel() || !teto.contains(item.route())) continue;
      if (liberadas.contains(item.rotaBase())) liberadas.add(item.route());
    }

    // Mesma regra do MenuRouteCache: quem ve um relatorio ve o indice deles.
    if (!liberadas.contains(RAIZ_DE_RELATORIOS)
        && teto.contains(RAIZ_DE_RELATORIOS)
        && liberadas.stream().anyMatch(rota -> rota.startsWith(RAIZ_DE_RELATORIOS + "/"))) {
      liberadas.add(RAIZ_DE_RELATORIOS);
    }

    liberadas.addAll(rotasPessoais(papel));
    return liberadas;
  }

  public static Set<String> rotasPessoais(String papel) {
    Set<String> rotas = new TreeSet<>(ROTAS_PESSOAIS);
    if ("PROFESSIONAL".equals(papel)) rotas.add(ROTA_MINHA_PRODUCAO);
    return rotas;
  }
}
