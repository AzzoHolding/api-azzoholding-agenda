package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ResumoFinanceiroResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFinanceiro;

class FinanceiroControllerResumoTest {

  @Test
  @DisplayName("O resumo diz se quem pede pode gerenciar: a tela esconde o que terminaria em 403")
  void resumoInformaSePodeGerenciar() {
    ServicoFinanceiro servico = mock(ServicoFinanceiro.class);
    PermissionService permissoes = mock(PermissionService.class);
    when(servico.resumo(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any()))
        .thenAnswer(chamada -> new ResumoFinanceiroResponse());
    FinanceiroController controller = new FinanceiroController(servico, permissoes);

    when(permissoes.possuiPermissao("finance:manage")).thenReturn(false);
    assertThat(controller.resumo(null, null, null, null, null, null, null, null, null).canManage)
        .isFalse();

    when(permissoes.possuiPermissao("finance:manage")).thenReturn(true);
    assertThat(controller.resumo(null, null, null, null, null, null, null, null, null).canManage)
        .isTrue();
  }
}
