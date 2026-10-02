package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Titular anonimizado (LGPD) nao se cobra, nem pelo id antigo no Asaas, nem criando um cliente novo
 * la com um CPF que chegou no pedido.
 */
class TenantAsaasChargeServiceAnonimizadoTest {

  private AsaasClient asaasClient;
  private TenantAsaasChargeService service;

  @BeforeEach
  void setUp() {
    asaasClient = mock(AsaasClient.class);
    service =
        new TenantAsaasChargeService(
            asaasClient, mock(TenantPaymentSettingsRepository.class), mock(EncryptionService.class));
  }

  private Cliente clienteAnonimizado() {
    Cliente cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setName("[ANONIMIZADO]");
    cliente.setAnonymizedAt(Instant.now());
    return cliente;
  }

  @Test
  void clienteAnonimizadoSemIdNoAsaasNaoGanhaCadastroNovo() {
    Cliente cliente = clienteAnonimizado();
    cliente.setCpfCnpj("12345678901"); // CPF que veio no pedido de assinatura

    assertThatThrownBy(() -> service.ensureAsaasCustomer("chave", cliente))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("anonimizado");
    verify(asaasClient, never()).createCustomer(anyString(), any());
  }

  @Test
  void clienteAnonimizadoComIdAntigoTambemNaoEhCobrado() {
    Cliente cliente = clienteAnonimizado();
    cliente.setAsaasCustomerId("cus_antigo"); // anonimizado antes de a rotina soltar o vinculo

    assertThatThrownBy(() -> service.ensureAsaasCustomer("chave", cliente))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("anonimizado");
  }

  @Test
  void clienteNormalSegueComoAntes() {
    Cliente cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setAsaasCustomerId("cus_normal");

    service.ensureAsaasCustomer("chave", cliente); // nao lanca: ja tem cadastro no Asaas

    verify(asaasClient, never()).createCustomer(anyString(), any());
  }
}
