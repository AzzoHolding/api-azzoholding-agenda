package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SettingsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailTemplateConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantBusinessHours;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantSpecialClosureDate;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailTemplateConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantBusinessHoursRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantSpecialClosureDateRepository;

/**
 * Espelha {@code modules/settings/application/TenantOperationalSettingsService.java}.
 *
 * <p><b>Substitui o placeholder</b> {@code integration/TenantOperationalSettingsService}, que
 * devolvia sempre o horario de funcionamento padrao e por isso calculava slots errados para todo
 * tenant que tivesse customizado o horario.
 *
 * <p><b>Nenhum metodo e {@code readOnly}</b> — todos passam por {@link #findByTenantIdOrCreate},
 * que <b>insere</b> a linha de configuracao na primeira leitura. Marcar qualquer um como
 * {@code readOnly = true} quebraria essa criacao sob demanda (mesma razao ja documentada em
 * {@code AppointmentSettingsService}).
 *
 * <p>Duas fontes de horario de funcionamento convivem no original e foram preservadas: o JSON
 * legado em {@code tenant_operational_settings.business_hours_json} e a tabela relacional
 * {@code tenant_business_hours} (V75/V76). {@code updateBusinessHoursList} escreve nas duas
 * (sincronizacao bidirecional da transicao); {@code getBusinessHourForDate} — o metodo que a agenda
 * consome — le <b>do JSON legado</b>, nao da tabela. Assimetria do original, mantida.
 */
@Service
public class TenantOperationalSettingsService {

  private static final Logger LOG = LoggerFactory.getLogger(TenantOperationalSettingsService.class);
  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  private static final TypeReference<List<SalonDtos.BusinessHour>> BUSINESS_HOURS_TYPE =
      new TypeReference<>() {};

  private final TenantOperationalSettingsRepository repository;
  private final TenantSpecialClosureDateRepository specialClosureDateRepository;
  private final TenantBusinessHoursRepository businessHoursRepository;
  private final ObjectMapper objectMapper;
  private final EmailTemplateConfigRepository emailTemplateConfigRepository;
  private final EmailTemplateRendererService emailTemplateRendererService;
  private final AuditService auditService;

  public TenantOperationalSettingsService(
      TenantOperationalSettingsRepository repository,
      TenantSpecialClosureDateRepository specialClosureDateRepository,
      TenantBusinessHoursRepository businessHoursRepository,
      ObjectMapper objectMapper,
      EmailTemplateConfigRepository emailTemplateConfigRepository,
      EmailTemplateRendererService emailTemplateRendererService,
      AuditService auditService) {
    this.repository = repository;
    this.specialClosureDateRepository = specialClosureDateRepository;
    this.businessHoursRepository = businessHoursRepository;
    this.objectMapper = objectMapper;
    this.emailTemplateConfigRepository = emailTemplateConfigRepository;
    this.emailTemplateRendererService = emailTemplateRendererService;
    this.auditService = auditService;
  }

