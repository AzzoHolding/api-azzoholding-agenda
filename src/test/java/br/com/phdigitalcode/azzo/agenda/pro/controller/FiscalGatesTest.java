package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * As tres faixas do fiscal (V128): leitura com {@code fiscal:view} (gate da classe), operacao com
 * {@code fiscal:manage} e escrita de configuracao so do dono. Um endpoint novo que escreva e caia
 * na faixa de leitura sem ninguem decidir quebra este teste. A avaliacao real das expressoes esta
 * em {@code GatesPorPerfilDeAcessoTest}.
 */
class FiscalGatesTest {

  private static final String LEITURA = "hasRole('OWNER') or @permissionService.possuiPermissao('fiscal:view')";
  private static final String OPERACAO = "hasRole('OWNER') or @permissionService.possuiPermissao('fiscal:manage')";
  private static final String CONFIGURACAO = "hasRole('OWNER')";

  /** POST que so LE: pede a geracao assincrona do PDF de uma nota que ja existe. */
  private static final Set<String> POST_DE_LEITURA = Set.of("solicitarGeracaoDanfe", "solicitarGeracaoPdf");

  @Test
  void asDuasClassesLeemComFiscalView() {
    assertThat(FiscalController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo(LEITURA);
    assertThat(NfseController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo(LEITURA);
  }

  @Test
  void fiscalController() {
    verificar(
        FiscalController.class,
        Set.of("criarInvoice", "atualizarInvoice", "cancelar", "autorizar", "reprocessarAutorizacao", "recalcular"),
        Set.of("atualizarTaxConfig", "salvarCertificado", "ativarCertificado", "removerCertificado"));
  }

  @Test
  void nfseController() {
    verificar(
        NfseController.class,
        Set.of("criarRascunho", "atualizarRascunho", "autorizar", "cancelar", "createUnlockSession", "revokeUnlockSession"),
        Set.of("salvarConfig", "salvarProviderCapabilities"));
  }

  private static void verificar(Class<?> controller, Set<String> operacao, Set<String> configuracao) {
    List<Method> endpoints = Arrays.stream(controller.getDeclaredMethods())
        .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
        .toList();
    assertThat(endpoints.stream().map(Method::getName)).containsAll(operacao).containsAll(configuracao);

    for (Method metodo : endpoints) {
      String nome = metodo.getName();
      PreAuthorize gate = metodo.getAnnotation(PreAuthorize.class);
      if (operacao.contains(nome)) {
        assertThat(gate).as(nome).isNotNull();
        assertThat(gate.value()).as(nome).isEqualTo(OPERACAO);
      } else if (configuracao.contains(nome)) {
        assertThat(gate).as(nome).isNotNull();
        assertThat(gate.value()).as(nome).isEqualTo(CONFIGURACAO);
      } else {
        assertThat(gate).as(nome).isNull();
        assertThat(metodo.isAnnotationPresent(GetMapping.class) || POST_DE_LEITURA.contains(nome))
            .as(nome + " escreve sem gate proprio")
            .isTrue();
      }
    }
  }
}
