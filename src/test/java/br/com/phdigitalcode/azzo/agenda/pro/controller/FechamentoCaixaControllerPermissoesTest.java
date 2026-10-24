package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;

class FechamentoCaixaControllerPermissoesTest {

  /**
   * O caixa tem permissoes proprias (V135): o perfil que so opera o caixa nao pode, por tabela,
   * lancar no financeiro — o que acontecia quando o caixa exigia finance:manage.
   */
  @Test
  void caixaExigeSoAsPermissoesDoCaixa() {
    Method[] endpoints =
        Arrays.stream(FechamentoCaixaController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(RequiresPermission.class))
            .toArray(Method[]::new);

    assertThat(endpoints).isNotEmpty();
    assertThat(endpoints)
        .extracting(m -> m.getAnnotation(RequiresPermission.class).value())
        .allMatch(codigo -> codigo.startsWith("cash:"));
  }
}