  @Transactional
  public SettingsDtos.SettingsResponse getOrCreateSettings(UUID tenantId) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    return toResponse(entity);
  }

  @Transactional
  public SettingsDtos.NotificationSettings updateNotifications(
      UUID tenantId, SettingsDtos.NotificationSettings request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    entity.setEmailNotifications(request != null && request.emailNotifications);
    entity.setSmsNotifications(request != null && request.smsNotifications);
    entity.setWhatsappNotifications(request == null || request.whatsappNotifications);
    entity.setReminderHours(
        request != null && request.reminderHours > 0 ? request.reminderHours : 24);
    return toNotificationSettings(entity);
  }

  @Transactional
  public SettingsDtos.ReactivationSettings updateReactivation(
      UUID tenantId, SettingsDtos.ReactivationSettings request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    entity.setReactivationEnabled(request == null || request.enabled);
    entity.setReactivationRespectBusinessHours(request == null || request.respectBusinessHours);
    entity.setReactivationSendWindowStart(
        normalizeTime(request != null ? request.sendWindowStart : null, "09:00"));
    entity.setReactivationSendWindowEnd(
        normalizeTime(request != null ? request.sendWindowEnd : null, "19:00"));
    entity.setReactivationMaxAttemptsEnabled(
        normalizeMaxAttemptsEnabled(request != null ? request.maxAttemptsEnabled : 3));
    return toReactivationSettings(entity);
  }

  @Transactional
  public Map<String, SettingsDtos.BusinessHoursDay> updateBusinessHours(
      UUID tenantId, Map<String, SettingsDtos.BusinessHoursDay> request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    entity.setBusinessHoursJson(serializeBusinessHours(fromBusinessHoursMap(request)));
    return toBusinessHoursMap(entity);
  }

  @Transactional
  public List<SalonDtos.BusinessHour> updateBusinessHoursList(
      UUID tenantId, List<SalonDtos.BusinessHour> request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    List<SalonDtos.BusinessHour> normalized = normalizeBusinessHours(request);
    entity.setBusinessHoursJson(serializeBusinessHours(normalized));
    // Sincronizacao bidirecional: manter tabela relacional atualizada durante transicao
    syncBusinessHoursToTable(tenantId, normalized);
    return readBusinessHours(entity);
  }

  /**
   * Sincroniza os horarios do JSON legado na tabela relacional {@code tenant_business_hours}.
   * Chamado sempre que o JSON legado e atualizado via {@code PUT /salon/profile}.
   */
  private void syncBusinessHoursToTable(UUID tenantId, List<SalonDtos.BusinessHour> hours) {
    if (hours == null || hours.isEmpty()) return;
    List<SettingsDtos.BusinessHoursItemRequest> items = new ArrayList<>();
    for (SalonDtos.BusinessHour hour : hours) {
      SettingsDtos.BusinessHoursItemRequest item = new SettingsDtos.BusinessHoursItemRequest();
      item.dayOfWeek = toBusinessHoursKey(hour.day).toUpperCase(Locale.ROOT); // MONDAY, TUESDAY...
      item.openTime = hour.open;
      item.closeTime = hour.close;
      item.enabled = hour.enabled;
      items.add(item);
    }
    updateBusinessHoursInTable(tenantId, items);
  }

  /**
   * @deprecated Substituido por {@link #getBusinessHoursFromTable(UUID)} (tabela relacional
   *     {@code tenant_business_hours}). Mantido para rollback, exatamente como no original.
   */
  @Deprecated(forRemoval = true)
  @Transactional
  public List<SalonDtos.BusinessHour> getBusinessHours(UUID tenantId) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    return readBusinessHours(entity);
  }

  @Transactional
  public List<SalonDtos.SpecialClosureDate> getSpecialClosureDates(UUID tenantId) {
    return specialClosureDateRepository.findByTenantIdOrderByClosureDateAsc(tenantId).stream()
        .map(this::toSpecialClosureDateDto)
        .toList();
  }

  @Transactional
  public List<SalonDtos.SpecialClosureDate> updateSpecialClosureDates(
      UUID tenantId, List<SalonDtos.SpecialClosureDate> request) {
    List<NormalizedSpecialClosureDate> normalized = normalizeSpecialClosureDates(request);
    Map<LocalDate, TenantSpecialClosureDate> existingByDate = new LinkedHashMap<>();
    for (TenantSpecialClosureDate entity :
        specialClosureDateRepository.findByTenantIdOrderByClosureDateAsc(tenantId)) {
      existingByDate.put(entity.getClosureDate(), entity);
    }

    for (TenantSpecialClosureDate entity : new ArrayList<>(existingByDate.values())) {
      boolean keep =
          normalized.stream().anyMatch(item -> item.date().equals(entity.getClosureDate()));
      if (!keep) {
        specialClosureDateRepository.delete(entity);
      }
    }

    for (NormalizedSpecialClosureDate item : normalized) {
      TenantSpecialClosureDate entity = existingByDate.get(item.date());
      if (entity == null) {
        entity = new TenantSpecialClosureDate();
        entity.setTenantId(tenantId);
        entity.setClosureDate(item.date());
        entity.setReason(item.reason());
        specialClosureDateRepository.save(entity);
      } else {
        entity.setReason(item.reason());
      }
    }

    // O original relê a tabela logo apos escrever; com Panache o INSERT/DELETE ja saiu no
    // persist()/delete(), com Spring Data ele so sairia no commit — sem este flush a releitura
    // devolveria o estado antigo (armadilha 2 do briefing de migracao).
    specialClosureDateRepository.flush();
    return getSpecialClosureDates(tenantId);
  }

  @Transactional
  public boolean isClosedOnSpecialDate(UUID tenantId, LocalDate date) {
    if (tenantId == null || date == null) return false;
    // Usa o metodo relacional que verifica all_day (sem profissional especifico)
    return specialClosureDateRepository.existsAllDayClosure(tenantId, date, null);
  }

  @Transactional
  public boolean isBusinessOpenAt(UUID tenantId, LocalDate date, LocalTime start, LocalTime end) {
    if (tenantId == null || date == null || start == null || end == null || !start.isBefore(end)) {
      return false;
    }
    if (isClosedOnSpecialDate(tenantId, date)) {
      return false;
    }

    String dayName = date.getDayOfWeek().name(); // "MONDAY", "TUESDAY", etc.
    Optional<TenantBusinessHours> hoursOpt = findBusinessHoursByTenantAndDay(tenantId, dayName);

    // fallback para JSON legado se tabela ainda nao tiver dados
    if (hoursOpt.isEmpty()) {
      LOG.warn(
          "isBusinessOpenAt: tabela tenant_business_hours sem dados para tenantId={} — usando"
              + " fallback JSON legado. Executar migracao.",
          tenantId);
      return isBusinessOpenAtLegacy(tenantId, date, start, end);
    }

    TenantBusinessHours hours = hoursOpt.get();
    if (!hours.isEnabled()) return false;
    if (hours.getOpenTime() == null || hours.getCloseTime() == null) return false;
    if (!hours.getOpenTime().isBefore(hours.getCloseTime())) return false;
    if (start.isBefore(hours.getOpenTime())) return false;
    if (end.isAfter(hours.getCloseTime())) return false;

    // Validar pausa
    if (hours.getBreakStart() != null
        && hours.getBreakEnd() != null
        && hours.getBreakStart().isBefore(hours.getBreakEnd())) {
      boolean startInBreak =
          !start.isBefore(hours.getBreakStart()) && start.isBefore(hours.getBreakEnd());
      boolean endInBreak =
          end.isAfter(hours.getBreakStart()) && !end.isAfter(hours.getBreakEnd());
      boolean spansBreak =
          start.isBefore(hours.getBreakStart()) && end.isAfter(hours.getBreakEnd());
      if (startInBreak || endInBreak || spansBreak) return false;
    }

    return true;
  }

  /**
   * @deprecated Substituido por {@code tenant_business_hours} (tabela relacional). Mantido para
   *     rollback, como no original.
   */
  @Deprecated(forRemoval = true)
  private boolean isBusinessOpenAtLegacy(
      UUID tenantId, LocalDate date, LocalTime start, LocalTime end) {
    SalonDtos.BusinessHour businessHour = getBusinessHourForDate(tenantId, date);
    if (businessHour == null || !businessHour.enabled) {
      return false;
    }
    LocalTime open = parseTime(businessHour.open, LocalTime.of(9, 0));
    LocalTime close = parseTime(businessHour.close, LocalTime.of(19, 0));
    if (!open.isBefore(close)) {
      return false;
    }
    return !start.isBefore(open) && !end.isAfter(close);
  }

  /**
   * {@code protected} no original para permitir sobrescrita em teste — mantido, embora aqui a
   * consulta ja seja injetavel pelo repositorio.
   */
  protected Optional<TenantBusinessHours> findBusinessHoursByTenantAndDay(
      UUID tenantId, String dayName) {
    return businessHoursRepository.findByTenantIdAndDayOfWeek(tenantId, dayName);
  }

  /**
   * Ponto de entrada consumido pelo motor de slots ({@code AppointmentService}) para recortar a
   * agenda do profissional pela janela de funcionamento do salao.
   */
  @Transactional
  public SalonDtos.BusinessHour getBusinessHourForDate(UUID tenantId, LocalDate date) {
    if (tenantId == null || date == null) return null;
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    SalonDtos.BusinessHour businessHour =
        findBusinessHourForDay(readBusinessHours(entity), date.getDayOfWeek());
    return businessHour == null ? null : copyHour(businessHour);
  }

  @Transactional
  public boolean isReactivationEnabled(UUID tenantId) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    return entity.isReactivationEnabled();
  }

  @Transactional
  public boolean allowsReactivationAt(UUID tenantId, Instant reference) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    if (!entity.isReactivationEnabled()) return false;

    ZonedDateTime now = (reference == null ? Instant.now() : reference).atZone(ZONA_BR);
    LocalTime currentTime = now.toLocalTime();
    LocalTime windowStart = parseTime(entity.getReactivationSendWindowStart(), LocalTime.of(9, 0));
    LocalTime windowEnd = parseTime(entity.getReactivationSendWindowEnd(), LocalTime.of(19, 0));
    if (!isWithinWindow(currentTime, windowStart, windowEnd)) return false;

    if (!entity.isReactivationRespectBusinessHours()) return true;

    SalonDtos.BusinessHour dayHours =
        findBusinessHourForDay(readBusinessHours(entity), now.getDayOfWeek());
    if (dayHours == null || !dayHours.enabled) return false;
    LocalTime open = parseTime(dayHours.open, LocalTime.of(9, 0));
    LocalTime close = parseTime(dayHours.close, LocalTime.of(19, 0));
    return isWithinWindow(currentTime, open, close);
  }

  @Transactional
  public boolean allowsReactivationAttemptNumber(UUID tenantId, Integer attemptNumber) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    ensureDefaults(entity);
    int normalizedAttemptNumber = attemptNumber == null || attemptNumber <= 0 ? 1 : attemptNumber;
    return normalizedAttemptNumber <= entity.getReactivationMaxAttemptsEnabled();
  }

  // ============================================================
  // Criacao sob demanda
  // ============================================================

  /**
   * Equivalente a {@code TenantOperationalSettingsRepository.findByTenantIdOrCreate} do original.
   *
   * <p>Usa {@code saveAndFlush} porque o {@code persist()} do Panache emite o INSERT na hora,
   * enquanto o {@code save()} do Spring Data adiaria ate o commit — e varios metodos deste service
   * releem a linha logo depois (armadilha 2). O {@code @PrePersist} da entidade tambem so roda no
   * flush, e o {@link #ensureDefaults} de quem chama depende dos valores que ele normaliza.
   */
  private TenantOperationalSettings findByTenantIdOrCreate(UUID tenantId) {
    return repository
        .findById(tenantId)
        .orElseGet(
            () -> {
              TenantOperationalSettings created = new TenantOperationalSettings();
              created.setTenantId(tenantId);
              return repository.saveAndFlush(created);
            });
  }

  // ============================================================
  // Mapeamento
  // ============================================================

  private SettingsDtos.SettingsResponse toResponse(TenantOperationalSettings entity) {
    SettingsDtos.SettingsResponse response = new SettingsDtos.SettingsResponse();
    response.notifications = toNotificationSettings(entity);
    response.reactivation = toReactivationSettings(entity);
    response.businessHours = toBusinessHoursMap(entity);
    return response;
  }

  private SettingsDtos.NotificationSettings toNotificationSettings(
      TenantOperationalSettings entity) {
    SettingsDtos.NotificationSettings response = new SettingsDtos.NotificationSettings();
    response.emailNotifications = entity.isEmailNotifications();
    response.smsNotifications = entity.isSmsNotifications();
    response.whatsappNotifications = entity.isWhatsappNotifications();
    response.reminderHours = entity.getReminderHours();
    return response;
  }

  private SettingsDtos.ReactivationSettings toReactivationSettings(
      TenantOperationalSettings entity) {
    SettingsDtos.ReactivationSettings response = new SettingsDtos.ReactivationSettings();
    response.enabled = entity.isReactivationEnabled();
    response.respectBusinessHours = entity.isReactivationRespectBusinessHours();
    response.sendWindowStart = normalizeTime(entity.getReactivationSendWindowStart(), "09:00");
    response.sendWindowEnd = normalizeTime(entity.getReactivationSendWindowEnd(), "19:00");
    response.maxAttemptsEnabled =
        normalizeMaxAttemptsEnabled(entity.getReactivationMaxAttemptsEnabled());
    return response;
  }

  private Map<String, SettingsDtos.BusinessHoursDay> toBusinessHoursMap(
      TenantOperationalSettings entity) {
    LinkedHashMap<String, SettingsDtos.BusinessHoursDay> response = new LinkedHashMap<>();
    for (SalonDtos.BusinessHour hour : readBusinessHours(entity)) {
      SettingsDtos.BusinessHoursDay item = new SettingsDtos.BusinessHoursDay();
      item.open = hour.open;
      item.close = hour.close;
      item.enabled = hour.enabled;
      response.put(toBusinessHoursKey(hour.day), item);
    }
    return response;
  }

  private List<SalonDtos.BusinessHour> fromBusinessHoursMap(
      Map<String, SettingsDtos.BusinessHoursDay> request) {
    List<SalonDtos.BusinessHour> result = new ArrayList<>();
    if (request == null || request.isEmpty()) return defaultBusinessHoursList();
    for (Map.Entry<String, SettingsDtos.BusinessHoursDay> entry : request.entrySet()) {
      SalonDtos.BusinessHour item = new SalonDtos.BusinessHour();
      item.day = fromBusinessHoursKey(entry.getKey());
      item.open = normalizeTime(entry.getValue() != null ? entry.getValue().open : null, "09:00");
      item.close = normalizeTime(entry.getValue() != null ? entry.getValue().close : null, "19:00");
      item.enabled = entry.getValue() != null && entry.getValue().enabled;
      result.add(item);
    }
    return normalizeBusinessHours(result);
  }

  private List<SalonDtos.BusinessHour> readBusinessHours(TenantOperationalSettings entity) {
    try {
      List<SalonDtos.BusinessHour> parsed =
          objectMapper.readValue(
              entity.getBusinessHoursJson() == null ? "[]" : entity.getBusinessHoursJson(),
              BUSINESS_HOURS_TYPE);
      return normalizeBusinessHours(parsed);
    } catch (Exception ignored) {
      return defaultBusinessHoursList();
    }
  }

  private String serializeBusinessHours(List<SalonDtos.BusinessHour> businessHours) {
    try {
      return objectMapper.writeValueAsString(normalizeBusinessHours(businessHours));
    } catch (Exception e) {
      throw new IllegalStateException("Nao foi possivel serializar horarios de funcionamento", e);
    }
  }

  private void ensureDefaults(TenantOperationalSettings entity) {
    if (entity.getBusinessHoursJson() == null
        || entity.getBusinessHoursJson().isBlank()
        || "[]".equals(entity.getBusinessHoursJson())) {
      entity.setBusinessHoursJson(serializeBusinessHours(defaultBusinessHoursList()));
    }
    entity.setReactivationSendWindowStart(
        normalizeTime(entity.getReactivationSendWindowStart(), "09:00"));
    entity.setReactivationSendWindowEnd(
        normalizeTime(entity.getReactivationSendWindowEnd(), "19:00"));
    entity.setReactivationMaxAttemptsEnabled(
        normalizeMaxAttemptsEnabled(entity.getReactivationMaxAttemptsEnabled()));
    if (entity.getReminderHours() <= 0) entity.setReminderHours(24);
  }

  private List<NormalizedSpecialClosureDate> normalizeSpecialClosureDates(
      List<SalonDtos.SpecialClosureDate> input) {
    Map<LocalDate, NormalizedSpecialClosureDate> unique = new LinkedHashMap<>();
    if (input != null) {
      for (SalonDtos.SpecialClosureDate item : input) {
        if (item == null) continue;
        LocalDate date = parseSpecialClosureDate(item.date);
        unique.put(
            date, new NormalizedSpecialClosureDate(date, normalizeSpecialClosureReason(item.reason)));
      }
    }
    return unique.values().stream()
        .sorted(Comparator.comparing(NormalizedSpecialClosureDate::date))
        .toList();
  }

  private LocalDate parseSpecialClosureDate(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Data especial de fechamento obrigatoria");
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("Data especial de fechamento invalida");
    }
  }

  private String normalizeSpecialClosureReason(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isBlank()) return null;
    return normalized.substring(0, Math.min(normalized.length(), 160));
  }

  private SalonDtos.SpecialClosureDate toSpecialClosureDateDto(TenantSpecialClosureDate entity) {
    SalonDtos.SpecialClosureDate dto = new SalonDtos.SpecialClosureDate();
    dto.date = entity.getClosureDate() != null ? entity.getClosureDate().toString() : null;
    dto.reason = entity.getReason();
    return dto;
  }

  private List<SalonDtos.BusinessHour> normalizeBusinessHours(List<SalonDtos.BusinessHour> hours) {
    LinkedHashMap<String, SalonDtos.BusinessHour> normalized = new LinkedHashMap<>();
    for (SalonDtos.BusinessHour fallback : defaultBusinessHoursList()) {
      normalized.put(toBusinessHoursKey(fallback.day), copyHour(fallback));
    }
    if (hours != null) {
      for (SalonDtos.BusinessHour input : hours) {
        if (input == null) continue;
        String key = toBusinessHoursKey(input.day);
        SalonDtos.BusinessHour item = new SalonDtos.BusinessHour();
        item.day = fromBusinessHoursKey(key);
        item.enabled = input.enabled;
        item.open = normalizeTime(input.open, "09:00");
        item.close = normalizeTime(input.close, "19:00");
        normalized.put(key, item);
      }
    }
    return new ArrayList<>(normalized.values());
  }

  private SalonDtos.BusinessHour findBusinessHourForDay(
      List<SalonDtos.BusinessHour> hours, DayOfWeek dayOfWeek) {
    String key =
        switch (dayOfWeek) {
          case MONDAY -> "monday";
          case TUESDAY -> "tuesday";
          case WEDNESDAY -> "wednesday";
          case THURSDAY -> "thursday";
          case FRIDAY -> "friday";
          case SATURDAY -> "saturday";
          case SUNDAY -> "sunday";
        };
    return hours.stream()
        .filter(hour -> key.equals(toBusinessHoursKey(hour.day)))
        .findFirst()
        .orElse(null);
  }

  private boolean isWithinWindow(LocalTime current, LocalTime start, LocalTime end) {
    if (current == null || start == null || end == null) return false;
    if (end.isBefore(start) || end.equals(start)) {
      return !current.isBefore(start) || !current.isAfter(end);
    }
    return !current.isBefore(start) && !current.isAfter(end);
  }

  private LocalTime parseTime(String value, LocalTime fallback) {
    try {
      return LocalTime.parse(value);
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private SalonDtos.BusinessHour copyHour(SalonDtos.BusinessHour value) {
    SalonDtos.BusinessHour item = new SalonDtos.BusinessHour();
    item.day = value.day;
    item.enabled = value.enabled;
    item.open = value.open;
    item.close = value.close;
    return item;
  }

  private List<SalonDtos.BusinessHour> defaultBusinessHoursList() {
    ArrayList<SalonDtos.BusinessHour> items = new ArrayList<>();
    items.add(defaultHour("Segunda-feira", true, "09:00", "19:00"));
    items.add(defaultHour("Terca-feira", true, "09:00", "19:00"));
    items.add(defaultHour("Quarta-feira", true, "09:00", "19:00"));
    items.add(defaultHour("Quinta-feira", true, "09:00", "19:00"));
    items.add(defaultHour("Sexta-feira", true, "09:00", "19:00"));
    items.add(defaultHour("Sabado", true, "09:00", "17:00"));
    items.add(defaultHour("Domingo", false, "09:00", "13:00"));
    return items;
  }

  private SalonDtos.BusinessHour defaultHour(
      String day, boolean enabled, String open, String close) {
    SalonDtos.BusinessHour item = new SalonDtos.BusinessHour();
    item.day = day;
    item.enabled = enabled;
    item.open = open;
    item.close = close;
    return item;
  }

  private String normalizeTime(String value, String fallback) {
    try {
      return LocalTime.parse(value).withSecond(0).withNano(0).toString();
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private int normalizeMaxAttemptsEnabled(int value) {
    if (value <= 1) return 1;
    if (value >= 3) return 3;
    return 2;
  }

  private String toBusinessHoursKey(String day) {
    if (day == null || day.isBlank()) return "monday";
    String normalized = day.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("seg")) return "monday";
    if (normalized.startsWith("ter")) return "tuesday";
    if (normalized.startsWith("qua")) return "wednesday";
    if (normalized.startsWith("qui")) return "thursday";
    if (normalized.startsWith("sex")) return "friday";
    if (normalized.startsWith("sab")) return "saturday";
    if (normalized.startsWith("dom")) return "sunday";
    try {
      DayOfWeek dayOfWeek = DayOfWeek.valueOf(normalized.toUpperCase(Locale.ROOT));
      return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT);
    } catch (Exception ignored) {
      return "monday";
    }
  }

  private String fromBusinessHoursKey(String key) {
    return switch (key) {
      case "monday" -> "Segunda-feira";
      case "tuesday" -> "Terca-feira";
      case "wednesday" -> "Quarta-feira";
      case "thursday" -> "Quinta-feira";
      case "friday" -> "Sexta-feira";
      case "saturday" -> "Sabado";
      case "sunday" -> "Domingo";
      default -> "Segunda-feira";
    };
  }

  // ============================================================
  // Business Hours — tabela relacional (V75/V76)
  // ============================================================

  @Transactional
  public List<SettingsDtos.BusinessHoursItemResponse> getBusinessHoursFromTable(UUID tenantId) {
    List<TenantBusinessHours> rows =
        businessHoursRepository.findByTenantIdOrderByDayOfWeekAsc(tenantId);
    if (rows.isEmpty()) {
      // fallback: inicializa com defaults
      initDefaultBusinessHours(tenantId);
      rows = businessHoursRepository.findByTenantIdOrderByDayOfWeekAsc(tenantId);
    }
    return rows.stream().map(this::toBusinessHoursItemResponse).toList();
  }

  @Transactional
  public List<SettingsDtos.BusinessHoursItemResponse> updateBusinessHoursInTable(
      UUID tenantId, List<SettingsDtos.BusinessHoursItemRequest> request) {
    if (request == null || request.isEmpty()) return getBusinessHoursFromTable(tenantId);
    for (SettingsDtos.BusinessHoursItemRequest item : request) {
      if (item.dayOfWeek == null || item.dayOfWeek.isBlank()) continue;
      String day = item.dayOfWeek.trim().toUpperCase(Locale.ROOT);
      TenantBusinessHours entity =
          businessHoursRepository
              .findByTenantIdAndDayOfWeek(tenantId, day)
              .orElseGet(
                  () -> {
                    TenantBusinessHours e = new TenantBusinessHours();
                    e.setTenantId(tenantId);
                    e.setDayOfWeek(day);
                    return e;
                  });
      entity.setOpenTime(parseLocalTime(item.openTime, LocalTime.of(9, 0)));
      entity.setCloseTime(parseLocalTime(item.closeTime, LocalTime.of(19, 0)));
      entity.setEnabled(item.enabled);
      entity.setBreakStart(parseLocalTimeOrNull(item.breakStart));
      entity.setBreakEnd(parseLocalTimeOrNull(item.breakEnd));
      TenantBusinessHours persisted = businessHoursRepository.saveAndFlush(entity);

      // Auditoria por dia atualizado — sem dados pessoais de profissional
      registrarAuditoriaBusinessHours(tenantId, persisted);
    }
    return getBusinessHoursFromTable(tenantId);
  }

  private void registrarAuditoriaBusinessHours(UUID tenantId, TenantBusinessHours entity) {
    try {
      AuditEventCommand cmd = new AuditEventCommand();
      cmd.tenantId = tenantId;
      cmd.module = AuditConstants.Module.SETTINGS;
      cmd.action = AuditConstants.Action.BUSINESS_HOURS_UPDATED;
      cmd.entityType = "TenantBusinessHours";
      cmd.entityId = entity.getDayOfWeek();
      cmd.sourceChannel = AuditConstants.SourceChannel.API;
      cmd.after =
          Map.of(
              "tenant_id", tenantId.toString(),
              "day_of_week", entity.getDayOfWeek() != null ? entity.getDayOfWeek() : "",
              "open_time", entity.getOpenTime() != null ? entity.getOpenTime().toString() : "",
              "close_time", entity.getCloseTime() != null ? entity.getCloseTime().toString() : "",
              "enabled", entity.isEnabled(),
              "break_start", entity.getBreakStart() != null ? entity.getBreakStart().toString() : "",
              "break_end", entity.getBreakEnd() != null ? entity.getBreakEnd().toString() : "");
      auditService.recordSuccess(cmd);
    } catch (Exception e) {
      // Falha de auditoria nunca interrompe a operacao principal
    }
  }

  private void initDefaultBusinessHours(UUID tenantId) {
    String[][] defaults = {
      {"MONDAY", "09:00", "19:00", "true"},
      {"TUESDAY", "09:00", "19:00", "true"},
      {"WEDNESDAY", "09:00", "19:00", "true"},
      {"THURSDAY", "09:00", "19:00", "true"},
      {"FRIDAY", "09:00", "19:00", "true"},
      {"SATURDAY", "09:00", "17:00", "true"},
      {"SUNDAY", "09:00", "13:00", "false"}
    };
    for (String[] d : defaults) {
      TenantBusinessHours entity = new TenantBusinessHours();
      entity.setTenantId(tenantId);
      entity.setDayOfWeek(d[0]);
      entity.setOpenTime(LocalTime.parse(d[1]));
      entity.setCloseTime(LocalTime.parse(d[2]));
      entity.setEnabled(Boolean.parseBoolean(d[3]));
      businessHoursRepository.save(entity);
    }
    // Panache emite o INSERT no persist(); aqui o flush e explicito porque o chamador
    // (getBusinessHoursFromTable) rele a tabela em seguida (armadilha 2).
    businessHoursRepository.flush();
  }

  private LocalTime parseLocalTime(String value, LocalTime fallback) {
    try {
      return LocalTime.parse(value);
    } catch (Exception e) {
      return fallback;
    }
  }

  private SettingsDtos.BusinessHoursItemResponse toBusinessHoursItemResponse(
      TenantBusinessHours entity) {
    SettingsDtos.BusinessHoursItemResponse r = new SettingsDtos.BusinessHoursItemResponse();
    r.dayOfWeek = entity.getDayOfWeek();
    r.openTime = entity.getOpenTime() != null ? entity.getOpenTime().toString() : null;
    r.closeTime = entity.getCloseTime() != null ? entity.getCloseTime().toString() : null;
    r.enabled = entity.isEnabled();
    r.breakStart = entity.getBreakStart() != null ? entity.getBreakStart().toString() : null;
    r.breakEnd = entity.getBreakEnd() != null ? entity.getBreakEnd().toString() : null;
    return r;
  }

  private LocalTime parseLocalTimeOrNull(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  // ============================================================
  // LGPD Contact (V77)
  // ============================================================

  @Transactional
  public SettingsDtos.LgpdContactResponse getLgpdContact(UUID tenantId) {
    return toLgpdContactResponse(findByTenantIdOrCreate(tenantId));
  }

  @Transactional
  public SettingsDtos.LgpdContactResponse updateLgpdContact(
      UUID tenantId, SettingsDtos.LgpdContactRequest request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    if (request != null) {
      entity.setLgpdContactEmail(request.lgpdContactEmail);
      entity.setLgpdContactChannel(request.lgpdContactChannel);
      entity.setLgpdContactResponseSlaDays(
          (request.lgpdContactResponseSlaDays != null && request.lgpdContactResponseSlaDays > 0)
              ? request.lgpdContactResponseSlaDays
              : 15);
    }
    return toLgpdContactResponse(entity);
  }

  private SettingsDtos.LgpdContactResponse toLgpdContactResponse(TenantOperationalSettings entity) {
    SettingsDtos.LgpdContactResponse r = new SettingsDtos.LgpdContactResponse();
    r.lgpdContactEmail = entity.getLgpdContactEmail();
    r.lgpdContactChannel = entity.getLgpdContactChannel();
    r.lgpdContactResponseSlaDays =
        entity.getLgpdContactResponseSlaDays() != null ? entity.getLgpdContactResponseSlaDays() : 15;
    return r;
  }

  // ============================================================
  // Feature Flags (V78)
  // ============================================================

  @Transactional
  public SettingsDtos.TenantFeatureFlagsResponse getFeatureFlags(UUID tenantId) {
    return toFeatureFlagsResponse(findByTenantIdOrCreate(tenantId));
  }

  @Transactional
  public SettingsDtos.TenantFeatureFlagsResponse updateFeatureFlags(
      UUID tenantId, SettingsDtos.TenantFeatureFlagsRequest request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    if (request != null) {
      if (request.asaasEnabled != null) entity.setAsaasEnabled(request.asaasEnabled);
      if (request.minioEnabled != null) entity.setMinioEnabled(request.minioEnabled);
      if (request.chatRetentionDaysCompleted != null && request.chatRetentionDaysCompleted > 0)
        entity.setChatRetentionDaysCompleted(request.chatRetentionDaysCompleted);
      if (request.chatRetentionDaysCanceled != null && request.chatRetentionDaysCanceled > 0)
        entity.setChatRetentionDaysCanceled(request.chatRetentionDaysCanceled);
      if (request.chatRetentionDaysDefault != null && request.chatRetentionDaysDefault > 0)
        entity.setChatRetentionDaysDefault(request.chatRetentionDaysDefault);
      if (request.auditRetentionDays != null && request.auditRetentionDays > 0)
        entity.setAuditRetentionDays(request.auditRetentionDays);
    }
    return toFeatureFlagsResponse(entity);
  }

  private SettingsDtos.TenantFeatureFlagsResponse toFeatureFlagsResponse(
      TenantOperationalSettings entity) {
    SettingsDtos.TenantFeatureFlagsResponse r = new SettingsDtos.TenantFeatureFlagsResponse();
    r.asaasEnabled = entity.isAsaasEnabled();
    r.minioEnabled = entity.isMinioEnabled();
    r.chatRetentionDaysCompleted = entity.getChatRetentionDaysCompleted();
    r.chatRetentionDaysCanceled = entity.getChatRetentionDaysCanceled();
    r.chatRetentionDaysDefault = entity.getChatRetentionDaysDefault();
    r.auditRetentionDays = entity.getAuditRetentionDays();
    return r;
  }

  // ============================================================
  // Politica de Cancelamento (V93)
  // ============================================================

  @Transactional
  public SettingsDtos.CancellationPolicyResponse getCancellationPolicy(UUID tenantId) {
    return toCancellationPolicyResponse(findByTenantIdOrCreate(tenantId));
  }

  @Transactional
  public SettingsDtos.CancellationPolicyResponse updateCancellationPolicy(
      UUID tenantId, SettingsDtos.CancellationPolicyRequest request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    if (request != null) {
      if (request.cancellationPolicyEnabled != null)
        entity.setCancellationPolicyEnabled(request.cancellationPolicyEnabled);
      if (request.cancellationMinHoursBefore != null && request.cancellationMinHoursBefore >= 0)
        entity.setCancellationMinHoursBefore(request.cancellationMinHoursBefore);
      if (request.cancellationFeePercent != null
          && request.cancellationFeePercent >= 0
          && request.cancellationFeePercent <= 100)
        entity.setCancellationFeePercent(request.cancellationFeePercent);
    }
    return toCancellationPolicyResponse(entity);
  }

  private SettingsDtos.CancellationPolicyResponse toCancellationPolicyResponse(
      TenantOperationalSettings entity) {
    SettingsDtos.CancellationPolicyResponse r = new SettingsDtos.CancellationPolicyResponse();
    r.cancellationPolicyEnabled = entity.isCancellationPolicyEnabled();
    r.cancellationMinHoursBefore = entity.getCancellationMinHoursBefore();
    r.cancellationFeePercent = entity.getCancellationFeePercent();
    return r;
  }

  // ============================================================
  // Regua de lembretes (F03 — V120)
  // ============================================================

  /**
   * Assimetria do original preservada na anotacao: este e o <b>unico</b> getter do service
   * <b>sem</b> {@code @Transactional}, apesar de tambem passar pelo {@code findByTenantIdOrCreate},
   * que insere.
   *
   * <p><b>Divergencia de comportamento conhecida, nao resolvida aqui.</b> No Quarkus, o
   * {@code persist()} do Panache fora de transacao estoura {@code TransactionRequiredException} —
   * ou seja, {@code GET /api/v1/settings/reminders} chamado por um tenant que ainda nao tem linha
   * em {@code tenant_operational_settings} devolve 500 no original. No Spring, o
   * {@code saveAndFlush} do {@code SimpleJpaRepository} abre transacao propria, entao a mesma
   * chamada cria a linha e responde 200.
   *
   * <p>Optou-se por <b>nao</b> reproduzir o 500: seria erro proposital, e a rota so o atinge quando
   * o tenant chama {@code /reminders} antes de qualquer outro endpoint de settings (qualquer um dos
   * demais ja cria a linha). Vale confirmar com o time se a ausencia do {@code @Transactional} no
   * original e intencional ou esquecimento antes de considerar o assunto encerrado.
   */
  public SettingsDtos.ReminderSettingsResponse getReminderSettings(UUID tenantId) {
    return toReminderSettingsResponse(findByTenantIdOrCreate(tenantId));
  }

  @Transactional
  public SettingsDtos.ReminderSettingsResponse updateReminderSettings(
      UUID tenantId, SettingsDtos.ReminderSettingsRequest request) {
    TenantOperationalSettings entity = findByTenantIdOrCreate(tenantId);
    if (request != null) {
      if (request.d1Habilitado != null) entity.setD1ReminderEnabled(request.d1Habilitado);
      if (request.d1Hora != null && request.d1Hora.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
        entity.setD1ReminderHora(request.d1Hora);
      }
      if (request.horasAntesHabilitado != null)
        entity.setHoursBeforeReminderEnabled(request.horasAntesHabilitado);
      if (request.horasAntes != null && request.horasAntes >= 1 && request.horasAntes <= 12) {
        entity.setReminderHours(request.horasAntes);
      }
    }
    return toReminderSettingsResponse(entity);
  }

  private SettingsDtos.ReminderSettingsResponse toReminderSettingsResponse(
      TenantOperationalSettings entity) {
    SettingsDtos.ReminderSettingsResponse r = new SettingsDtos.ReminderSettingsResponse();
    r.d1Habilitado = entity.isD1ReminderEnabled();
    r.d1Hora = entity.getD1ReminderHora();
    r.horasAntesHabilitado = entity.isHoursBeforeReminderEnabled();
    r.horasAntes = entity.getReminderHours();
    return r;
  }

  // ============================================================
  // Email Templates por tenant (V30 — tabela email_template_configs)
  // ============================================================

  @Transactional
  public SettingsDtos.EmailTemplateListResponse listEmailTemplates(UUID tenantId) {
    SettingsDtos.EmailTemplateListResponse response = new SettingsDtos.EmailTemplateListResponse();
    response.templates = new ArrayList<>();
    for (EmailTemplateRendererService.TemplateDefinition def :
        emailTemplateRendererService.definitions()) {
      response.templates.add(buildEmailTemplateResponse(tenantId, def));
    }
    return response;
  }

  @Transactional
  public SettingsDtos.EmailTemplateResponse getEmailTemplate(UUID tenantId, String type) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    EmailTemplateRendererService.TemplateDefinition def =
        emailTemplateRendererService.definition(templateType);
    return buildEmailTemplateResponse(tenantId, def);
  }

  @Transactional
  public SettingsDtos.EmailTemplateResponse updateEmailTemplate(
      UUID tenantId, String type, SettingsDtos.EmailTemplateRequest request) {
    EmailTemplateType templateType = parseEmailTemplateType(type);
    EmailTemplateRendererService.TemplateDefinition def =
        emailTemplateRendererService.definition(templateType);

    EmailTemplateConfig config =
        emailTemplateConfigRepository
            .findFirstByTenantIdAndTemplateType(tenantId, templateType)
            .orElseGet(
                () -> {
                  EmailTemplateConfig c = new EmailTemplateConfig();
                  c.setTenantId(tenantId);
                  c.setTemplateType(templateType);
                  c.setSubjectTemplate(def.defaultSubjectTemplate());
                  c.setHtmlTemplate(def.defaultHtmlTemplate());
                  return c;
                });

    if (request != null) {
      if (request.subjectTemplate != null && !request.subjectTemplate.isBlank())
        config.setSubjectTemplate(request.subjectTemplate);
      if (request.htmlTemplate != null && !request.htmlTemplate.isBlank())
        config.setHtmlTemplate(emailTemplateRendererService.sanitizeHtml(request.htmlTemplate));
      config.setFromEmail(request.fromEmail);
      config.setFromName(request.fromName);
      config.setReplyTo(request.replyTo);
    }
    config.setActive(true);
    emailTemplateConfigRepository.saveAndFlush(config);
    return buildEmailTemplateResponse(tenantId, def);
  }

  private SettingsDtos.EmailTemplateResponse buildEmailTemplateResponse(
      UUID tenantId, EmailTemplateRendererService.TemplateDefinition def) {
    Optional<EmailTemplateConfig> tenantConfig =
        emailTemplateConfigRepository.findFirstByTenantIdAndTemplateType(tenantId, def.type());
    Optional<EmailTemplateConfig> globalConfig =
        emailTemplateConfigRepository.findGlobalByType(def.type());

    SettingsDtos.EmailTemplateResponse r = new SettingsDtos.EmailTemplateResponse();
    r.type = def.type().name();
    r.label = def.label();
    r.tenantCustomized = tenantConfig.isPresent();

    EmailTemplateConfig effective = tenantConfig.orElse(globalConfig.orElse(null));
    r.subjectTemplate =
        effective != null ? effective.getSubjectTemplate() : def.defaultSubjectTemplate();
    r.htmlTemplate = effective != null ? effective.getHtmlTemplate() : def.defaultHtmlTemplate();
    r.fromEmail = effective != null ? effective.getFromEmail() : null;
    r.fromName = effective != null ? effective.getFromName() : null;
    r.replyTo = effective != null ? effective.getReplyTo() : null;
    r.active = effective == null || effective.isActive();
    return r;
  }

  private EmailTemplateType parseEmailTemplateType(String type) {
    try {
      return EmailTemplateType.valueOf(type.toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("Tipo de template invalido: " + type);
    }
  }

  private record NormalizedSpecialClosureDate(LocalDate date, String reason) {}
}
