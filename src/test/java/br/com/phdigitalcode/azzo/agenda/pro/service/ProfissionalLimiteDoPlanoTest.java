package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ProfissionalRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PlanLimitsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacUserRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SpecialtyRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * AGD-015: o limite de profissionais do plano vale para profissionais ATIVOS em qualquer caminho.
 *
 * <p>Antes so o cadastro conferia. Num plano de 3: criar 3, desativar um, criar outro e reativar o
 * primeiro deixava 4 ativos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfissionalLimiteDoPlanoTest {

  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private SpecialtyRepository specialtyRepository;
  @Mock private ServicoRepository servicoRepository;
  @Mock private RbacRoleRepository rbacRoleRepository;
  @Mock private RbacUserRoleRepository rbacUserRoleRepository;
  @Mock private RbacAuthorizationRepository rbacAuthorizationRepository;
  @Mock private PlanLimitsRepository planLimitsRepository;
  @Mock private CredentialsEmailService credentialsEmailService;
  @Mock private AfterCommitExecutor afterCommitExecutor;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuditService auditService;
  @Mock private PasswordPolicyValidator passwordPolicyValidator;
  @Mock private AgendamentoRepository agendamentoRepository;

  @InjectMocks private ProfissionalService service;

  private static final UUID TENANT = UUID.randomUUID();
  private static final String LIMITE_ATINGIDO = "Limite de profissionais do plano atingido (3)";

  @BeforeEach
  void preparar() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT);
    when(profissionalRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    UUID produto = UUID.randomUUID();
    when(planLimitsRepository.findActivePlanProductId(any(), any())).thenReturn(Optional.of(produto));
    when(planLimitsRepository.findMaxProfessionals(produto)).thenReturn(Optional.of(3));
  }

  /** O salao ja tem {@code ativos} profissionais ativos. */
  private void salaoComAtivos(long ativos) {
    when(profissionalRepository.countByTenantIdAndIsActiveTrue(TENANT)).thenReturn(ativos);
  }

  private Profissional profissional(boolean ativo) {
    Profissional p = new Profissional();
    p.setId(UUID.randomUUID());
    p.setTenantId(TENANT);
    p.setName("Ana");
    p.setActive(ativo);
    when(profissionalRepository.findByIdAndTenantId(p.getId(), TENANT)).thenReturn(Optional.of(p));
    return p;
  }

  private ProfissionalRequest edicao(boolean ativo) {
    ProfissionalRequest req = new ProfissionalRequest();
    req.name = "Ana";
    req.isActive = ativo;
    return req;
  }

  @Test
  @DisplayName("reativar pelo toggle no limite do plano e recusado")
  void reativarPeloToggleNoLimiteERecusado() {
    salaoComAtivos(3);
    Profissional inativo = profissional(false);

    assertThatThrownBy(() -> service.toggleStatus(inativo.getId(), true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(LIMITE_ATINGIDO);

    assertThat(inativo.isActive()).isFalse();
    verify(profissionalRepository, never()).save(any());
  }

  @Test
  @DisplayName("reativar pela edicao (isActive=true) no limite do plano e recusado")
  void reativarPelaEdicaoNoLimiteERecusado() {
    salaoComAtivos(3);
    Profissional inativo = profissional(false);

    assertThatThrownBy(() -> service.atualizar(inativo.getId(), edicao(true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(LIMITE_ATINGIDO);

    assertThat(inativo.isActive()).isFalse();
    verify(profissionalRepository, never()).save(any());
  }

  @Test
  @DisplayName("reativar com vaga no plano funciona")
  void reativarComVagaFunciona() {
    salaoComAtivos(2);
    Profissional pelaTela = profissional(false);
    Profissional pelaEdicao = profissional(false);

    service.toggleStatus(pelaTela.getId(), true);
    service.atualizar(pelaEdicao.getId(), edicao(true));

    assertThat(pelaTela.isActive()).isTrue();
    assertThat(pelaEdicao.isActive()).isTrue();
  }

  @Test
  @DisplayName("quem ja esta ativo nao e reconferido: editar ou ligar de novo no limite passa")
  void quemJaEstaAtivoNaoEReconferido() {
    salaoComAtivos(3); // o proprio profissional esta entre os 3
    Profissional ativo = profissional(true);

    service.toggleStatus(ativo.getId(), true);
    service.atualizar(ativo.getId(), edicao(true));

    assertThat(ativo.isActive()).isTrue();
    verify(planLimitsRepository, never()).travarLimiteDeProfissionais(any());
  }

  @Test
  @DisplayName("desativar nunca e barrado pelo limite")
  void desativarNaoConsultaOLimite() {
    salaoComAtivos(3);
    Profissional ativo = profissional(true);

    service.toggleStatus(ativo.getId(), false);

    assertThat(ativo.isActive()).isFalse();
    verify(planLimitsRepository, never()).travarLimiteDeProfissionais(any());
  }

  /** A conferencia e serializada por salao: o lock vem ANTES de contar os ativos. */
  @Test
  @DisplayName("a reativacao trava o salao antes de contar os ativos")
  void reativarTravaOSalaoAntesDeContar() {
    salaoComAtivos(1);
    Profissional inativo = profissional(false);

    service.toggleStatus(inativo.getId(), true);

    InOrder ordem = inOrder(planLimitsRepository, profissionalRepository);
    ordem.verify(planLimitsRepository).travarLimiteDeProfissionais(TENANT);
    ordem.verify(profissionalRepository).countByTenantIdAndIsActiveTrue(TENANT);
  }

  @Test
  @DisplayName("o cadastro tambem trava o salao e segue respeitando o limite")
  void cadastroTravaERespeitaOLimite() {
    salaoComAtivos(3);
    ProfissionalRequest novo = edicao(true);

    assertThatThrownBy(() -> service.criar(novo))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(LIMITE_ATINGIDO);

    InOrder ordem = inOrder(planLimitsRepository, profissionalRepository);
    ordem.verify(planLimitsRepository).travarLimiteDeProfissionais(TENANT);
    ordem.verify(profissionalRepository).countByTenantIdAndIsActiveTrue(TENANT);
  }
}
