package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ComandaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentCustomerNoteResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentDetailResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentManagementReportResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentUpdateRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.NoShowReportResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.PendingAttendanceResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentCustomerNote;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AppointmentDeposit;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Comanda;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Transacao;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.exception.AppointmentConflictException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.NotificacaoService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppAppointmentNotificationService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentCustomerNoteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AppointmentDepositRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AuditEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteStatsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ComandaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RelatorioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransactionCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Testes de {@link ServicoAgendamentos}.
 *
 * <p>Os metodos {@code default} das interfaces Spring Data ({@code listByTenantAndEntity},
 * {@code findFirstByAppointmentAndTenant}, {@code findPaidUnusedByAppointmentId},
 * {@code listByTenantAndAppointmentIds}, {@code mapNamesByTenantAndIds}) sao stubados
 * <b>diretamente</b>: num mock do Mockito a implementacao {@code default} nao roda, so o proxy do
 * Spring Data a executa de verdade.
 */
@ExtendWith(MockitoExtension.class)
class ServicoAgendamentosTest {

  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

  private final UUID tenantId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID professionalId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @Mock private AgendamentoRepository agendamentoRepository;
  @Mock private AgendamentoQueryRepository agendamentoQueryRepository;
  @Mock private AgendamentoItemRepository agendamentoItemRepository;
  @Mock private AppointmentCustomerNoteRepository appointmentCustomerNoteRepository;
  @Mock private AppointmentDepositRepository appointmentDepositRepository;
  @Mock private ClienteRepository clienteRepository;
  @Mock private ClienteStatsRepository clienteStatsRepository;
  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  @Mock private ServicoRepository servicoRepository;
  @Mock private ServiceCategoryRepository serviceCategoryRepository;
  @Mock private TransacaoRepository transacaoRepository;
  @Mock private TransactionCategoryRepository transactionCategoryRepository;
  @Mock private ComandaRepository comandaRepository;
  @Mock private RelatorioRepository relatorioRepository;
  @Mock private AuditEventRepository auditEventRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private CommissionService commissionService;
  @Mock private AppointmentSettingsService appointmentSettingsService;
  @Mock private TenantOperationalSettingsService tenantOperationalSettingsService;
  @Mock private SpecialClosureService specialClosureService;
  @Mock private ServicoComanda servicoComanda;
  @Mock private NotificacaoService notificacaoService;
  @Mock private WhatsAppAppointmentNotificationService whatsAppNotificationService;
  @Mock private EstoqueMovimentacaoService estoqueMovimentacaoService;
  @Mock private AuditService auditService;
  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;

  private ServicoAgendamentos service;

  @BeforeEach
  void setUp() {
    service =
        new ServicoAgendamentos(
            agendamentoRepository,
            agendamentoQueryRepository,
            agendamentoItemRepository,
            appointmentCustomerNoteRepository,
            appointmentDepositRepository,
            clienteRepository,
            clienteStatsRepository,
            profissionalRepository,
            profissionalWorkingHourRepository,
            servicoRepository,
            serviceCategoryRepository,
            transacaoRepository,
            transactionCategoryRepository,
            comandaRepository,
            relatorioRepository,
            auditEventRepository,
            usuarioRepository,
            commissionService,
            appointmentSettingsService,
            tenantOperationalSettingsService,
            specialClosureService,
            servicoComanda,
            notificacaoService,
            whatsAppNotificationService,
            estoqueMovimentacaoService,
            auditService,
            new ObjectMapper(),
            contextoTenant,
            authenticatedUser);

    lenient().when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    // Simula o que o JPA faz: persist dispara o @PrePersist (id + createdAt).
    lenient()
        .when(agendamentoRepository.saveAndFlush(any(Agendamento.class)))
        .thenAnswer(invocation -> prePersist(invocation.getArgument(0)));
    lenient()
        .when(agendamentoRepository.save(any(Agendamento.class)))
        .thenAnswer(invocation -> prePersist(invocation.getArgument(0)));
    lenient()
        .when(agendamentoItemRepository.save(any(AgendamentoItem.class)))
        .thenAnswer(
            invocation -> {
              AgendamentoItem item = invocation.getArgument(0);
              if (item.getId() == null) item.setId(UUID.randomUUID());
              if (item.getCreatedAt() == null) item.setCreatedAt(Instant.now());
              return item;
            });
    lenient()
        .when(appointmentCustomerNoteRepository.save(any(AppointmentCustomerNote.class)))
        .thenAnswer(
            invocation -> {
              AppointmentCustomerNote note = invocation.getArgument(0);
              if (note.getId() == null) note.setId(UUID.randomUUID());
              if (note.getCreatedAt() == null) note.setCreatedAt(Instant.now());
              return note;
            });
  }

