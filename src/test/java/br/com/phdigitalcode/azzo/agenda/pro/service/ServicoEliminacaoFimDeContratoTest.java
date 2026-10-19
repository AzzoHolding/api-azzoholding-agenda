package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/** LGPD: anonimizar os clientes 5 anos depois do fim do contrato do salao (decisao de 19/10/2026). */
class ServicoEliminacaoFimDeContratoTest {

  private static final Duration SEIS_ANOS = Duration.ofDays(6 * 366);
  private static final Duration QUATRO_ANOS = Duration.ofDays(4 * 365);

  private TenantRepository tenants;
  private CheckoutOrderRepository pedidos;
  private SubscriptionRepository assinaturas;
  private ClienteRepository clientes;
  private ServicoAnonimizacaoTitular anonimizacao;
  private ServicoEliminacaoFimDeContrato servico;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    tenants = mock(TenantRepository.class);
    pedidos = mock(CheckoutOrderRepository.class);
    assinaturas = mock(SubscriptionRepository.class);
    clientes = mock(ClienteRepository.class);
    anonimizacao = mock(ServicoAnonimizacaoTitular.class);
    servico = new ServicoEliminacaoFimDeContrato(tenants, pedidos, assinaturas, clientes, anonimizacao, 5, 500);
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    when(tenants.findAll()).thenReturn(List.of(tenant));
  }

  private void contratoTerminouHa(Duration tempo) {
    when(pedidos.possuiPlanoVigente(eq(tenantId), any())).thenReturn(false);
    when(assinaturas.possuiRenovacaoAtiva(tenantId)).thenReturn(false);
    when(pedidos.fimDoUltimoPlano(tenantId, StatusCheckout.CONFIRMED)).thenReturn(Instant.now().minus(tempo));
  }

  @Test
  void contratoEncerradoHaMaisDeCincoAnosAnonimizaOsClientes() {
    contratoTerminouHa(SEIS_ANOS);
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(clientes.idsNaoAnonimizados(eq(tenantId), any())).thenReturn(List.of(a, b));

    assertThat(servico.executar()).isEqualTo(2);
    verify(anonimizacao).anonimizarPorFimDeContrato(tenantId, a);
    verify(anonimizacao).anonimizarPorFimDeContrato(tenantId, b);
  }

  @Test
  void contratoEncerradoHaMenosDeCincoAnosNaoMexe() {
    contratoTerminouHa(QUATRO_ANOS);

    assertThat(servico.executar()).isZero();
    verify(anonimizacao, never()).anonimizarPorFimDeContrato(any(), any());
  }

  @Test
  void salaoComPlanoVigenteOuAssinaturaRenovandoNaoMexe() {
    contratoTerminouHa(SEIS_ANOS);
    when(pedidos.possuiPlanoVigente(eq(tenantId), any())).thenReturn(true);
    assertThat(servico.executar()).isZero();

    when(pedidos.possuiPlanoVigente(eq(tenantId), any())).thenReturn(false);
    when(assinaturas.possuiRenovacaoAtiva(tenantId)).thenReturn(true);
    assertThat(servico.executar()).isZero();
    verify(anonimizacao, never()).anonimizarPorFimDeContrato(any(), any());
  }

  @Test
  void salaoQueNuncaTevePlanoConfirmadoFicaDeFora() {
    when(pedidos.possuiPlanoVigente(eq(tenantId), any())).thenReturn(false);
    when(assinaturas.possuiRenovacaoAtiva(tenantId)).thenReturn(false);
    when(pedidos.fimDoUltimoPlano(tenantId, StatusCheckout.CONFIRMED)).thenReturn(null);

    assertThat(servico.executar()).isZero();
  }

  @Test
  void falhaEmUmClienteNaoParaOsOutros() {
    contratoTerminouHa(SEIS_ANOS);
    UUID quebrado = UUID.randomUUID();
    UUID bom = UUID.randomUUID();
    when(clientes.idsNaoAnonimizados(eq(tenantId), any())).thenReturn(List.of(quebrado, bom));
    doThrow(new IllegalStateException("erro")).when(anonimizacao).anonimizarPorFimDeContrato(tenantId, quebrado);

    assertThat(servico.executar()).isEqualTo(1);
    verify(anonimizacao).anonimizarPorFimDeContrato(tenantId, bom);
  }
}
