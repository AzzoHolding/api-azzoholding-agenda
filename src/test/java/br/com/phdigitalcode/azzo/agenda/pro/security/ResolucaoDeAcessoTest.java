package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.security.ResolucaoDeAcesso.ItemCatalogo;

/** A regra dos perfis de acesso — docs/ESPEC_PERFIS_DE_ACESSO.md. */
class ResolucaoDeAcessoTest {

  private static ItemCatalogo item(String rota) {
    return new ItemCatalogo(UUID.randomUUID(), rota, rota, null, 0, null, true, false, true);
  }

  private static ItemCatalogo exclusivo(String rota) {
    return new ItemCatalogo(UUID.randomUUID(), rota, rota, null, 0, null, true, true, true);
  }

  private static ItemCatalogo emBreve(String rota) {
    return new ItemCatalogo(UUID.randomUUID(), rota, rota, null, 0, null, true, false, false);
  }

  private static final List<ItemCatalogo> CATALOGO =
      List.of(
          item("/agenda"),
          item("/clientes"),
          item("/clientes/:id"),
          item("/estoque"),
          item("/relatorio"),
          item("/relatorio/vendas"),
          item("/perfil-usuario"),
          item("/notificacoes"),
          item("/sugestoes"),
          exclusivo("/configuracoes/acessos"),
          exclusivo("/financeiro/licenca"),
          emBreve("/fiscal"));

  private static final Set<String> TETO =
      Set.of(
          "/agenda", "/clientes", "/clientes/:id", "/estoque", "/relatorio", "/relatorio/vendas",
          "/perfil-usuario", "/notificacoes", "/sugestoes", "/configuracoes/acessos",
          "/financeiro/licenca", "/fiscal");

  @Test
  void perfilConcedeSoOQueEscolheuMaisAsRotasPessoais() {
    Set<String> rotas =
        ResolucaoDeAcesso.rotasEfetivas(CATALOGO, TETO, Set.of("/agenda"), false, "STAFF");

    assertThat(rotas).containsExactlyInAnyOrder("/agenda", "/perfil-usuario", "/notificacoes", "/sugestoes");
  }

  /** R2: o que o dono nao tem, ninguem recebe — mesmo que esteja gravado no perfil. */
  @Test
  void nadaPassaDoTetoDoDono() {
    Set<String> tetoSemEstoque = new java.util.HashSet<>(TETO);
    tetoSemEstoque.remove("/estoque");

    Set<String> rotas =
        ResolucaoDeAcesso.rotasEfetivas(CATALOGO, tetoSemEstoque, Set.of("/estoque", "/agenda"), false, "STAFF");

    assertThat(rotas).contains("/agenda").doesNotContain("/estoque");
  }

  /** R4 e fase 2: exclusivas e "em breve" nunca saem, nem gravadas no perfil. */
  @Test
  void exclusivasEEmBreveNuncaSaem() {
    Set<String> rotas =
        ResolucaoDeAcesso.rotasEfetivas(
            CATALOGO, TETO, Set.of("/configuracoes/acessos", "/financeiro/licenca", "/fiscal"), false, "STAFF");

    assertThat(rotas).doesNotContain("/configuracoes/acessos", "/financeiro/licenca", "/fiscal");
  }

  /** D5: o Acesso completo acompanha o teto, menos exclusivas e em breve. */
  @Test
  void acessoCompletoAcompanhaOTeto() {
    Set<String> rotas = ResolucaoDeAcesso.rotasEfetivas(CATALOGO, TETO, Set.of(), true, "PROFESSIONAL");

    assertThat(rotas)
        .contains("/agenda", "/clientes", "/clientes/:id", "/estoque", "/relatorio", "/relatorio/vendas")
        .contains("/minha-producao")
        .doesNotContain("/configuracoes/acessos", "/financeiro/licenca", "/fiscal");
  }

  @Test
  void detalheComParametroSegueATelaDeOrigem() {
    assertThat(ResolucaoDeAcesso.rotasEfetivas(CATALOGO, TETO, Set.of("/clientes"), false, "STAFF"))
        .contains("/clientes", "/clientes/:id");
    assertThat(ResolucaoDeAcesso.rotasEfetivas(CATALOGO, TETO, Set.of("/agenda"), false, "STAFF"))
        .doesNotContain("/clientes/:id");
  }

  @Test
  void quemVeUmRelatorioVeOIndice() {
    assertThat(ResolucaoDeAcesso.rotasEfetivas(CATALOGO, TETO, Set.of("/relatorio/vendas"), false, "STAFF"))
        .contains("/relatorio", "/relatorio/vendas");
  }

  @Test
  void minhaProducaoSoParaQuemAtende() {
    assertThat(ResolucaoDeAcesso.rotasPessoais("PROFESSIONAL")).contains("/minha-producao");
    assertThat(ResolucaoDeAcesso.rotasPessoais("STAFF")).doesNotContain("/minha-producao");
  }

  private static ItemCatalogo acompanha(String rota, String alvo) {
    return new ItemCatalogo(UUID.randomUUID(), rota, rota, null, 0, null, true, false, true, alvo);
  }

  /** V128: o fiscal esta em varias rotas e e UMA escolha — as outras acompanham /fiscal. */
  @Test
  void quemAcompanhaVemJuntoENaoEEscolhido() {
    List<ItemCatalogo> catalogo =
        List.of(
            item("/fiscal"),
            acompanha("/fiscal/nfse", "/fiscal"),
            acompanha("/emitir-nota", "/fiscal"),
            item("/fiscal/nfse/:id"),
            item("/agenda"));
    Set<String> teto = Set.of("/fiscal", "/fiscal/nfse", "/emitir-nota", "/fiscal/nfse/:id", "/agenda");

    assertThat(ResolucaoDeAcesso.distribuiveis(catalogo, teto).keySet())
        .containsExactlyInAnyOrder("/fiscal", "/agenda");
    // O detalhe com parametro segue /fiscal/nfse, que acompanha /fiscal.
    assertThat(ResolucaoDeAcesso.rotasEfetivas(catalogo, teto, Set.of("/fiscal"), false, "STAFF"))
        .contains("/fiscal", "/fiscal/nfse", "/emitir-nota", "/fiscal/nfse/:id");
    // Gravado sozinho no perfil, quem acompanha nao entra: nao e escolha.
    assertThat(ResolucaoDeAcesso.rotasEfetivas(catalogo, teto, Set.of("/fiscal/nfse", "/agenda"), false, "STAFF"))
        .contains("/agenda")
        .doesNotContain("/fiscal/nfse", "/fiscal/nfse/:id");
    // Fora do teto do dono, nao vem nem junto.
    assertThat(ResolucaoDeAcesso.rotasEfetivas(catalogo, Set.of("/fiscal"), Set.of("/fiscal"), false, "STAFF"))
        .contains("/fiscal")
        .doesNotContain("/fiscal/nfse", "/emitir-nota");
  }

  @Test
  void distribuiveisTiramExclusivasEmBreveEDetalhes() {
    assertThat(ResolucaoDeAcesso.distribuiveis(CATALOGO, TETO).keySet())
        .contains("/agenda", "/estoque")
        .doesNotContain("/clientes/:id", "/configuracoes/acessos", "/fiscal");
  }
}
