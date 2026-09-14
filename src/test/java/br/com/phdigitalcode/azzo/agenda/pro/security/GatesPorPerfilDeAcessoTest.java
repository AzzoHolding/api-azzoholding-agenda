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
import br.com.phdigitalcode.azzo.agenda.pro.controller.FiscalController;
import br.com.phdigitalcode.azzo.agenda.pro.controller.NfseController;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuditQueryService;
import br.com.phdigitalcode.azzo.agenda.pro.service.CommissionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalIdempotencyService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseCertificateUnlockService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseConfigService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseIdempotencyService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseLocationService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfsePdfJobService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseProviderCapabilitiesService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFiscal;

/**
 * Avalia DE VERDADE as expressoes "papel OU permissao" das telas distribuiveis (auditoria, V126;
 * comissoes, V127; fiscal, V128) com o Spring Security ligado. O teste de string do controller nao pega uma
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

    @Bean
    FiscalController fiscalController() {
      return new FiscalController(
          mock(ServicoFiscal.class),
          mock(FiscalAccessService.class),
          mock(FiscalIdempotencyService.class),
          mock(ContextoTenant.class));
    }

    @Bean
    NfseController nfseController() {
      return new NfseController(
          mock(NfseService.class),
          mock(FiscalAccessService.class),
          mock(NfseCertificateUnlockService.class),
          mock(NfseIdempotencyService.class),
          mock(NfseLocationService.class),
          mock(NfseConfigService.class),
          mock(NfseProviderCapabilitiesService.class),
          mock(NfsePdfJobService.class),
          mock(ContextoTenant.class));
    }
  }

  @Autowired private PermissionService permissionService;
  @Autowired private CommissionController comissoes;
  @Autowired private AuditoriaController auditoria;
  @Autowired private FiscalController fiscal;
  @Autowired private NfseController nfse;

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
    assertThatThrownBy(() -> fiscal.listarInvoices(null, null, null, null, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void fiscalSoLeituraConsultaMasNaoEmite() {
    logadoComo("STAFF");
    when(permissionService.possuiPermissao("fiscal:view")).thenReturn(true);
    assertThatCode(() -> fiscal.listarInvoices(null, null, null, null, null)).doesNotThrowAnyException();
    // A emissao le a configuracao: a leitura dela vem com fiscal:view.
    assertThatCode(() -> nfse.obterConfig(null)).doesNotThrowAnyException();
    assertThatCode(() -> fiscal.obterTaxConfig()).doesNotThrowAnyException();
    assertThatThrownBy(() -> nfse.autorizar("n-1", null, null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void comOsDoisCodigosDoFiscalEmiteMasNaoMexeNaConfiguracao() {
    logadoComo("STAFF");
    when(permissionService.possuiPermissao("fiscal:view")).thenReturn(true);
    when(permissionService.possuiPermissao("fiscal:manage")).thenReturn(true);
    assertThatCode(() -> nfse.autorizar("n-1", null, null)).doesNotThrowAnyException();
    assertThatCode(() -> fiscal.recalcular(2026, 9)).doesNotThrowAnyException();
    assertThatThrownBy(() -> nfse.salvarConfig(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> fiscal.salvarCertificado(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> fiscal.atualizarTaxConfig(null)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void donoMexeNaConfiguracaoFiscal() {
    logadoComo("OWNER");
    assertThatCode(() -> nfse.salvarConfig(null)).doesNotThrowAnyException();
    assertThatCode(() -> fiscal.atualizarTaxConfig(null)).doesNotThrowAnyException();
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
