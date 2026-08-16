package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PackageDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackageBalance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ClientPackagePurchase;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackageItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackageBalanceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClientPackagePurchaseRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicePackageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre {@code modules/packages/application/ServicoPackages.java}: isolamento por tenant, validacao
 * de servico, regravacao integral da composicao no update e o calculo de sessoes disponiveis.
 */
class ServicoPackagesTest {

  private ServicePackageRepository servicePackageRepository;
  private ServicePackageItemRepository servicePackageItemRepository;
  private ClientPackagePurchaseRepository clientPackagePurchaseRepository;
  private ClientPackageBalanceRepository clientPackageBalanceRepository;
  private ServicoRepository servicoRepository;
  private ServicoPackages service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    servicePackageRepository = mock(ServicePackageRepository.class);
    servicePackageItemRepository = mock(ServicePackageItemRepository.class);
    clientPackagePurchaseRepository = mock(ClientPackagePurchaseRepository.class);
    clientPackageBalanceRepository = mock(ClientPackageBalanceRepository.class);
    servicoRepository = mock(ServicoRepository.class);

    ContextoTenant contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    // O @PrePersist da entidade nao roda com repositorio mockado — o id e atribuido aqui para
    // reproduzir o efeito observavel do persist().
    when(servicePackageRepository.save(any(ServicePackage.class)))
        .thenAnswer(
            inv -> {
              ServicePackage p = inv.getArgument(0);
              if (p.getId() == null) p.setId(UUID.randomUUID());
              return p;
            });
    when(servicePackageItemRepository.save(any(ServicePackageItem.class)))
        .thenAnswer(
            inv -> {
              ServicePackageItem i = inv.getArgument(0);
              if (i.getId() == null) i.setId(UUID.randomUUID());
              return i;
            });
    when(servicePackageItemRepository.findByPackageId(any())).thenReturn(List.of());