  private Agendamento prePersist(Agendamento a) {
    if (a.getId() == null) a.setId(UUID.randomUUID());
    if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());
    return a;
  }

  // ─── FIXTURES ─────────────────────────────────────────────────────────────

  private Servico servico(int duration, String price) {
    Servico s = new Servico();
    s.setId(serviceId);
    s.setTenantId(tenantId);
    s.setName("Corte");
    s.setDuration(duration);
    s.setPrice(new BigDecimal(price));
    s.setActive(true);
    return s;
  }

  private Profissional profissional() {
    Profissional p = new Profissional();
    p.setId(professionalId);
    p.setTenantId(tenantId);
    p.setUserId(userId);
    p.setName("Ana");
    p.setActive(true);
    p.setCommissionRate(new BigDecimal("10.00"));
    return p;
  }

  private AgendamentoRequest requestValido() {
    AgendamentoRequest req = new AgendamentoRequest();
    req.clientId = clientId.toString();
    req.professionalId = professionalId.toString();
    req.serviceId = serviceId.toString();
    req.date = "2030-06-10";
    req.startTime = "10:00";
    return req;
  }

  private Agendamento agendamentoExistente(StatusAgendamento status, LocalDate date, String start, String end) {
    Agendamento a = new Agendamento();
    a.setId(UUID.randomUUID());
    a.setTenantId(tenantId);
    a.setClientId(clientId);
    a.setProfessionalId(professionalId);
    a.setDate(date);
    a.setStartTime(start);
    a.setEndTime(end);
    a.setStatus(status);
    a.setCreatedAt(Instant.now());
    AgendamentoItem item = new AgendamentoItem();
    item.setId(UUID.randomUUID());
    item.setTenantId(tenantId);
    item.setAppointmentId(a.getId());
    item.setServiceId(serviceId);
    item.setQuantity(1);
    item.setUnitPrice(new BigDecimal("100.00"));
    item.setGrossAmount(new BigDecimal("100.00"));
    item.setDiscountAmount(BigDecimal.ZERO);
    item.setTotalPrice(new BigDecimal("100.00"));
    item.setCreatedAt(Instant.now());
    a.getItems().add(item);
    return a;
  }

  /** Stubs comuns do caminho feliz de {@code criar}. */
  private void stubCriacaoSemConflito() {
    when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
    when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
        .thenReturn(Optional.of(profissional()));
    when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
        .thenReturn(Optional.of(servico(30, "100.00")));
    when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any()))
        .thenReturn(true);
    when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any()))
        .thenReturn(false);
    when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
        .thenReturn(List.of());
    when(agendamentoRepository.listActiveByProfessionalAndDate(
            eq(tenantId), eq(professionalId), any(), anyList()))
        .thenReturn(List.of());
    lenient().when(agendamentoItemRepository.existsByAppointmentId(any())).thenReturn(false);
    lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
    lenient()
        .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
        .thenReturn(Optional.empty());
  }

  // ─── CRIACAO ──────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("criar")
  class Criar {

    @Test
    @DisplayName(
        "trava o par profissional/data ANTES de ler os conflitos e antes do insert (fix da race condition)")
    void aplicaAdvisoryLockAntesDaChecagemDeConflito() {
      stubCriacaoSemConflito();

      service.criar(requestValido());

      InOrder ordem =
          inOrder(agendamentoQueryRepository, agendamentoRepository);
      ordem
          .verify(agendamentoQueryRepository)
          .lockProfessionalDateForWrite(tenantId, professionalId, LocalDate.of(2030, 6, 10));
      ordem
          .verify(agendamentoRepository)
          .listActiveByProfessionalAndDate(
              eq(tenantId), eq(professionalId), eq(LocalDate.of(2030, 6, 10)), anyList());
      ordem.verify(agendamentoRepository).saveAndFlush(any(Agendamento.class));
    }

    @Test
    @DisplayName("calcula endTime a partir da duracao dos itens e grava o item resolvido")
    void calculaEndTimeEGravaItens() {
      stubCriacaoSemConflito();

      AgendamentoResponse response = service.criar(requestValido());

      assertThat(response.startTime).isEqualTo("10:00");
      assertThat(response.endTime).isEqualTo("10:30");
      assertThat(response.status).isEqualTo("PENDING");
      ArgumentCaptor<AgendamentoItem> captor = ArgumentCaptor.forClass(AgendamentoItem.class);
      verify(agendamentoItemRepository).save(captor.capture());
      assertThat(captor.getValue().getServiceId()).isEqualTo(serviceId);
      assertThat(captor.getValue().getQuantity()).isEqualTo(1);
      assertThat(captor.getValue().getUnitPrice()).isEqualByComparingTo("100.00");
      assertThat(response.totalPrice).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("CANCELLED e COMPLETED nao entram na busca de conflito")
    void ignoraCanceladoEConcluidoNaBuscaDeConflito() {
      stubCriacaoSemConflito();

      service.criar(requestValido());

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<StatusAgendamento>> captor = ArgumentCaptor.forClass(List.class);
      verify(agendamentoRepository)
          .listActiveByProfessionalAndDate(eq(tenantId), eq(professionalId), any(), captor.capture());
      assertThat(captor.getValue())
          .containsExactlyInAnyOrder(StatusAgendamento.CANCELLED, StatusAgendamento.COMPLETED);
    }

    @Test
    @DisplayName("recusa data com fechamento especial do salao")
    void recusaDataFechada() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(true);

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Salao fechado na data informada");
      verify(agendamentoQueryRepository, never()).lockProfessionalDateForWrite(any(), any(), any());
    }

    @Test
    @DisplayName("recusa horario fora do funcionamento do salao")
    void recusaForaDoHorarioDeFuncionamento() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
          .thenReturn(Optional.of(servico(30, "100.00")));
      when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any()))
          .thenReturn(false);

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Salao fechado ou fora do horario de funcionamento informado");
    }

    @Test
    @DisplayName("recusa quando ha fechamento especial parcial no intervalo")
    void recusaFechamentoEspecialParcial() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
          .thenReturn(Optional.of(servico(30, "100.00")));
      when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any()))
          .thenReturn(true);
      when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any()))
          .thenReturn(true);

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Salao ou profissional com fechamento especial no horario informado");
    }

    @Test
    @DisplayName("recusa horario fora da janela de trabalho configurada do profissional")
    void recusaForaDaJanelaDoProfissional() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
          .thenReturn(Optional.of(servico(30, "100.00")));
      when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any()))
          .thenReturn(true);
      when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any()))
          .thenReturn(false);
      // 2030-06-10 e uma segunda-feira (ISO 1). Janela 14:00-18:00 nao comporta 10:00-10:30.
      ProfissionalWorkingHour wh = new ProfissionalWorkingHour();
      wh.setTenantId(tenantId);
      wh.setProfessionalId(professionalId);
      wh.setDayOfWeek(1);
      wh.setWorking(true);
      wh.setStartTime(LocalTime.of(14, 0));
      wh.setEndTime(LocalTime.of(18, 0));
      when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
          .thenReturn(List.of(wh));

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("O profissional nao atende neste horario");
    }

    @Test
    @DisplayName("profissional inativo ou de outro tenant e recusado")
    void recusaProfissionalInativo() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Profissional nao encontrado ou inativo");
    }

    @Test
    @DisplayName("request sem serviceId e sem items e recusado")
    void recusaRequestSemItem() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));
      AgendamentoRequest req = requestValido();
      req.serviceId = null;

      assertThatThrownBy(() -> service.criar(req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Agendamento deve informar ao menos um item");
    }

    @Test
    @DisplayName("servico com lista de profissionais que nao inclui o escolhido e recusado")
    void recusaProfissionalQueNaoAtendeOServico() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));
      Servico s = servico(30, "100.00");
      Profissional outro = new Profissional();
      outro.setId(UUID.randomUUID());
      s.getProfissionais().add(outro);
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(s));

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Profissional nao atende o servico selecionado");
    }

    @Test
    @DisplayName("conflito sem override possivel: 'Profissional indisponivel neste horario.'")
    void conflitoSemOverride() {
      stubConflitoDeAgenda();
      when(appointmentSettingsService.allowsManualConflictByTenantId(tenantId)).thenReturn(false);

      assertThatThrownBy(() -> service.criar(requestValido()))
          .isInstanceOf(AppointmentConflictException.class)
          .hasMessage("Profissional indisponivel neste horario.");
      verify(agendamentoRepository, never()).saveAndFlush(any(Agendamento.class));
    }

    @Test
    @DisplayName("conflito com override permitido mas nao confirmado pede confirmacao explicita")
    void conflitoComOverridePossivelMasNaoConfirmado() {
      stubConflitoDeAgenda();
      when(appointmentSettingsService.allowsManualConflictByTenantId(tenantId)).thenReturn(true);
      AgendamentoRequest req = requestValido();
      req.origin = "INTERNAL_MANUAL";

      assertThatThrownBy(() -> service.criar(req))
          .isInstanceOf(AppointmentConflictException.class)
          .hasMessage("Horario ja utilizado para este profissional. Confirme o conflito para continuar.");
    }

    @Test
    @DisplayName("origem nao-manual nunca pode sobrepor, mesmo com a flag do tenant ligada")
    void origemNaoManualNuncaSobrepoe() {
      stubConflitoDeAgenda();
      when(appointmentSettingsService.allowsManualConflictByTenantId(tenantId)).thenReturn(true);
      AgendamentoRequest req = requestValido();
      req.origin = "WHATSAPP";
      req.allowConflict = Boolean.TRUE;
      req.conflictAcknowledged = Boolean.TRUE;

      assertThatThrownBy(() -> service.criar(req))
          .isInstanceOf(AppointmentConflictException.class)
          .hasMessage("Profissional indisponivel neste horario.");
    }

    @Test
    @DisplayName("override manual confirmado cria e audita como APPOINTMENT_CREATE_WITH_CONFLICT_OVERRIDE")
    void overrideManualConfirmadoCria() {
      stubConflitoDeAgenda();
      when(appointmentSettingsService.allowsManualConflictByTenantId(tenantId)).thenReturn(true);
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(agendamentoItemRepository.existsByAppointmentId(any())).thenReturn(false);
      AgendamentoRequest req = requestValido();
      req.origin = "INTERNAL_MANUAL";
      req.allowConflict = Boolean.TRUE;
      req.conflictAcknowledged = Boolean.TRUE;

      service.criar(req);

      ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
      verify(auditService).recordSuccess(captor.capture());
      assertThat(captor.getValue().action).isEqualTo("APPOINTMENT_CREATE_WITH_CONFLICT_OVERRIDE");
      assertThat(captor.getValue().metadata).isNotNull();
    }

    @Test
    @DisplayName("falha do WhatsApp nao derruba a criacao")
    void whatsappQuebradoNaoImpedeCriacao() {
      stubCriacaoSemConflito();
      org.mockito.Mockito.doThrow(new RuntimeException("gateway fora"))
          .when(whatsAppNotificationService)
          .sendConfirmation(eq(tenantId), any(Agendamento.class));

      AgendamentoResponse response = service.criar(requestValido());

      assertThat(response.id).isNotNull();
      verify(notificacaoService)
          .registrarCriacaoAgendamento(eq(tenantId), any(), eq(clientId), eq("APPOINTMENT_CREATED"));
    }

    private void stubConflitoDeAgenda() {
      when(tenantOperationalSettingsService.isClosedOnSpecialDate(eq(tenantId), any())).thenReturn(false);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
          .thenReturn(Optional.of(servico(30, "100.00")));
      when(tenantOperationalSettingsService.isBusinessOpenAt(eq(tenantId), any(), any(), any()))
          .thenReturn(true);
      when(specialClosureService.isClosedAt(eq(tenantId), eq(professionalId), any(), any(), any()))
          .thenReturn(false);
      when(profissionalWorkingHourRepository.listByProfessional(tenantId, professionalId))
          .thenReturn(List.of());
      Agendamento existente =
          agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.of(2030, 6, 10), "10:15", "10:45");
      when(agendamentoRepository.listActiveByProfessionalAndDate(
              eq(tenantId), eq(professionalId), any(), anyList()))
          .thenReturn(List.of(existente));
    }
  }

  // ─── TRANSICAO DE STATUS ──────────────────────────────────────────────────

  @Nested
  @DisplayName("atualizarStatus")
  class AtualizarStatus {

    @Test
    @DisplayName("concluir sem nenhuma nota operacional e bloqueado")
    void concluirExigeNotaOperacional() {
      Agendamento a = agendamentoExistente(StatusAgendamento.IN_PROGRESS, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.countByTenantIdAndAppointmentId(tenantId, a.getId()))
          .thenReturn(0L);

      assertThatThrownBy(() -> service.atualizarStatus(a.getId(), "COMPLETED"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Antes de concluir o atendimento, registre ao menos um detalhe operacional do cliente.");
      assertThat(a.getStatus()).isEqualTo(StatusAgendamento.IN_PROGRESS);
    }

    @Test
    @DisplayName("transicao invalida e recusada pela maquina de estados")
    void transicaoInvalida() {
      Agendamento a = agendamentoExistente(StatusAgendamento.PENDING, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

      assertThatThrownBy(() -> service.atualizarStatus(a.getId(), "COMPLETED"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Transicao de status invalida: PENDING -> COMPLETED");
    }

    @Test
    @DisplayName("concluir registra receita, comissao e consumo de insumo")
    void concluirRegistraEfeitosFinanceiros() {
      Agendamento a = agendamentoExistente(StatusAgendamento.IN_PROGRESS, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.countByTenantIdAndAppointmentId(tenantId, a.getId()))
          .thenReturn(1L);
      when(comandaRepository.existsByAppointmentIdAndTenantId(a.getId(), tenantId)).thenReturn(false);
      when(transacaoRepository.existsByTenantAndAppointmentAndType(
              tenantId, a.getId(), TipoTransacao.INCOME))
          .thenReturn(false);
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
          .thenReturn(Optional.of(servico(30, "100.00")));
      when(transactionCategoryRepository.findByTenantAndName(tenantId, "APPOINTMENT"))
          .thenReturn(Optional.empty());
      when(transactionCategoryRepository.save(any()))
          .thenAnswer(
              invocation -> {
                var c = (br.com.phdigitalcode.azzo.agenda.pro.entity.TransactionCategory)
                    invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
              });
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient()
          .when(comandaRepository.findFirstByAppointmentAndTenant(a.getId(), tenantId))
          .thenReturn(Optional.empty());

      service.atualizarStatus(a.getId(), "COMPLETED");

      ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
      verify(transacaoRepository).save(captor.capture());
      assertThat(captor.getValue().getType()).isEqualTo(TipoTransacao.INCOME);
      assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100.00");
      assertThat(captor.getValue().getDescription()).isEqualTo("Receita de agendamento - Corte");
      verify(commissionService)
          .registerServiceCommissionsIfApplicable(
              eq(tenantId), eq(a.getId()), eq(professionalId), any(), eq(a.getDate()));
      verify(estoqueMovimentacaoService)
          .consumirInsumosPorAgendamento(tenantId, a.getId(), List.of(serviceId));
    }

    @Test
    @DisplayName("comanda vinculada assume a receita: concluir nao lanca transacao duplicada")
    void concluirComComandaVinculadaNaoDuplicaReceita() {
      Agendamento a = agendamentoExistente(StatusAgendamento.IN_PROGRESS, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.countByTenantIdAndAppointmentId(tenantId, a.getId()))
          .thenReturn(1L);
      when(comandaRepository.existsByAppointmentIdAndTenantId(a.getId(), tenantId)).thenReturn(true);
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      service.atualizarStatus(a.getId(), "COMPLETED");

      verify(transacaoRepository, never()).save(any(Transacao.class));
    }

    @Test
    @DisplayName("sair de COMPLETED com comanda ja FECHADA e bloqueado")
    void naoSaiDeCompletedComComandaFechada() {
      Agendamento a = agendamentoExistente(StatusAgendamento.COMPLETED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      Comanda comanda = new Comanda();
      comanda.setId(UUID.randomUUID());
      comanda.setStatus(Comanda.STATUS_FECHADA);
      when(comandaRepository.findFirstByAppointmentAndTenant(a.getId(), tenantId))
          .thenReturn(Optional.of(comanda));

      assertThatThrownBy(() -> service.atualizarStatus(a.getId(), "CANCELLED"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("a comanda vinculada ja foi fechada");
      assertThat(a.getStatus()).isEqualTo(StatusAgendamento.COMPLETED);
    }

    @Test
    @DisplayName("sair de COMPLETED estorna a receita e reverte a comissao")
    void sairDeCompletedEstorna() {
      Agendamento a = agendamentoExistente(StatusAgendamento.COMPLETED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(comandaRepository.findFirstByAppointmentAndTenant(a.getId(), tenantId))
          .thenReturn(Optional.empty());
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      service.atualizarStatus(a.getId(), "CANCELLED");

      verify(transacaoRepository)
          .deleteByTenantAndAppointmentAndTypeAndCategoryName(
              tenantId, a.getId(), TipoTransacao.INCOME, "APPOINTMENT");
      verify(commissionService)
          .reverseServiceCommissionIfApplicable(
              tenantId, a.getId(), "Agendamento saiu do status COMPLETED");
    }

    @Test
    @DisplayName("iniciar o atendimento abre a comanda automatica e devolve o comandaId")
    void iniciarAbreComandaAutomatica() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      UUID comandaId = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(comandaRepository.existsByAppointmentIdAndTenantId(a.getId(), tenantId)).thenReturn(false);
      ComandaDtos.ComandaResponse comandaResponse = new ComandaDtos.ComandaResponse();
      comandaResponse.id = comandaId.toString();
      when(servicoComanda.abrir(any())).thenReturn(comandaResponse);
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      AgendamentoResponse response = service.atualizarStatus(a.getId(), "IN_PROGRESS");

      assertThat(response.comandaId).isEqualTo(comandaId.toString());
      ArgumentCaptor<ComandaDtos.AdicionarItemRequest> itemCaptor =
          ArgumentCaptor.forClass(ComandaDtos.AdicionarItemRequest.class);
      verify(servicoComanda).adicionarItem(eq(comandaId), itemCaptor.capture());
      assertThat(itemCaptor.getValue().tipo).isEqualTo("SERVICO");
      assertThat(itemCaptor.getValue().referenciaId).isEqualTo(serviceId.toString());
    }

    @Test
    @DisplayName("falha ao abrir a comanda automatica nao bloqueia o inicio do atendimento")
    void falhaAoAbrirComandaNaoBloqueiaInicio() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(comandaRepository.existsByAppointmentIdAndTenantId(a.getId(), tenantId)).thenReturn(false);
      when(servicoComanda.abrir(any())).thenThrow(new RuntimeException("PDV fora"));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      AgendamentoResponse response = service.atualizarStatus(a.getId(), "IN_PROGRESS");

      assertThat(response.status).isEqualTo("IN_PROGRESS");
      assertThat(response.comandaId).isNull();
    }

    @Test
    @DisplayName("cancelar cancela a comanda ABERTA e avisa o cliente no WhatsApp")
    void cancelarCancelaComandaAbertaENotifica() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      Comanda comanda = new Comanda();
      comanda.setId(UUID.randomUUID());
      comanda.setStatus(Comanda.STATUS_ABERTA);
      when(comandaRepository.findFirstByAppointmentAndTenant(a.getId(), tenantId))
          .thenReturn(Optional.of(comanda));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      service.atualizarStatus(a.getId(), "CANCELLED");

      verify(servicoComanda).cancelar(eq(comanda.getId()), any());
      verify(whatsAppNotificationService).sendCancellation(tenantId, a);
    }

    @Test
    @DisplayName("pagar agora abate o sinal ja pago antes de cobrar o restante")
    void pagarAgoraAbateSinal() {
      Agendamento a = agendamentoExistente(StatusAgendamento.IN_PROGRESS, LocalDate.now(ZONE_BR), "10:00", "10:30");
      UUID comandaId = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.countByTenantIdAndAppointmentId(tenantId, a.getId()))
          .thenReturn(1L);
      Comanda comanda = new Comanda();
      comanda.setId(comandaId);
      comanda.setStatus(Comanda.STATUS_ABERTA);
      when(comandaRepository.findFirstByAppointmentAndTenant(a.getId(), tenantId))
          .thenReturn(Optional.of(comanda));
      ComandaDtos.ComandaResponse comandaResponse = new ComandaDtos.ComandaResponse();
      comandaResponse.id = comandaId.toString();
      comandaResponse.status = Comanda.STATUS_ABERTA;
      comandaResponse.total = new BigDecimal("100.00");
      when(servicoComanda.obter(comandaId)).thenReturn(comandaResponse);
      AppointmentDeposit deposit = new AppointmentDeposit();
      deposit.setAmountCents(3000L);
      when(appointmentDepositRepository.findPaidUnusedByAppointmentId(a.getId()))
          .thenReturn(Optional.of(deposit));
      when(comandaRepository.existsByAppointmentIdAndTenantId(a.getId(), tenantId)).thenReturn(true);
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      service.atualizarStatus(a.getId(), "COMPLETED", "CASH", "PAY_NOW");

      ArgumentCaptor<ComandaDtos.RegistrarPagamentoRequest> captor =
          ArgumentCaptor.forClass(ComandaDtos.RegistrarPagamentoRequest.class);
      verify(servicoComanda, org.mockito.Mockito.times(2))
          .registrarPagamento(eq(comandaId), captor.capture());
      assertThat(captor.getAllValues().get(0).meio).isEqualTo(ComandaPagamento.MEIO_CREDITO_SINAL);
      assertThat(captor.getAllValues().get(0).valor).isEqualByComparingTo("30.00");
      assertThat(captor.getAllValues().get(1).meio).isEqualTo(ComandaPagamento.MEIO_DINHEIRO);
      assertThat(captor.getAllValues().get(1).valor).isEqualByComparingTo("70.00");
      verify(servicoComanda).fechar(comandaId);
    }

    @Test
    @DisplayName("pagar agora com PIX e recusado com orientacao explicita")
    void pagarAgoraComMeioNaoSuportado() {
      Agendamento a = agendamentoExistente(StatusAgendamento.IN_PROGRESS, LocalDate.now(ZONE_BR), "10:00", "10:30");
      UUID comandaId = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.countByTenantIdAndAppointmentId(tenantId, a.getId()))
          .thenReturn(1L);
      Comanda comanda = new Comanda();
      comanda.setId(comandaId);
      comanda.setStatus(Comanda.STATUS_ABERTA);
      when(comandaRepository.findFirstByAppointmentAndTenant(a.getId(), tenantId))
          .thenReturn(Optional.of(comanda));
      ComandaDtos.ComandaResponse comandaResponse = new ComandaDtos.ComandaResponse();
      comandaResponse.id = comandaId.toString();
      comandaResponse.status = Comanda.STATUS_ABERTA;
      comandaResponse.total = new BigDecimal("100.00");
      when(servicoComanda.obter(comandaId)).thenReturn(comandaResponse);
      when(appointmentDepositRepository.findPaidUnusedByAppointmentId(a.getId()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.atualizarStatus(a.getId(), "COMPLETED", "PIX", "PAY_NOW"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Forma de pagamento nao suportada para 'pagar agora': PIX");
    }
  }

  // ─── PRESENCA ─────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("presenca e no-show")
  class Presenca {

    @Test
    @DisplayName("no-show antes de 5 minutos do horario agendado e recusado")
    void noShowAntesDaJanela() {
      // Ancorado em amanha, nao em "agora + 1h": LocalTime.now().plusHours(1) da meia-noite para
      // tras vira 00:xx e, combinado com LocalDate.now(), aponta para o PASSADO de hoje — a janela
      // de no-show passa a estar aberta e o teste falha. Era um flake diario entre 23:00 e 00:00.
      Agendamento a =
          agendamentoExistente(
              StatusAgendamento.CONFIRMED,
              LocalDate.now(ZONE_BR).plusDays(1),
              "10:00",
              "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

      assertThatThrownBy(() -> service.registrarPresenca(a.getId(), false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Nao compareceu so pode ser marcado 5 minutos apos o horario agendado");
      assertThat(a.getStatus()).isEqualTo(StatusAgendamento.CONFIRMED);
    }

    @Test
    @DisplayName("no-show apos a janela marca NO_SHOW e dispara o aviso")
    void noShowAposJanela() {
      Agendamento a =
          agendamentoExistente(
              StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR).minusDays(1), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      AgendamentoResponse response = service.registrarPresenca(a.getId(), false);

      assertThat(response.status).isEqualTo("NO_SHOW");
      verify(whatsAppNotificationService).sendNoShow(a);
    }

    @Test
    @DisplayName("presenca confirmada promove PENDING para CONFIRMED")
    void presencaPromovePendingParaConfirmed() {
      Agendamento a = agendamentoExistente(StatusAgendamento.PENDING, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      AgendamentoResponse response = service.registrarPresenca(a.getId(), true);

      assertThat(response.status).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("profissional nao pode marcar presenca em agendamento de outro profissional")
    void profissionalSoMarcaOProprioAgendamento() {
      Agendamento a = agendamentoExistente(StatusAgendamento.PENDING, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(authenticatedUser.isProfessional()).thenReturn(true);
      when(authenticatedUser.idOuFalhar()).thenReturn(userId);
      Profissional outro = new Profissional();
      outro.setId(UUID.randomUUID());
      when(profissionalRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(outro));

      assertThatThrownBy(() -> service.registrarPresenca(a.getId(), true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Acesso negado: agendamento nao pertence ao profissional");
    }

    @Test
    @DisplayName("a fila de confirmacao mascara o telefone do cliente")
    void filaDeConfirmacaoMascaraTelefone() {
      Agendamento a =
          agendamentoExistente(
              StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR).minusDays(1), "10:00", "10:30");
      when(agendamentoRepository.listPendingAttendanceCandidates(
              eq(tenantId), anyList(), any(LocalDate.class), any(Pageable.class)))
          .thenReturn(List.of(a));
      Cliente cliente = new Cliente();
      cliente.setId(clientId);
      cliente.setName("Joao");
      cliente.setPhone("+55 11 98765-4321");
      when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));
      when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));

      List<PendingAttendanceResponse> pendentes = service.listarPendentesConfirmacaoPresenca();

      assertThat(pendentes).hasSize(1);
      assertThat(pendentes.get(0).clientPhone).isEqualTo("(**) *****-4321");
      assertThat(pendentes.get(0).professionalName).isEqualTo("Ana");
    }
  }

  // ─── EDICAO E REALOCACAO ──────────────────────────────────────────────────

  @Nested
  @DisplayName("edicao e realocacao")
  class EdicaoERealocacao {

    @Test
    @DisplayName("agendamento concluido nao pode ser editado")
    void naoEditaConcluido() {
      Agendamento a = agendamentoExistente(StatusAgendamento.COMPLETED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

      assertThatThrownBy(() -> service.atualizar(a.getId(), new AppointmentUpdateRequest()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Nao e permitido editar agendamento concluido ou cancelado");
    }

    @Test
    @DisplayName("editar so as notas nao dispara revalidacao de horario")
    void editarNotasNaoRevalidaHorario() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());
      AppointmentUpdateRequest req = new AppointmentUpdateRequest();
      req.notes = "  chegar 10 min antes  ";

      AgendamentoResponse response = service.atualizar(a.getId(), req);

      assertThat(response.notes).isEqualTo("chegar 10 min antes");
      verify(tenantOperationalSettingsService, never())
          .isBusinessOpenAt(any(), any(), any(), any());
    }

    @Test
    @DisplayName("realocacao para profissional que nao atende os servicos e recusada")
    void realocacaoParaProfissionalIncompativel() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      UUID novoProfId = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      Profissional novo = new Profissional();
      novo.setId(novoProfId);
      novo.setTenantId(tenantId);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(novoProfId, tenantId))
          .thenReturn(Optional.of(novo));
      Servico s = servico(30, "100.00");
      Profissional somenteOriginal = new Profissional();
      somenteOriginal.setId(professionalId);
      s.getProfissionais().add(somenteOriginal);
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(s));

      assertThatThrownBy(() -> service.realocarProfissional(a.getId(), novoProfId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Profissional nao atende um ou mais servicos do agendamento");
    }

    @Test
    @DisplayName("realocacao para o mesmo profissional e no-op")
    void realocacaoParaOMesmoProfissionalENoOp() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      service.realocarProfissional(a.getId(), professionalId);

      verify(auditService, never()).recordSuccess(any());
      verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }

    @Test
    @DisplayName("realocacao com conflito no novo profissional e recusada")
    void realocacaoComConflito() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      UUID novoProfId = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      Profissional novo = new Profissional();
      novo.setId(novoProfId);
      novo.setTenantId(tenantId);
      when(profissionalRepository.findByIdAndTenantIdAndIsActiveTrue(novoProfId, tenantId))
          .thenReturn(Optional.of(novo));
      when(servicoRepository.findByIdAndTenantId(serviceId, tenantId))
          .thenReturn(Optional.of(servico(30, "100.00")));
      Agendamento conflitante =
          agendamentoExistente(StatusAgendamento.CONFIRMED, a.getDate(), "10:15", "10:45");
      when(agendamentoRepository.listActiveByProfessionalAndDateExcluding(
              eq(tenantId), eq(novoProfId), eq(a.getDate()), eq(a.getId()), anyList()))
          .thenReturn(List.of(conflitante));

      assertThatThrownBy(() -> service.realocarProfissional(a.getId(), novoProfId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Profissional indisponivel neste horario");
    }

    @Test
    @DisplayName("agendamento concluido nao pode ser realocado")
    void naoRealocaConcluido() {
      Agendamento a = agendamentoExistente(StatusAgendamento.COMPLETED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

      assertThatThrownBy(() -> service.realocarProfissional(a.getId(), UUID.randomUUID()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Nao e permitido realocar agendamento concluido ou cancelado");
    }
  }

  // ─── NOTAS OPERACIONAIS ───────────────────────────────────────────────────

  @Nested
  @DisplayName("notas operacionais")
  class Notas {

    @Test
    @DisplayName("nota sem nenhum campo preenchido e recusada")
    void notaVazia() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      AppointmentCustomerNoteRequest req = new AppointmentCustomerNoteRequest();
      req.serviceExecutionNotes = "   ";

      assertThatThrownBy(() -> service.adicionarNotaCliente(a.getId(), req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Informe ao menos um detalhe operacional do atendimento");
    }

    @Test
    @DisplayName("texto da nota e trimado e truncado em 1000 caracteres")
    void notaTruncaEm1000() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(authenticatedUser.idOuNulo()).thenReturn(userId);
      AppointmentCustomerNoteRequest req = new AppointmentCustomerNoteRequest();
      req.serviceExecutionNotes = "  " + "a".repeat(1500) + "  ";

      AppointmentCustomerNoteResponse response = service.adicionarNotaCliente(a.getId(), req);

      assertThat(response.serviceExecutionNotes).hasSize(1000);
      assertThat(response.recordedByUserId).isEqualTo(userId.toString());
      assertThat(response.clientId).isEqualTo(clientId.toString());
    }

    @Test
    @DisplayName("nota de outro agendamento nao e encontrada")
    void notaDeOutroAgendamento() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      UUID noteId = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.findByIdAndTenantIdAndAppointmentId(
              noteId, tenantId, a.getId()))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deletarNotaCliente(a.getId(), noteId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Nota operacional nao encontrada");
    }
  }

  // ─── LISTAGEM, DETALHE E RELATORIOS ───────────────────────────────────────

  @Nested
  @DisplayName("listagem, detalhe e relatorios")
  class LeituraERelatorios {

    @Test
    @DisplayName("a pagina 1-indexed do frontend vira 0-indexed e o tamanho e limitado a 500")
    void paginacaoUmIndexadaELimiteDeTamanho() {
      when(agendamentoRepository.listByTenant(eq(tenantId), any(Pageable.class))).thenReturn(List.of());

      service.listar(null, 3, 9000);

      ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
      verify(agendamentoRepository).listByTenant(eq(tenantId), captor.capture());
      assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
      assertThat(captor.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("profissional autenticado sem cadastro na agenda enxerga lista vazia")
    void profissionalSemCadastroVeListaVazia() {
      when(authenticatedUser.isProfessional()).thenReturn(true);
      when(authenticatedUser.idOuFalhar()).thenReturn(userId);
      when(profissionalRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());

      assertThat(service.listar(null, 1, 20)).isEmpty();
      verify(agendamentoRepository, never()).listByTenant(any(), any());
    }

    @Test
    @DisplayName("mes fora de 1..12 e recusado nas metricas diarias")
    void mesInvalidoNasMetricas() {
      assertThatThrownBy(() -> service.listarMetricasDiarias(13, 2030))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Mes invalido. Informe valor entre 1 e 12");
    }

    @Test
    @DisplayName("detalhe traz notas e timeline traduzida")
    void detalheComTimeline() {
      Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMED, LocalDate.now(ZONE_BR), "10:00", "10:30");
      when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
      when(appointmentCustomerNoteRepository.findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(
              tenantId, a.getId()))
          .thenReturn(List.of());
      br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent evento =
          new br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent();
      evento.setId(UUID.randomUUID());
      evento.setAction("APPOINTMENT_CREATE");
      evento.setActorUserId(userId);
      evento.setCreatedAt(Instant.now());
      evento.setAfterJson("{\"status\":\"PENDING\"}");
      when(auditEventRepository.listByTenantAndEntity(
              eq(tenantId), eq("APPOINTMENT"), eq(a.getId().toString()), anyInt()))
          .thenReturn(List.of(evento));
      when(usuarioRepository.mapNamesByTenantAndIds(eq(tenantId), anyList()))
          .thenReturn(java.util.Map.of(userId, "Recepcao"));
      lenient().when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.empty());
      lenient()
          .when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.empty());
      lenient().when(servicoRepository.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.empty());

      AppointmentDetailResponse detalhe = service.obterDetalhe(a.getId());

      assertThat(detalhe.timeline).hasSize(1);
      assertThat(detalhe.timeline.get(0).actionLabel).isEqualTo("Agendamento criado");
      assertThat(detalhe.timeline.get(0).actorName).isEqualTo("Recepcao");
      assertThat(detalhe.timeline.get(0).after).isInstanceOf(java.util.Map.class);
    }

    @Test
    @DisplayName("agendamento de outro tenant nao e encontrado")
    void detalheDeOutroTenant() {
      UUID id = UUID.randomUUID();
      when(agendamentoRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.obterDetalhe(id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Agendamento nao encontrado");
    }

    @Test
    @DisplayName("relatorio gerencial calcula as taxas e levanta os alertas correspondentes")
    void relatorioGerencialTaxasEAlertas() {
      RelatorioRepository.AppointmentManagementSummaryRow summary =
          new RelatorioRepository.AppointmentManagementSummaryRow();
      summary.totalAppointments = 20;
      summary.totalConfirmed = 6;
      summary.totalCompleted = 4;
      summary.totalPending = 3;
      summary.totalCancelled = 4; // 20%
      summary.totalNoShow = 3; // 15%
      summary.totalRevenue = new BigDecimal("1000.00");
      summary.totalGapOpportunities = 2;
      summary.totalAbandonmentSignalDays = 1;
      when(relatorioRepository.obterResumoRelatorioAgendamentos(
              eq(tenantId), any(), any(), any(), any(), any()))
          .thenReturn(summary);
      when(relatorioRepository.findLastRefreshAt("mv_relatorio_agendamentos")).thenReturn(null);
      when(relatorioRepository.listarRelatorioAgendamentos(
              eq(tenantId), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(List.of());

      AppointmentManagementReportResponse response =
          service.listarRelatorioGerencial(
              LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31), null, null, null, null);

      assertThat(response.occupancyRate).isEqualTo(50.0);
      assertThat(response.cancellationRate).isEqualTo(20.0);
      assertThat(response.noShowRate).isEqualTo(15.0);
      assertThat(response.alerts)
          .extracting(alert -> alert.code)
          .containsExactly(
              "UNCONFIRMED_APPOINTMENTS", "HIGH_CANCELLATION_RATE", "HIGH_NO_SHOW_RATE");
      assertThat(response.opportunities)
          .extracting(item -> item.code)
          .containsExactly("GAP_OPPORTUNITIES", "ABANDONMENT_SIGNAL", "REACTIVATION_WINDOW");
    }

    @Test
    @DisplayName("periodo invertido e recusado no relatorio")
    void periodoInvertidoNoRelatorio() {
      assertThatThrownBy(
              () ->
                  service.listarRelatorioGerencial(
                      LocalDate.of(2030, 2, 1), LocalDate.of(2030, 1, 1), null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Periodo de filtro invalido");
    }

    @Test
    @DisplayName("status 'all' nao filtra; status nomeado vira a descricao em portugues da coluna")
    void filtroDeStatusUsaDescricaoDoBanco() {
      RelatorioRepository.AppointmentManagementSummaryRow summary =
          new RelatorioRepository.AppointmentManagementSummaryRow();
      summary.totalRevenue = BigDecimal.ZERO;
      when(relatorioRepository.obterResumoRelatorioAgendamentos(
              eq(tenantId), any(), any(), any(), any(), any()))
          .thenReturn(summary);
      when(relatorioRepository.listarRelatorioAgendamentos(
              eq(tenantId), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(List.of());

      service.listarRelatorioGerencial(
          LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31), null, null, "NO_SHOW", null);

      verify(relatorioRepository)
          .obterResumoRelatorioAgendamentos(
              tenantId,
              LocalDate.of(2030, 1, 1),
              LocalDate.of(2030, 1, 31),
              null,
              null,
              "Nao compareceu");
    }

    @Test
    @DisplayName("profissional autenticado so ve o proprio recorte, mesmo pedindo outro")
    void profissionalNaoEscapaDoProprioRecorte() {
      when(authenticatedUser.isProfessional()).thenReturn(true);
      when(authenticatedUser.idOuFalhar()).thenReturn(userId);
      when(profissionalRepository.findByTenantIdAndUserId(tenantId, userId))
          .thenReturn(Optional.of(profissional()));
      RelatorioRepository.AppointmentManagementSummaryRow summary =
          new RelatorioRepository.AppointmentManagementSummaryRow();
      summary.totalRevenue = BigDecimal.ZERO;
      when(relatorioRepository.obterResumoRelatorioAgendamentos(
              eq(tenantId), any(), any(), any(), any(), any()))
          .thenReturn(summary);
      when(relatorioRepository.listarRelatorioAgendamentos(
              eq(tenantId), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(List.of());

      service.listarRelatorioGerencial(
          LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31), UUID.randomUUID(), null, null, null);

      verify(relatorioRepository)
          .obterResumoRelatorioAgendamentos(
              eq(tenantId), any(), any(), eq(professionalId), any(), any());
    }

    @Test
    @DisplayName("no-show sem nenhum atendimento no periodo devolve taxa zero, sem divisao por zero")
    void noShowSemMovimentoNoPeriodo() {
      RelatorioRepository.NoShowSummaryRow summary = new RelatorioRepository.NoShowSummaryRow();
      summary.revenueAtRisk = BigDecimal.ZERO;
      when(relatorioRepository.obterResumoNoShowFiltrado(
              eq(tenantId), any(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(summary);
      when(relatorioRepository.listarSerieNoShowDiaria(
              eq(tenantId), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of());

      NoShowReportResponse response =
          service.listarNoShows(
              null, null, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31), null, null, null, null, null);

      assertThat(response.noShowRate).isZero();
      assertThat(response.groupBy).isEqualTo("DAY");
      assertThat(response.hasMore).isFalse();
      assertThat(response.items).isEmpty();
    }

    @Test
    @DisplayName("groupBy desconhecido e recusado")
    void groupByInvalido() {
      assertThatThrownBy(
              () ->
                  service.listarNoShows(
                      null, null, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31), null, null, null, null,
                      "MES"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("groupBy invalido. Use DAY, PROFESSIONAL, CLIENT ou SERVICE.");
    }

    @Test
    @DisplayName("CSV de no-show sai com BOM, cabecalho por agrupamento e celulas com aspas escapadas")
    void csvDeNoShowComBomECabecalho() throws Exception {
      RelatorioRepository.NoShowGroupedSummaryRow row =
          new RelatorioRepository.NoShowGroupedSummaryRow();
      row.key = professionalId.toString();
      row.label = "Ana \"a tesoura\"";
      row.totalNoShows = 3;
      row.revenueAtRisk = new BigDecimal("450.00");
      when(relatorioRepository.listarResumoNoShowAgrupadoPorProfissional(
              eq(tenantId), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(row));

      var stream =
          service.exportarNoShowsCsv(
              LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31), null, null, null, null, "PROFESSIONAL");
      var out = new java.io.ByteArrayOutputStream();
      stream.writeTo(out);
      String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);

      assertThat(csv).startsWith(((char) 0xFEFF) + "grupo,total_no_shows,receita_em_risco\n");
      assertThat(csv).contains("\"Ana \"\"a tesoura\"\"\",3,450.00");
    }

    @Test
    @DisplayName("historico do cliente agrupa as notas por agendamento e valida o periodo")
    void historicoDoCliente() {
      Cliente cliente = new Cliente();
      cliente.setId(clientId);
      cliente.setTenantId(tenantId);
      when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));
      Agendamento a = agendamentoExistente(StatusAgendamento.COMPLETED, LocalDate.of(2030, 1, 5), "10:00", "10:30");
      when(agendamentoQueryRepository.contarHistoricoClienteFiltrado(
              tenantId, clientId, null, null, null))
          .thenReturn(1L);
      when(agendamentoQueryRepository.listarHistoricoClienteFiltrado(
              tenantId, clientId, null, null, null, 0, 20))
          .thenReturn(List.of(a));
      AppointmentCustomerNote nota = new AppointmentCustomerNote();
      nota.setId(UUID.randomUUID());
      nota.setAppointmentId(a.getId());
      nota.setClientId(clientId);
      nota.setCreatedAt(Instant.now());
      nota.setServiceExecutionNotes("escova");
      when(appointmentCustomerNoteRepository.listByTenantAndAppointmentIds(
              eq(tenantId), anyList()))
          .thenReturn(List.of(nota));
      when(profissionalRepository.findByIdAndTenantId(professionalId, tenantId))
          .thenReturn(Optional.of(profissional()));

      var response = service.obterHistoricoCliente(clientId, null, null, null, null, null);

      assertThat(response.totalItems).isEqualTo(1);
      assertThat(response.size).isEqualTo(20);
      assertThat(response.items).hasSize(1);
      assertThat(response.items.get(0).professionalName).isEqualTo("Ana");
      assertThat(response.items.get(0).careNotes).hasSize(1);
      assertThat(response.items.get(0).services).hasSize(1);
    }

    @Test
    @DisplayName("historico com periodo invertido e recusado")
    void historicoComPeriodoInvertido() {
      Cliente cliente = new Cliente();
      cliente.setId(clientId);
      cliente.setTenantId(tenantId);
      when(clienteRepository.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(cliente));

      assertThatThrownBy(
              () ->
                  service.obterHistoricoCliente(
                      clientId, null, null, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 1, 1), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Periodo de filtro invalido");
    }
  }

  // ─── EXCLUSAO ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("excluir remove o agendamento e registra a auditoria com o snapshot anterior")
  void deletarRegistraAuditoria() {
    Agendamento a = agendamentoExistente(StatusAgendamento.PENDING, LocalDate.of(2030, 6, 10), "10:00", "10:30");
    when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

    service.deletar(a.getId());

    verify(agendamentoRepository).delete(a);
    ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
    verify(auditService).recordSuccess(captor.capture());
    assertThat(captor.getValue().action).isEqualTo("APPOINTMENT_DELETE");
    assertThat(captor.getValue().after).isNull();
    assertThat(captor.getValue().before).isNotNull();
  }

  @Test
  @DisplayName("excluir agendamento de outro tenant nao remove nada")
  void deletarDeOutroTenant() {
    UUID id = UUID.randomUUID();
    when(agendamentoRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deletar(id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agendamento nao encontrado");
    verify(agendamentoRepository, never()).delete(any(Agendamento.class));
  }

  @Test
  @DisplayName("a falha da auditoria nunca derruba o fluxo principal")
  void auditoriaQuebradaNaoDerrubaFluxo() {
    Agendamento a = agendamentoExistente(StatusAgendamento.PENDING, LocalDate.of(2030, 6, 10), "10:00", "10:30");
    when(agendamentoRepository.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));
    org.mockito.Mockito.doThrow(new RuntimeException("audit fora"))
        .when(auditService)
        .recordSuccess(any());

    service.deletar(a.getId());

    verify(agendamentoRepository).delete(a);
  }
}
