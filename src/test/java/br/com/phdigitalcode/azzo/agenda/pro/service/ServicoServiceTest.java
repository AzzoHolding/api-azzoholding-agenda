package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ServicoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ServicoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ServiceCategory;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Cobre as regras de negocio de servicos que existem no {@code ServicoServicos} do Quarkus
 * original — validacao de sinal (deposit), resolucao/criacao de categoria por nome, vinculo de
 * profissionais e isolamento por tenant. Verifica o resultado observavel, nao "se o mock foi
 * chamado".
 */
class ServicoServiceTest {

  private ServicoRepository servicoRepository;
  private ProfissionalRepository profissionalRepository;
  private ServiceCategoryRepository serviceCategoryRepository;
  private ContextoTenant contextoTenant;
  private ServicoService service;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    servicoRepository = mock(ServicoRepository.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    serviceCategoryRepository = mock(ServiceCategoryRepository.class);
    contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(servicoRepository.save(any(Servico.class))).thenAnswer(inv -> {
      Servico s = inv.getArgument(0);
      if (s.getId() == null) s.setId(UUID.randomUUID());
      return s;
    });
    service = new ServicoService(servicoRepository, profissionalRepository, serviceCategoryRepository, contextoTenant);
  }

  private ServicoRequest baseRequest() {
    ServicoRequest req = new ServicoRequest();
    req.name = "Corte Masculino";
    req.description = "Corte simples";
    req.duration = 30;
    req.price = new BigDecimal("50.00");
    req.isActive = true;
    return req;
  }

  @Test
  void criaServicoSemSinalNaoPersisteTipoNemValorDeSinal() {
    ServicoRequest req = baseRequest();
    req.requiresDeposit = false;
    req.depositType = "PERCENTUAL";
    req.depositValue = new BigDecimal("30");

    ServicoResponse response = service.criar(req);

    assertThat(response.requiresDeposit).isFalse();
    assertThat(response.depositType).isNull();
    assertThat(response.depositValue).isNull();
  }

  @Test
  void servicoComSinalSemTipoEhRejeitado() {
    ServicoRequest req = baseRequest();
    req.requiresDeposit = true;
    req.depositValue = new BigDecimal("20");

    assertThatThrownBy(() -> service.criar(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tipo de sinal e obrigatorio");
  }

  @Test
  void servicoComSinalSemValorEhRejeitado() {
    ServicoRequest req = baseRequest();
    req.requiresDeposit = true;
    req.depositType = "FIXO";

    assertThatThrownBy(() -> service.criar(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Valor do sinal e obrigatorio");
  }

  @Test
  void sinalPercentualAcimaDeCemEhRejeitado() {
    ServicoRequest req = baseRequest();
    req.requiresDeposit = true;
    req.depositType = "PERCENTUAL";
    req.depositValue = new BigDecimal("101");

    assertThatThrownBy(() -> service.criar(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nao pode ser maior que 100");
  }

  @Test
  void sinalPercentualDeExatamenteCemEhAceito() {
    ServicoRequest req = baseRequest();
    req.requiresDeposit = true;
    req.depositType = "PERCENTUAL";
    req.depositValue = new BigDecimal("100");

    ServicoResponse response = service.criar(req);

    assertThat(response.requiresDeposit).isTrue();
    assertThat(response.depositValue).isEqualByComparingTo("100");
  }

  @Test
  void sinalFixoAcimaDeCemEhAceitoPoisRegraDeCemSoValeParaPercentual() {
    ServicoRequest req = baseRequest();
    req.requiresDeposit = true;
    req.depositType = "FIXO";
    req.depositValue = new BigDecimal("250.00");

    ServicoResponse response = service.criar(req);

    assertThat(response.depositType).isEqualTo("FIXO");
    assertThat(response.depositValue).isEqualByComparingTo("250.00");
  }

  @Test
  void categoriaInexistenteEhCriadaEDevolvidaNaResposta() {
    UUID categoryId = UUID.randomUUID();
    when(serviceCategoryRepository.findByTenantAndName(eq(tenantId), eq("Cabelo"))).thenReturn(Optional.empty());
    when(serviceCategoryRepository.save(any(ServiceCategory.class))).thenAnswer(inv -> {
      ServiceCategory c = inv.getArgument(0);
      c.setId(categoryId);
      return c;
    });
    ServiceCategory persisted = new ServiceCategory();
    persisted.setId(categoryId);
    persisted.setTenantId(tenantId);
    persisted.setName("Cabelo");
    when(serviceCategoryRepository.findById(categoryId)).thenReturn(Optional.of(persisted));

    ServicoRequest req = baseRequest();
    req.category = "  Cabelo  "; // deve ser normalizado (trim)

    ServicoResponse response = service.criar(req);

    assertThat(response.category).isEqualTo("Cabelo");
  }

  @Test
  void profissionaisInformadosSaoVinculadosRespeitandoTenant() {
    UUID profId = UUID.randomUUID();
    Profissional prof = new Profissional();
    prof.setId(profId);
    prof.setTenantId(tenantId);
    prof.setName("Ana");
    when(profissionalRepository.findByIdInAndTenantId(anyList(), eq(tenantId))).thenReturn(List.of(prof));

    ServicoRequest req = baseRequest();
    req.professionalIds = List.of(profId);

    ServicoResponse response = service.criar(req);

    assertThat(response.professionalIds).containsExactly(profId);
  }

  @Test
  void atualizarServicoDeOutroTenantFalhaComNaoEncontrado() {
    UUID outroId = UUID.randomUUID();
    when(servicoRepository.findByIdAndTenantId(outroId, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.atualizar(outroId, baseRequest()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Servico nao encontrado");
  }

  @Test
  void deletarSelecionadosComListaVaziaEhRejeitado() {
    assertThatThrownBy(() -> service.deletarSelecionados(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nenhum servico selecionado");
  }

  @Test
  void deletarTodosSemServicosRetornaZero() {
    when(servicoRepository.findByTenantId(tenantId)).thenReturn(List.of());
    assertThat(service.deletarTodos()).isZero();
  }
}