    service =
        new ServicoPackages(
            contextoTenant,
            servicePackageRepository,
            servicePackageItemRepository,
            clientPackagePurchaseRepository,
            clientPackageBalanceRepository,
            servicoRepository);
  }

  private void servicoExiste(String nome) {
    Servico servico = new Servico();
    servico.setId(serviceId);
    servico.setTenantId(tenantId);
    servico.setName(nome);
    when(servicoRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.of(servico));
    when(servicoRepository.findById(eq(serviceId))).thenReturn(Optional.of(servico));
  }

  private PackageDtos.PackageRequest requestComUmItem(int sessoes) {
    PackageDtos.ItemRequest item = new PackageDtos.ItemRequest();
    item.serviceId = serviceId.toString();
    item.sessoes = sessoes;

    PackageDtos.PackageRequest req = new PackageDtos.PackageRequest();
    req.nome = "Combo Corte";
    req.descricao = "5 cortes";
    req.preco = new BigDecimal("250.00");
    req.ativo = true;
    req.itens = new ArrayList<>(List.of(item));
    return req;
  }

  @Test
  void criarPersistePacoteComTenantEItensDaComposicao() {
    servicoExiste("Corte");

    PackageDtos.PackageResponse response = service.criar(requestComUmItem(5));

    ArgumentCaptor<ServicePackage> pacoteCaptor = ArgumentCaptor.forClass(ServicePackage.class);
    verify(servicePackageRepository).save(pacoteCaptor.capture());
    ServicePackage pacote = pacoteCaptor.getValue();
    assertThat(pacote.getTenantId()).isEqualTo(tenantId);
    assertThat(pacote.getNome()).isEqualTo("Combo Corte");
    assertThat(pacote.getDescricao()).isEqualTo("5 cortes");
    assertThat(pacote.getPreco()).isEqualByComparingTo("250.00");
    assertThat(pacote.isAtivo()).isTrue();

    ArgumentCaptor<ServicePackageItem> itemCaptor =
        ArgumentCaptor.forClass(ServicePackageItem.class);
    verify(servicePackageItemRepository).save(itemCaptor.capture());
    ServicePackageItem item = itemCaptor.getValue();
    assertThat(item.getTenantId()).isEqualTo(tenantId);
    assertThat(item.getPackageId()).isEqualTo(pacote.getId());
    assertThat(item.getServiceId()).isEqualTo(serviceId);
    assertThat(item.getSessoes()).isEqualTo(5);

    assertThat(response.id).isEqualTo(pacote.getId().toString());
    assertThat(response.nome).isEqualTo("Combo Corte");
  }

  @Test
  void criarComServicoDeOutroTenantFalhaCom404ENaoPersisteItem() {
    when(servicoRepository.findByIdAndTenantId(eq(serviceId), eq(tenantId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.criar(requestComUmItem(3)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Servico nao encontrado: " + serviceId)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);

    verify(servicePackageItemRepository, never()).save(any());
  }

  @Test
  void atualizarApagaComposicaoAntigaAntesDeRegravar() {
    servicoExiste("Corte");
    ServicePackage existente = new ServicePackage();
    existente.setId(UUID.randomUUID());
    existente.setTenantId(tenantId);
    existente.setNome("antigo");
    when(servicePackageRepository.findByIdAndTenantId(eq(existente.getId()), eq(tenantId)))
        .thenReturn(Optional.of(existente));

    PackageDtos.PackageRequest req = requestComUmItem(10);
    req.nome = "Combo novo";
    req.ativo = false;

    service.atualizar(existente.getId(), req);

    // A ordem importa: o delete + flush tem que acontecer antes do save dos novos itens, senao o
    // Hibernate poderia descartar a composicao recem-gravada junto com a antiga.
    InOrder ordem = inOrder(servicePackageItemRepository);
    ordem.verify(servicePackageItemRepository).deleteByPackageId(existente.getId());
    ordem.verify(servicePackageItemRepository).flush();
    ordem.verify(servicePackageItemRepository).save(any(ServicePackageItem.class));

    assertThat(existente.getNome()).isEqualTo("Combo novo");
    assertThat(existente.isAtivo()).isFalse();
  }

  @Test
  void atualizarDePacoteInexistenteFalhaCom404() {
    UUID id = UUID.randomUUID();
    when(servicePackageRepository.findByIdAndTenantId(eq(id), eq(tenantId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.atualizar(id, requestComUmItem(1)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Pacote nao encontrado.");

    verify(servicePackageItemRepository, never()).deleteByPackageId(any());
  }

  @Test
  void obterResolveNomeDoServicoEDeixaNuloQuandoServicoSumiu() {
    UUID pacoteId = UUID.randomUUID();
    UUID servicoRemovidoId = UUID.randomUUID();
    ServicePackage pacote = new ServicePackage();
    pacote.setId(pacoteId);
    pacote.setTenantId(tenantId);
    pacote.setNome("Combo");
    pacote.setPreco(new BigDecimal("99.90"));
    pacote.setCreatedAt(Instant.parse("2026-01-10T12:00:00Z"));
    when(servicePackageRepository.findByIdAndTenantId(eq(pacoteId), eq(tenantId)))
        .thenReturn(Optional.of(pacote));

    servicoExiste("Corte");
    when(servicoRepository.findById(eq(servicoRemovidoId))).thenReturn(Optional.empty());

    ServicePackageItem itemOk = new ServicePackageItem();
    itemOk.setServiceId(serviceId);
    itemOk.setSessoes(4);
    ServicePackageItem itemOrfao = new ServicePackageItem();
    itemOrfao.setServiceId(servicoRemovidoId);
    itemOrfao.setSessoes(2);
    when(servicePackageItemRepository.findByPackageId(eq(pacoteId)))
        .thenReturn(List.of(itemOk, itemOrfao));

    PackageDtos.PackageResponse response = service.obter(pacoteId);

    assertThat(response.createdAt).isEqualTo("2026-01-10T12:00:00Z");
    assertThat(response.itens).hasSize(2);
    assertThat(response.itens.get(0).serviceNome).isEqualTo("Corte");
    assertThat(response.itens.get(0).sessoes).isEqualTo(4);
    assertThat(response.itens.get(1).serviceNome).isNull();
  }

  @Test
  void listarDoClienteCalculaSessoesDisponiveisComoTotaisMenosUsadas() {
    UUID clientId = UUID.randomUUID();
    UUID compraId = UUID.randomUUID();
    ClientPackagePurchase compra = new ClientPackagePurchase();
    compra.setId(compraId);
    compra.setTenantId(tenantId);
    compra.setClientId(clientId);
    compra.setPackageId(UUID.randomUUID());
    compra.setPackageNome("Combo Corte");
    compra.setPrecoPago(new BigDecimal("250.00"));
    when(clientPackagePurchaseRepository.findByTenantIdAndClientIdOrderByCreatedAtDesc(
            eq(tenantId), eq(clientId)))
        .thenReturn(List.of(compra));

    ClientPackageBalance saldo = new ClientPackageBalance();
    saldo.setServiceId(serviceId);
    saldo.setServiceNome("Corte");
    saldo.setSessoesTotais(5);
    saldo.setSessoesUsadas(2);
    when(clientPackageBalanceRepository.findByPurchaseId(eq(compraId))).thenReturn(List.of(saldo));

    List<PackageDtos.ClientPackagePurchaseResponse> compras = service.listarDoCliente(clientId);

    assertThat(compras).hasSize(1);
    assertThat(compras.get(0).packageNome).isEqualTo("Combo Corte");
    assertThat(compras.get(0).saldos).hasSize(1);
    assertThat(compras.get(0).saldos.get(0).sessoesTotais).isEqualTo(5);
    assertThat(compras.get(0).saldos.get(0).sessoesUsadas).isEqualTo(2);
    assertThat(compras.get(0).saldos.get(0).sessoesDisponiveis).isEqualTo(3);
  }
}
