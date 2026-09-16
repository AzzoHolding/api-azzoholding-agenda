package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ProfissionalRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ProfissionalResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
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
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * "Aceita agendamento" e o login do dono no cadastro do profissional.
 *
 * <p>Quem nao aceita agendamento continua na EQUIPE (a tela de equipe lista todos), mas sai das
 * listas de MARCAR — o assistente pede so os que aceitam. E ligar um login a um cadastro so vale
 * para quem ainda nao tem: trocar deixaria o login anterior sem dono.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfissionalAceitaAgendamentoTest {

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

  @BeforeEach
  void tenant() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(TENANT);
    when(profissionalRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
  }

  private Profissional profissional(String nome, boolean aceita) {
    Profissional p = new Profissional();
    p.setId(UUID.randomUUID());
    p.setTenantId(TENANT);
    p.setName(nome);
    p.setActive(true);
    p.setAcceptsAppointments(aceita);
    return p;
  }

  private ProfissionalRequest edicao(String nome) {
    ProfissionalRequest req = new ProfissionalRequest();
    req.name = nome;
    req.isActive = true;
    return req;
  }

  @Test
  @DisplayName("a equipe lista todos; a lista de marcar so quem aceita agendamento")
  void listaDeMarcarSoTemQuemAceita() {
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(TENANT))
        .thenReturn(List.of(profissional("Ana", true), profissional("Recepcao", false)));

    assertThat(service.listar(null)).extracting(r -> r.name).containsExactly("Ana", "Recepcao");
    assertThat(service.listar(null, true)).extracting(r -> r.name).containsExactly("Ana");
  }

  @Test
  @DisplayName("a resposta diz se o profissional aceita agendamento")
  void respostaDizSeAceita() {
    when(profissionalRepository.findByTenantIdAndIsActiveTrue(TENANT))
        .thenReturn(List.of(profissional("Recepcao", false)));

    ProfissionalResponse resposta = service.listar(null).get(0);
    assertThat(resposta.acceptsAppointments).isFalse();
  }

  @Test
  @DisplayName("a edicao desliga quando pedido, e sem o campo mantem o que estava")
  void edicaoDesligaOuMantem() {
    Profissional p = profissional("Ana", true);
    when(profissionalRepository.findByIdAndTenantId(p.getId(), TENANT)).thenReturn(Optional.of(p));

    service.atualizar(p.getId(), edicao("Ana"));
    assertThat(p.isAcceptsAppointments()).isTrue();

    ProfissionalRequest desligar = edicao("Ana");
    desligar.acceptsAppointments = false;
    service.atualizar(p.getId(), desligar);
    assertThat(p.isAcceptsAppointments()).isFalse();
  }

  @Test
  @DisplayName("nao troca o login de quem ja tem um")
  void naoTrocaLoginDeQuemJaTem() {
    Profissional p = profissional("Ana", true);
    p.setUserId(UUID.randomUUID());
    when(profissionalRepository.findByIdAndTenantId(p.getId(), TENANT)).thenReturn(Optional.of(p));
    ProfissionalRequest req = edicao("Ana");
    req.userId = UUID.randomUUID().toString();

    assertThatThrownBy(() -> service.atualizar(p.getId(), req))
        .hasMessage("Este profissional ja tem login proprio");
    verify(profissionalRepository, never()).save(any());
  }

  @Test
  @DisplayName("conta so pendentes e confirmados daqui para a frente")
  @SuppressWarnings("unchecked")
  void contaAtendimentosFuturos() {
    Profissional p = profissional("Ana", true);
    when(profissionalRepository.findByIdAndTenantId(p.getId(), TENANT)).thenReturn(Optional.of(p));
    when(agendamentoRepository.countFutureActiveForProfessional(
            eq(TENANT), eq(p.getId()), any(), any(), anyString()))
        .thenReturn(3L);

    assertThat(service.contarAgendamentosFuturos(p.getId())).isEqualTo(3L);

    ArgumentCaptor<Collection<StatusAgendamento>> status = ArgumentCaptor.forClass(Collection.class);
    verify(agendamentoRepository)
        .countFutureActiveForProfessional(eq(TENANT), eq(p.getId()), status.capture(), any(), anyString());
    assertThat(status.getValue())
        .containsExactlyInAnyOrder(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED);
  }

  /**
   * A tela manda `commissionRate: null` quando o campo fica em branco, e a coluna e NOT NULL: em
   * producao (2026-09-16) todo cadastro sem comissao morria com "null value in column
   * commission_rate" e a mensagem generica de erro inesperado.
   */
  @Test
  @DisplayName("comissao ausente vira zero no cadastro e mantem o valor na edicao")
  void comissaoAusenteNaoViraNula() {
    Profissional novo = profissional("Ana", true);
    novo.setCommissionRate(null);
    when(profissionalRepository.findByIdAndTenantId(novo.getId(), TENANT)).thenReturn(Optional.of(novo));

    service.atualizar(novo.getId(), edicao("Ana"));
    assertThat(novo.getCommissionRate()).isEqualByComparingTo(BigDecimal.ZERO);

    Profissional comComissao = profissional("Bia", true);
    comComissao.setCommissionRate(new BigDecimal("40.00"));
    when(profissionalRepository.findByIdAndTenantId(comComissao.getId(), TENANT))
        .thenReturn(Optional.of(comComissao));

    service.atualizar(comComissao.getId(), edicao("Bia"));
    assertThat(comComissao.getCommissionRate()).isEqualByComparingTo(new BigDecimal("40.00"));
  }
}
