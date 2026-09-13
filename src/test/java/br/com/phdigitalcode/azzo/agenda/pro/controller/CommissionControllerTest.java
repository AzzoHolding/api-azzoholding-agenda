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
 * O gate das comissoes (V127): leitura com {@code commission:view}, escrita com
 * {@code commission:manage}, e o dono sempre pelo papel. A avaliacao real das expressoes esta em
 * {@code GatesPorPerfilDeAcessoTest}.
 */
class CommissionControllerTest {

  private static final Set<String> ESCRITAS = Set.of(
      "createRuleSet", "updateRuleSet", "setRuleSetActive", "closeCycle", "payCycle", "createAdjustment");

  @Test
  void classeLiberaODonoOuQuemTemCommissionView() {
    assertThat(CommissionController.class.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasRole('OWNER') or @permissionService.possuiPermissao('commission:view')");
  }

  @Test
  void todaEscritaExigeCommissionManageETodoOResto() {
    List<Method> endpoints = endpoints();
    assertThat(endpoints.stream().map(Method::getName)).containsAll(ESCRITAS);

    for (Method metodo : endpoints) {
      PreAuthorize doMetodo = metodo.getAnnotation(PreAuthorize.class);
      if (ESCRITAS.contains(metodo.getName())) {
        assertThat(doMetodo).as(metodo.getName()).isNotNull();
        assertThat(doMetodo.value())
            .as(metodo.getName())
            .isEqualTo("hasRole('OWNER') or @permissionService.possuiPermissao('commission:manage')");
      } else {
        // Endpoint novo que escreve e cai aqui sem gate proprio ficaria com a regra de LEITURA.
        assertThat(metodo.isAnnotationPresent(GetMapping.class)).as(metodo.getName()).isTrue();
        assertThat(doMetodo).as(metodo.getName()).isNull();
      }
    }
  }

  private static List<Method> endpoints() {
    return Arrays.stream(CommissionController.class.getDeclaredMethods())
        .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
        .toList();
  }
}
