package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SettingsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailTemplateConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantBusinessHours;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailTemplateConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantBusinessHoursRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantSpecialClosureDateRepository;

/**
 * Cobre {@link TenantOperationalSettingsService}, que substituiu o placeholder
 * {@code integration/TenantOperationalSettingsService}.
 *
 * <p>O teste mais importante do arquivo e {@code getBusinessHourForDateUsaHorarioCustomizado}: e
 * exatamente o comportamento que o placeholder suprimia (devolvia sempre o horario padrao, fazendo
 * a agenda calcular slots errados para todo tenant que tivesse customizado o funcionamento).
 */
@ExtendWith(MockitoExtension.class)
class TenantOperationalSettingsServiceTest {

  private final UUID tenantId = UUID.randomUUID();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private TenantOperationalSettingsRepository repository;
  @Mock private TenantSpecialClosureDateRepository specialClosureDateRepository;
  @Mock private TenantBusinessHoursRepository businessHoursRepository;
  @Mock private EmailTemplateConfigRepository emailTemplateConfigRepository;
  @Mock private AuditService auditService;

  private EmailTemplateRendererService rendererService;
  private TenantOperationalSettingsService service;

  @BeforeEach
  void setUp() {
    rendererService =
        new EmailTemplateRendererService(
            emailTemplateConfigRepository, "no-reply@azzoholding.com.br", "Azzo Agenda Pro");
    service =
        new TenantOperationalSettingsService(
            repository,
            specialClosureDateRepository,
            businessHoursRepository,
            objectMapper,
            emailTemplateConfigRepository,
            rendererService,
            auditService);
    // saveAndFlush devolve a propria entidade, como o JPA faria com uma linha nova.
    lenient()
        .when(repository.saveAndFlush(any(TenantOperationalSettings.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  // ---------- criacao sob demanda ----------

  @Test
  @DisplayName("primeira leitura cria a linha de configuracao e devolve os defaults")
  void primeiraLeituraCriaLinha() {
    when(repository.findById(tenantId)).thenReturn(Optional.empty());

    SettingsDtos.SettingsResponse response = service.getOrCreateSettings(tenantId);

    ArgumentCaptor<TenantOperationalSettings> captor =
        ArgumentCaptor.forClass(TenantOperationalSettings.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);

    assertThat(response.notifications.reminderHours).isEqualTo(24);
    assertThat(response.reactivation.sendWindowStart).isEqualTo("09:00");
    assertThat(response.reactivation.maxAttemptsEnabled).isEqualTo(3);
    assertThat(response.businessHours).hasSize(7);
    assertThat(response.businessHours.get("sunday").enabled).isFalse();
    assertThat(response.businessHours.get("saturday").close).isEqualTo("17:00");
  }

  @Test
  @DisplayName("configuracao existente nao e recriada")
  void configuracaoExistenteNaoERecriada() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));

    service.getOrCreateSettings(tenantId);

    verify(repository, never()).saveAndFlush(any());
  }

  // ---------- o bug que o placeholder causava ----------

  @Test
  @DisplayName("getBusinessHourForDate devolve o horario CUSTOMIZADO do tenant, nao o padrao")
  void getBusinessHourForDateUsaHorarioCustomizado() throws Exception {
    TenantOperationalSettings entidade = novaEntidade();
    entidade.setBusinessHoursJson(
        objectMapper.writeValueAsString(
            List.of(hour("Segunda-feira", true, "07:30", "12:00"))));
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    // 2026-08-03 e uma segunda-feira
    SalonDtos.BusinessHour segunda =
        service.getBusinessHourForDate(tenantId, LocalDate.of(2026, 8, 3));

    assertThat(segunda).isNotNull();
    assertThat(segunda.enabled).isTrue();
    assertThat(segunda.open).isEqualTo("07:30");
    assertThat(segunda.close).isEqualTo("12:00");
  }

  @Test
  @DisplayName("dias nao informados no JSON caem no default, sem sumir da lista")
  void diasAusentesCaemNoDefault() throws Exception {
    TenantOperationalSettings entidade = novaEntidade();
    entidade.setBusinessHoursJson(
        objectMapper.writeValueAsString(List.of(hour("Segunda-feira", true, "07:30", "12:00"))));
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    // 2026-08-04 e uma terca-feira, ausente do JSON acima
    SalonDtos.BusinessHour terca =
        service.getBusinessHourForDate(tenantId, LocalDate.of(2026, 8, 4));

    assertThat(terca.open).isEqualTo("09:00");
    assertThat(terca.close).isEqualTo("19:00");
  }

  @Test
  @DisplayName("getBusinessHourForDate devolve copia — mutar a resposta nao altera a entidade")
  void getBusinessHourForDateDevolveCopia() {
    TenantOperationalSettings entidade = novaEntidade();
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    SalonDtos.BusinessHour primeira =
        service.getBusinessHourForDate(tenantId, LocalDate.of(2026, 8, 3));
    primeira.open = "MUTADO";

    SalonDtos.BusinessHour segunda =
        service.getBusinessHourForDate(tenantId, LocalDate.of(2026, 8, 3));
    assertThat(segunda.open).isEqualTo("09:00");
  }

  @Test
  @DisplayName("tenantId ou data nulos devolvem null sem tocar no repositorio")
  void argumentosNulosNaoConsultamRepositorio() {
    assertThat(service.getBusinessHourForDate(null, LocalDate.of(2026, 8, 3))).isNull();
    assertThat(service.getBusinessHourForDate(tenantId, null)).isNull();
    verify(repository, never()).findById(any());
  }

  @Test
  @DisplayName("JSON de horarios corrompido cai no default em vez de estourar")
  void jsonCorrompidoCaiNoDefault() {
    TenantOperationalSettings entidade = novaEntidade();
    entidade.setBusinessHoursJson("{isso nao e uma lista}");
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    SalonDtos.BusinessHour segunda =
        service.getBusinessHourForDate(tenantId, LocalDate.of(2026, 8, 3));

    assertThat(segunda.open).isEqualTo("09:00");
    assertThat(segunda.close).isEqualTo("19:00");
  }

  // ---------- notificacoes / reativacao ----------

  @Test
  @DisplayName("updateNotifications com request nulo: whatsapp fica ligado, email/sms desligados")
  void updateNotificationsComRequestNulo() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));

    SettingsDtos.NotificationSettings response = service.updateNotifications(tenantId, null);

    assertThat(response.emailNotifications).isFalse();
    assertThat(response.smsNotifications).isFalse();
    assertThat(response.whatsappNotifications).isTrue();
    assertThat(response.reminderHours).isEqualTo(24);
  }

  @Test
  @DisplayName("reminderHours zero ou negativo volta para 24")
  void reminderHoursInvalidoVoltaPara24() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));
    SettingsDtos.NotificationSettings request = new SettingsDtos.NotificationSettings();
    request.reminderHours = 0;

    assertThat(service.updateNotifications(tenantId, request).reminderHours).isEqualTo(24);
  }

  @Test
  @DisplayName("janela de reativacao ilegivel volta para 09:00-19:00 e maxAttempts e limitado a 1..3")
  void reactivationNormalizaJanelaEMaxAttempts() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));
    SettingsDtos.ReactivationSettings request = new SettingsDtos.ReactivationSettings();
    request.sendWindowStart = "nao-e-hora";
    request.sendWindowEnd = "25:99";
    request.maxAttemptsEnabled = 9;

    SettingsDtos.ReactivationSettings response = service.updateReactivation(tenantId, request);

    assertThat(response.sendWindowStart).isEqualTo("09:00");
    assertThat(response.sendWindowEnd).isEqualTo("19:00");
    assertThat(response.maxAttemptsEnabled).isEqualTo(3);
  }

  @Test
  @DisplayName("maxAttempts 2 continua 2; 0 vira 1 (faixa fechada do original)")
  void maxAttemptsRespeitaFaixa() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));

    SettingsDtos.ReactivationSettings dois = new SettingsDtos.ReactivationSettings();
    dois.maxAttemptsEnabled = 2;
    assertThat(service.updateReactivation(tenantId, dois).maxAttemptsEnabled).isEqualTo(2);

    SettingsDtos.ReactivationSettings zero = new SettingsDtos.ReactivationSettings();
    zero.maxAttemptsEnabled = 0;
    assertThat(service.updateReactivation(tenantId, zero).maxAttemptsEnabled).isEqualTo(1);
  }

  @Test
  @DisplayName("allowsReactivationAttemptNumber compara com o limite configurado")
  void allowsReactivationAttemptNumber() {
    TenantOperationalSettings entidade = novaEntidade();
    entidade.setReactivationMaxAttemptsEnabled(2);
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    assertThat(service.allowsReactivationAttemptNumber(tenantId, 2)).isTrue();
    assertThat(service.allowsReactivationAttemptNumber(tenantId, 3)).isFalse();
    // null e <= 0 sao tratados como a primeira tentativa
    assertThat(service.allowsReactivationAttemptNumber(tenantId, null)).isTrue();
    assertThat(service.allowsReactivationAttemptNumber(tenantId, -5)).isTrue();
  }

  // ---------- horarios via mapa ----------

  @Test
  @DisplayName("updateBusinessHours com mapa vazio grava os sete dias default")
  void updateBusinessHoursComMapaVazio() {
    TenantOperationalSettings entidade = novaEntidade();
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    Map<String, SettingsDtos.BusinessHoursDay> response =
        service.updateBusinessHours(tenantId, Map.of());

    assertThat(response).hasSize(7);
    assertThat(response.get("monday").open).isEqualTo("09:00");
    assertThat(entidade.getBusinessHoursJson()).contains("Segunda-feira");
  }

  @Test
  @DisplayName("chave de dia desconhecida cai em monday, sem estourar")
  void chaveDeDiaDesconhecidaCaiEmMonday() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));
    SettingsDtos.BusinessHoursDay dia = new SettingsDtos.BusinessHoursDay();
    dia.open = "10:00";
    dia.close = "16:00";
    dia.enabled = true;

    Map<String, SettingsDtos.BusinessHoursDay> response =
        service.updateBusinessHours(tenantId, Map.of("dia-inexistente", dia));

    assertThat(response.get("monday").open).isEqualTo("10:00");
    assertThat(response.get("monday").close).isEqualTo("16:00");
  }

  // ---------- tabela relacional ----------

  @Test
  @DisplayName("getBusinessHoursFromTable inicializa os sete dias quando a tabela esta vazia")
  void getBusinessHoursFromTableInicializaDefaults() {
    when(businessHoursRepository.findByTenantIdOrderByDayOfWeekAsc(tenantId))
        .thenReturn(List.of())
        .thenReturn(List.of(businessHours("MONDAY", "09:00", "19:00", true)));

    List<SettingsDtos.BusinessHoursItemResponse> response =
        service.getBusinessHoursFromTable(tenantId);

    verify(businessHoursRepository, org.mockito.Mockito.times(7))
        .save(any(TenantBusinessHours.class));
    verify(businessHoursRepository).flush();
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().dayOfWeek).isEqualTo("MONDAY");
  }

  @Test
  @DisplayName("isBusinessOpenAt respeita a pausa configurada na tabela relacional")
  void isBusinessOpenAtRespeitaPausa() {
    LocalDate segunda = LocalDate.of(2026, 8, 3);
    TenantBusinessHours hours = businessHours("MONDAY", "09:00", "19:00", true);
    hours.setBreakStart(LocalTime.of(12, 0));
    hours.setBreakEnd(LocalTime.of(13, 0));
    when(specialClosureDateRepository.existsAllDayClosure(tenantId, segunda, null))
        .thenReturn(false);
    when(businessHoursRepository.findByTenantIdAndDayOfWeek(tenantId, "MONDAY"))
        .thenReturn(Optional.of(hours));

    assertThat(
            service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(10, 0), LocalTime.of(11, 0)))
        .isTrue();
    // comeca dentro da pausa
    assertThat(
            service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(12, 30), LocalTime.of(13, 30)))
        .isFalse();
    // atravessa a pausa inteira
    assertThat(
            service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(11, 0), LocalTime.of(14, 0)))
        .isFalse();
  }

  @Test
  @DisplayName("fechamento especial no dia fecha a agenda antes de olhar horario")
  void fechamentoEspecialFechaODia() {
    LocalDate segunda = LocalDate.of(2026, 8, 3);
    when(specialClosureDateRepository.existsAllDayClosure(tenantId, segunda, null)).thenReturn(true);

    assertThat(
            service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(10, 0), LocalTime.of(11, 0)))
        .isFalse();
    verify(businessHoursRepository, never()).findByTenantIdAndDayOfWeek(any(), any());
  }

  @Test
  @DisplayName("tabela relacional vazia cai no JSON legado")
  void tabelaVaziaCaiNoLegado() throws Exception {
    LocalDate segunda = LocalDate.of(2026, 8, 3);
    TenantOperationalSettings entidade = novaEntidade();
    entidade.setBusinessHoursJson(
        objectMapper.writeValueAsString(List.of(hour("Segunda-feira", true, "08:00", "12:00"))));
    when(specialClosureDateRepository.existsAllDayClosure(tenantId, segunda, null))
        .thenReturn(false);
    when(businessHoursRepository.findByTenantIdAndDayOfWeek(tenantId, "MONDAY"))
        .thenReturn(Optional.empty());
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));

    assertThat(service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(8, 30), LocalTime.of(9, 0)))
        .isTrue();
    assertThat(
            service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(12, 30), LocalTime.of(13, 0)))
        .isFalse();
  }

  @Test
  @DisplayName("intervalo invertido ou nulo nunca conta como aberto")
  void intervaloInvalidoNaoAbre() {
    LocalDate segunda = LocalDate.of(2026, 8, 3);
    assertThat(service.isBusinessOpenAt(tenantId, segunda, LocalTime.of(11, 0), LocalTime.of(10, 0)))
        .isFalse();
    assertThat(service.isBusinessOpenAt(tenantId, segunda, null, LocalTime.of(10, 0))).isFalse();
    assertThat(service.isBusinessOpenAt(null, segunda, LocalTime.of(9, 0), LocalTime.of(10, 0)))
        .isFalse();
    verify(specialClosureDateRepository, never()).existsAllDayClosure(any(), any(), any());
  }

  // ---------- feature flags, cancelamento e lembretes ----------

  @Test
  @DisplayName("updateFeatureFlags ignora nulos e valores nao positivos")
  void updateFeatureFlagsIgnoraNulosENaoPositivos() {
    TenantOperationalSettings entidade = novaEntidade();
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));
    SettingsDtos.TenantFeatureFlagsRequest request =
        new SettingsDtos.TenantFeatureFlagsRequest();
    request.asaasEnabled = true;
    request.minioEnabled = null;
    request.chatRetentionDaysCompleted = 0; // ignorado
    request.auditRetentionDays = 30;

    SettingsDtos.TenantFeatureFlagsResponse response =
        service.updateFeatureFlags(tenantId, request);

    assertThat(response.asaasEnabled).isTrue();
    assertThat(response.minioEnabled).isFalse();
    assertThat(response.chatRetentionDaysCompleted).isEqualTo(180);
    assertThat(response.auditRetentionDays).isEqualTo(30);
  }

  @Test
  @DisplayName("percentual de multa fora de 0..100 e ignorado")
  void percentualDeMultaForaDaFaixaEIgnorado() {
    when(repository.findById(tenantId)).thenReturn(Optional.of(novaEntidade()));
    SettingsDtos.CancellationPolicyRequest request =
        new SettingsDtos.CancellationPolicyRequest();
    request.cancellationFeePercent = 150;
    request.cancellationMinHoursBefore = 48;

    SettingsDtos.CancellationPolicyResponse response =
        service.updateCancellationPolicy(tenantId, request);

    assertThat(response.cancellationFeePercent).isZero();
    assertThat(response.cancellationMinHoursBefore).isEqualTo(48);
  }

  @Test
  @DisplayName("regua de lembretes valida HH:mm e a faixa 1..12 de horasAntes")
  void reminderSettingsValidaFormatoEFaixa() {
    TenantOperationalSettings entidade = novaEntidade();
    when(repository.findById(tenantId)).thenReturn(Optional.of(entidade));
    SettingsDtos.ReminderSettingsRequest request = new SettingsDtos.ReminderSettingsRequest();
    request.d1Hora = "26:00"; // invalido, mantem o default
    request.horasAntes = 20; // fora de 1..12, mantem o default

    SettingsDtos.ReminderSettingsResponse response =
        service.updateReminderSettings(tenantId, request);

    assertThat(response.d1Hora).isEqualTo("18:00");
    assertThat(response.horasAntes).isEqualTo(24);

    request.d1Hora = "07:15";
    request.horasAntes = 6;
    SettingsDtos.ReminderSettingsResponse valido =
        service.updateReminderSettings(tenantId, request);
    assertThat(valido.d1Hora).isEqualTo("07:15");
    assertThat(valido.horasAntes).isEqualTo(6);
  }

  // ---------- templates de e-mail ----------

  @Test
  @DisplayName("sem template do tenant, responde com o global e tenantCustomized=false")
  void templateCaiNoGlobalQuandoTenantNaoCustomizou() {
    EmailTemplateConfig global = new EmailTemplateConfig();
    global.setTemplateType(EmailTemplateType.PASSWORD_RESET);
    global.setSubjectTemplate("Assunto global");
    global.setHtmlTemplate("<p>global</p>");
    when(emailTemplateConfigRepository.findFirstByTenantIdAndTemplateType(
            tenantId, EmailTemplateType.PASSWORD_RESET))
        .thenReturn(Optional.empty());
    // findGlobalByType e um metodo default da interface: no mock ele NAO delega para a
    // implementacao padrao (so o proxy do Spring Data faz isso), entao a stub tem que ser nele.
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET))
        .thenReturn(Optional.of(global));

    SettingsDtos.EmailTemplateResponse response =
        service.getEmailTemplate(tenantId, "password_reset");

    assertThat(response.type).isEqualTo("PASSWORD_RESET");
    assertThat(response.label).isEqualTo("Redefinicao de senha");
    assertThat(response.tenantCustomized).isFalse();
    assertThat(response.subjectTemplate).isEqualTo("Assunto global");
  }

  @Test
  @DisplayName("update de template sanitiza o HTML e marca o template como ativo")
  void updateDeTemplateSanitizaHtml() {
    when(emailTemplateConfigRepository.findFirstByTenantIdAndTemplateType(
            tenantId, EmailTemplateType.PASSWORD_RESET))
        .thenReturn(Optional.empty());
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET))
        .thenReturn(Optional.empty());
    when(emailTemplateConfigRepository.saveAndFlush(any(EmailTemplateConfig.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SettingsDtos.EmailTemplateRequest request = new SettingsDtos.EmailTemplateRequest();
    request.subjectTemplate = "Novo assunto";
    request.htmlTemplate = "<p>ok</p><script>alert(1)</script>";

    service.updateEmailTemplate(tenantId, "PASSWORD_RESET", request);

    ArgumentCaptor<EmailTemplateConfig> captor =
        ArgumentCaptor.forClass(EmailTemplateConfig.class);
    verify(emailTemplateConfigRepository).saveAndFlush(captor.capture());
    EmailTemplateConfig salvo = captor.getValue();
    assertThat(salvo.getTenantId()).isEqualTo(tenantId);
    assertThat(salvo.getSubjectTemplate()).isEqualTo("Novo assunto");
    assertThat(salvo.getHtmlTemplate()).isEqualTo("<p>ok</p>");
    assertThat(salvo.isActive()).isTrue();
  }

  @Test
  @DisplayName("tipo de template desconhecido tem mensagem propria")
  void tipoDeTemplateDesconhecido() {
    assertThatThrownBy(() -> service.getEmailTemplate(tenantId, "INEXISTENTE"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Tipo de template invalido: INEXISTENTE");
  }

  // ---------- fechamentos especiais (visao legada do salao) ----------

  @Test
  @DisplayName("updateSpecialClosureDates deduplica por data, ordena e da flush antes de reler")
  void updateSpecialClosureDatesDeduplicaEOrdena() {
    when(specialClosureDateRepository.findByTenantIdOrderByClosureDateAsc(tenantId))
        .thenReturn(List.of())
        .thenReturn(List.of());

    SalonDtos.SpecialClosureDate natal = new SalonDtos.SpecialClosureDate();
    natal.date = "2026-12-25";
    natal.reason = "  Natal  ";
    SalonDtos.SpecialClosureDate natalDuplicado = new SalonDtos.SpecialClosureDate();
    natalDuplicado.date = "2026-12-25";
    natalDuplicado.reason = "   ";
    SalonDtos.SpecialClosureDate anoNovo = new SalonDtos.SpecialClosureDate();
    anoNovo.date = "2026-01-01";

    service.updateSpecialClosureDates(tenantId, List.of(natal, natalDuplicado, anoNovo));

    // duas datas distintas -> dois saves; a duplicata sobrescreve o reason para null (blank)
    verify(specialClosureDateRepository, org.mockito.Mockito.times(2)).save(any());
    verify(specialClosureDateRepository).flush();
  }

  @Test
  @DisplayName("data de fechamento ausente ou ilegivel tem mensagens distintas")
  void datasDeFechamentoInvalidas() {
    SalonDtos.SpecialClosureDate semData = new SalonDtos.SpecialClosureDate();
    assertThatThrownBy(() -> service.updateSpecialClosureDates(tenantId, List.of(semData)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Data especial de fechamento obrigatoria");

    SalonDtos.SpecialClosureDate dataRuim = new SalonDtos.SpecialClosureDate();
    dataRuim.date = "25/12/2026";
    assertThatThrownBy(() -> service.updateSpecialClosureDates(tenantId, List.of(dataRuim)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Data especial de fechamento invalida");
  }

  @Test
  @DisplayName("isClosedOnSpecialDate delega para o exists all-day sem profissional")
  void isClosedOnSpecialDateDelegaParaAllDay() {
    LocalDate data = LocalDate.of(2026, 12, 25);
    when(specialClosureDateRepository.existsAllDayClosure(tenantId, data, null)).thenReturn(true);

    assertThat(service.isClosedOnSpecialDate(tenantId, data)).isTrue();
    assertThat(service.isClosedOnSpecialDate(null, data)).isFalse();
    assertThat(service.isClosedOnSpecialDate(tenantId, null)).isFalse();
    verify(specialClosureDateRepository).existsAllDayClosure(eq(tenantId), eq(data), eq(null));
  }

  // ---------- helpers ----------

  private TenantOperationalSettings novaEntidade() {
    TenantOperationalSettings entidade = new TenantOperationalSettings();
    entidade.setTenantId(tenantId);
    return entidade;
  }

  private static SalonDtos.BusinessHour hour(
      String day, boolean enabled, String open, String close) {
    SalonDtos.BusinessHour hour = new SalonDtos.BusinessHour();
    hour.day = day;
    hour.enabled = enabled;
    hour.open = open;
    hour.close = close;
    return hour;
  }

  private static TenantBusinessHours businessHours(
      String dayOfWeek, String open, String close, boolean enabled) {
    TenantBusinessHours hours = new TenantBusinessHours();
    hours.setId(UUID.randomUUID());
    hours.setDayOfWeek(dayOfWeek);
    hours.setOpenTime(LocalTime.parse(open));
    hours.setCloseTime(LocalTime.parse(close));
    hours.setEnabled(enabled);
    return hours;
  }
}
