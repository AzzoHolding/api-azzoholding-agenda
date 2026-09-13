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

  @Test
  void distribuiveisTiramExclusivasEmBreveEDetalhes() {
    assertThat(ResolucaoDeAcesso.distribuiveis(CATALOGO, TETO).keySet())
        .contains("/agenda", "/estoque")
        .doesNotContain("/clientes/:id", "/configuracoes/acessos", "/fiscal");
  }
}
