package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import br.com.phdigitalcode.azzo.agenda.pro.controller.AuditoriaController;
import br.com.phdigitalcode.azzo.agenda.pro.controller.CommissionController;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuditQueryService;
import br.com.phdigitalcode.azzo.agenda.pro.service.CommissionService;

/**
 * Avalia DE VERDADE as expressoes "papel OU permissao" das telas distribuiveis (auditoria, V126;
 * comissoes, V127) com o Spring Security ligado. O teste de string do controller nao pega uma
 * expressao que nao resolve — por exemplo, o bean {@code permissionService} com outro nome.
 */
@SpringJUnitConfig(GatesPorPerfilDeAcessoTest.Configuracao.class)
class GatesPorPerfilDeAcessoTest {

  @Configuration
  @EnableMethodSecurity
  static class Configuracao {
    @Bean
    PermissionService permissionService() {
      return mock(PermissionService.class);
    }

    @Bean
    CommissionController commissionController() {
      return new CommissionController(mock(CommissionService.class));
    }

    @Bean
    AuditoriaController auditoriaController() {
      return new AuditoriaController(
          mock(ContextoTenant.class), mock(AuditQueryService.class), mock(AuditService.class));
    }
  }

  @Autowired private PermissionService permissionService;
  @Autowired private CommissionController comissoes;
  @Autowired private AuditoriaController auditoria;

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
    reset(permissionService);
  }

  @Test
  void donoLeEPagaComissaoSemPermissaoNenhuma() {
    logadoComo("OWNER");
    assertThatCode(() -> comissoes.cycles(null)).doesNotThrowAnyException();
    assertThatCode(() -> comissoes.payCycle(UUID.randomUUID(), null)).doesNotThrowAnyException();
  }

  @Test
  void profissionalSemPerfilNaoVeComissao() {
    logadoComo("PROFESSIONAL");
    assertThatThrownBy(() -> comissoes.cycles(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> comissoes.createAdjustment(null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void soCommissionViewLeMasNaoPaga() {
    logadoComo("STAFF");
    when(permissionService.possuiPermissao("commission:view")).thenReturn(true);
    assertThatCode(() -> comissoes.report(null, null, null, null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> comissoes.payCycle(UUID.randomUUID(), null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void comOsDoisCodigosDaTelaPagaEFechaCiclo() {
    logadoComo("STAFF");
    when(permissionService.possuiPermissao("commission:view")).thenReturn(true);
    when(permissionService.possuiPermissao("commission:manage")).thenReturn(true);
    assertThatCode(() -> comissoes.closeCycle(null)).doesNotThrowAnyException();
    assertThatCode(() -> comissoes.payCycle(UUID.randomUUID(), null)).doesNotThrowAnyException();
  }

  @Test
  void adminNaoEntraNasComissoesNemNaTrilha() {
    logadoComo("ADMIN");
    assertThatThrownBy(() -> comissoes.cycles(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> auditoria.filterOptions(null, null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void trilhaAceitaFinancePeloPapelEFuncionarioPelaPermissao() {
    logadoComo("FINANCE");
    assertThatCode(() -> auditoria.filterOptions(null, null)).doesNotThrowAnyException();

    logadoComo("STAFF");
    assertThatThrownBy(() -> auditoria.filterOptions(null, null)).isInstanceOf(AccessDeniedException.class);
    when(permissionService.possuiPermissao("audit:view")).thenReturn(true);
    assertThatCode(() -> auditoria.filterOptions(null, null)).doesNotThrowAnyException();
  }

  private static void logadoComo(String papel) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(
            "usuario", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + papel))));
  }
}
