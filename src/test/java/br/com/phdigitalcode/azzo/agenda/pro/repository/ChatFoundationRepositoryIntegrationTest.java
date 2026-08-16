package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatMessageEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationConsentHistoryEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationSendLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantReactivationConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatAppointmentMarker;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageDirection;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationAttemptStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;

/**
 * Teste de integracao contra PostgreSQL real (Testcontainers) para a Fronteira 1 (fundacao) do
 * modulo {@code chat}: 7 entidades + 7 repositorios portados de
 * {@code modules/chat/domain/{entity,repository}}. Cobre especificamente os pontos de maior risco
 * de nao-paridade: as queries nativas de update ({@code incrementUnread}/{@code clearUnread} em
 * {@code ChatConversationRepository}, {@code clearExpiredContents}/{@code markInboundMessagesRead}/
 * {@code updateExpiresAtByConversation} em {@code ChatMessageRepository}), a paginacao por cursor
 * de mensagens, o join nativo com {@code clients} em {@code listPagedWithClient}, e a especificacao
 * dinamica de {@code WhatsAppBookingReactivationCycleRepository.listOperational}/{@code
 * countOperational} (que no original e HQL montado na mao com os mesmos 6 campos de busca).
 *
 * <p>Roda contra o schema real gerado pelas 124+ migrations do projeto (inclui V40, V44, V57, V74,
 * V90, V112, V113 — os ajustes de reativacao WhatsApp aplicados ao longo do tempo no original,
 * copiados sem alteracao).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatFoundationRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private TenantRepository tenantRepository;
  @Autowired private ClienteRepository clienteRepository;
  @Autowired private ChatConversationRepository conversationRepository;
  @Autowired private ChatMessageRepository messageRepository;
  @Autowired private ReactivationConsentHistoryRepository consentHistoryRepository;
  @Autowired private ReactivationSendLogRepository sendLogRepository;
  @Autowired private TenantReactivationConfigRepository reactivationConfigRepository;
  @Autowired private WhatsAppBookingReactivationAttemptRepository attemptRepository;
  @Autowired private WhatsAppBookingReactivationCycleRepository cycleRepository;

  private UUID tenantId;
  private UUID clientId;

  @BeforeEach
  void setUp() {
    Tenant tenant = new Tenant();
    tenant.setName("Salao Teste Chat");
    tenant.setSlug("salao-teste-chat-" + UUID.randomUUID());
    tenant = tenantRepository.save(tenant);
    tenantId = tenant.getId();

    Cliente cliente = new Cliente();
    cliente.setTenantId(tenantId);
    cliente.setName("Cliente Teste");
    cliente.setPhone("+5511999990000");
    cliente = clienteRepository.save(cliente);
    clientId = cliente.getId();
  }

  private ChatConversationEntity conversation(ChatChannel channel, String externalContactId) {
    ChatConversationEntity c = new ChatConversationEntity();
    c.setTenantId(tenantId);
    c.setClientId(clientId);
    c.setChannel(channel);
    c.setExternalContactId(externalContactId);
    c.setAppointmentMarker(ChatAppointmentMarker.NAO_INICIADO);
    return conversationRepository.save(c);
  }

  @Test
  void deveIncrementarEZerarUnreadCountViaQueryNativa() {
    ChatConversationEntity conversation = conversation(ChatChannel.WHATSAPP, "5511999990000");
    assertThat(conversation.getUnreadCount()).isZero();

    long updated = conversationRepository.incrementUnread(tenantId, conversation.getId());
    assertThat(updated).isEqualTo(1);
    conversationRepository.incrementUnread(tenantId, conversation.getId());

    ChatConversationEntity reloaded = conversationRepository.findById(conversation.getId()).orElseThrow();
    assertThat(reloaded.getUnreadCount()).isEqualTo(2);

    long cleared = conversationRepository.clearUnread(tenantId, conversation.getId());
    assertThat(cleared).isEqualTo(1);
    assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getUnreadCount())
        .isZero();

    // segunda chamada nao afeta linha nenhuma (unread_count > 0 na WHERE)
    assertThat(conversationRepository.clearUnread(tenantId, conversation.getId())).isZero();
  }

  @Test
  void devePermitirTelegramNaConversationApesarDaCheckOriginalDoV1() {
    // V113 corrigiu a CHECK ck_chat_conversations_channel para aceitar TELEGRAM (bug real do
    // original documentado na migration) — este teste prova que o insert nao quebra mais.
    ChatConversationEntity telegramConversation = conversation(ChatChannel.TELEGRAM, "123456789");
    assertThat(telegramConversation.getId()).isNotNull();
    assertThat(telegramConversation.getChannel()).isEqualTo(ChatChannel.TELEGRAM);
  }

  @Test
  void deveRespeitarUniqueConstraintTenantClientChannel() {
    conversation(ChatChannel.WHATSAPP, "5511999990000");
    assertThat(
            conversationRepository
                .findByTenantClientAndChannel(tenantId, clientId, ChatChannel.WHATSAPP)
                .isPresent())
        .isTrue();
  }

  @Test
  void deveEncontrarConversaPorExternalContactIdComFallbackWhatsapp() {
    conversation(ChatChannel.WHATSAPP, "5511988887777");

    Optional<ChatConversationEntity> found =
        conversationRepository.findByTenantAndExternalContactId(tenantId, "5511988887777");

    assertThat(found).isPresent();
    assertThat(
            conversationRepository
                .findByTenantAndExternalContactId(tenantId, "nao-existe")
                .isPresent())
        .isFalse();
    assertThat(conversationRepository.findByTenantAndExternalContactId(tenantId, "  ")).isEmpty();
  }

  @Test
  void deveListarConversasComDadosDoClienteViaJoinNativo() {
    ChatConversationEntity conv = conversation(ChatChannel.WHATSAPP, "5511977776666");
    conv.setLastMessagePreview("Oi, tudo bem?");
    conv.setLastMessageAt(Instant.now());
    conversationRepository.save(conv);

    List<ChatConversationRepository.ConversationWithClientRow> rows =
        conversationRepository.listPagedWithClient(tenantId, 0, 10);

    assertThat(rows).isNotEmpty();
    ChatConversationRepository.ConversationWithClientRow row = rows.get(0);
    assertThat(row.id()).isEqualTo(conv.getId());
    assertThat(row.clientName()).isEqualTo("Cliente Teste");
    assertThat(row.clientPhone()).isEqualTo("+5511999990000");
    assertThat(row.lastMessagePreview()).isEqualTo("Oi, tudo bem?");
  }

  @Test
  void deveListarESomenteContarConversasComMensagemHoje() {
    ChatConversationEntity conv = conversation(ChatChannel.WHATSAPP, "5511966665555");
    Instant now = Instant.now();
    Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
    Instant nextDayStart = dayStart.plus(1, ChronoUnit.DAYS);

    ChatMessageEntity msg = new ChatMessageEntity();
    msg.setTenantId(tenantId);
    msg.setConversationId(conv.getId());
    msg.setClientId(clientId);
    msg.setDirection(ChatMessageDirection.INBOUND);
    msg.setContent("mensagem de hoje");
    msg.setStatus(ChatMessageStatus.DELIVERED);
    msg.setExpiresAt(now.plusSeconds(3600));
    messageRepository.save(msg);

    assertThat(conversationRepository.countTodayByTenant(tenantId, dayStart, nextDayStart)).isEqualTo(1);
    assertThat(conversationRepository.listTodayPaged(tenantId, dayStart, nextDayStart, 0, 10))
        .extracting(ChatConversationEntity::getId)
        .containsExactly(conv.getId());

    // outra conversa sem mensagem hoje nao aparece
    conversation(ChatChannel.WHATSAPP, "5511955554444");
    assertThat(conversationRepository.countTodayByTenant(tenantId, dayStart, nextDayStart)).isEqualTo(1);
  }

  @Test
  void devePaginarMensagensPorCursorEmOrdemCrescente() {
    ChatConversationEntity conv = conversation(ChatChannel.WHATSAPP, "5511944443333");
    List<UUID> ids = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      ChatMessageEntity msg = new ChatMessageEntity();
      msg.setTenantId(tenantId);
      msg.setConversationId(conv.getId());
      msg.setClientId(clientId);
      msg.setDirection(ChatMessageDirection.OUTBOUND);
      msg.setContent("msg-" + i);
      msg.setStatus(ChatMessageStatus.SENT);
      msg.setExpiresAt(Instant.now().plusSeconds(3600));
      ids.add(messageRepository.save(msg).getId());
    }

    List<ChatMessageEntity> firstPage =
        messageRepository.listByConversationAfterCursor(tenantId, conv.getId(), null, 3);
    assertThat(firstPage).hasSize(3);

    List<ChatMessageEntity> secondPage =
        messageRepository.listByConversationAfterCursor(
            tenantId, conv.getId(), firstPage.get(firstPage.size() - 1).getId(), 10);
    assertThat(secondPage).hasSize(2);
    assertThat(secondPage.get(0).getContent()).isEqualTo("msg-3");

    assertThat(messageRepository.countByConversation(tenantId, conv.getId())).isEqualTo(5);
  }

  @Test
  void deveMarcarMensagensInboundComoLidasEAtualizarExpiracaoViaQueryNativa() {
    ChatConversationEntity conv = conversation(ChatChannel.WHATSAPP, "5511933332222");
    ChatMessageEntity inbound = new ChatMessageEntity();
    inbound.setTenantId(tenantId);
    inbound.setConversationId(conv.getId());
    inbound.setClientId(clientId);
    inbound.setDirection(ChatMessageDirection.INBOUND);
    inbound.setContent("oi");
    inbound.setStatus(ChatMessageStatus.DELIVERED);
    inbound.setExpiresAt(Instant.now().plusSeconds(60));
    inbound = messageRepository.save(inbound);

    long marked = messageRepository.markInboundMessagesRead(tenantId, conv.getId(), Instant.now());
    assertThat(marked).isEqualTo(1);
    ChatMessageEntity reloaded = messageRepository.findById(inbound.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ChatMessageStatus.READ);
    assertThat(reloaded.getReadAt()).isNotNull();

    Instant newExpiration = Instant.now().plusSeconds(999_999);
    long updatedExpiry =
        messageRepository.updateExpiresAtByConversation(tenantId, conv.getId(), newExpiration);
    assertThat(updatedExpiry).isEqualTo(1);
    assertThat(messageRepository.findById(inbound.getId()).orElseThrow().getExpiresAt())
        .isCloseTo(newExpiration, new org.assertj.core.data.TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
  }

  @Test
  void deveLimparConteudoDeMensagensExpiradasViaQueryNativa() {
    ChatConversationEntity conv = conversation(ChatChannel.WHATSAPP, "5511922221111");
    ChatMessageEntity expired = new ChatMessageEntity();
    expired.setTenantId(tenantId);
    expired.setConversationId(conv.getId());
    expired.setClientId(clientId);
    expired.setDirection(ChatMessageDirection.OUTBOUND);
    expired.setContent("vai sumir");
    expired.setStatus(ChatMessageStatus.SENT);
    expired.setExpiresAt(Instant.now().minusSeconds(10));
    expired = messageRepository.save(expired);

    long cleared = messageRepository.clearExpiredContents(Instant.now());
    assertThat(cleared).isEqualTo(1);
    assertThat(messageRepository.findById(expired.getId()).orElseThrow().getContent()).isNull();
  }

  @Test
  void deveEncontrarMensagemPorProviderMessageId() {
    ChatConversationEntity conv = conversation(ChatChannel.WHATSAPP, "5511911110000");
    ChatMessageEntity msg = new ChatMessageEntity();
    msg.setTenantId(tenantId);
    msg.setConversationId(conv.getId());
    msg.setClientId(clientId);
    msg.setDirection(ChatMessageDirection.OUTBOUND);
    msg.setStatus(ChatMessageStatus.SENT);
    msg.setProviderMessageId("wamid.abc123");
    msg.setExpiresAt(Instant.now().plusSeconds(60));
    messageRepository.save(msg);

    assertThat(messageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.abc123"))
        .isPresent();
    assertThat(messageRepository.findByTenantIdAndProviderMessageId(tenantId, "wamid.nope"))
        .isEmpty();
  }

  @Test
  void deveRetornarConfigDeReativacaoPadraoQuandoTenantNaoTemLinha() {
    TenantReactivationConfigEntity config = reactivationConfigRepository.findByTenantIdOrDefault(tenantId);
    assertThat(config.getId()).isNull();
    assertThat(config.getMaxAttempts()).isEqualTo(3);
    assertThat(config.getMinIntervalDays()).isEqualTo(7);
    assertThat(config.getMaxMessagesPerMonthPerClient()).isEqualTo(4);
  }

  @Test
  void devePersistirEEncontrarConfigDeReativacaoDoTenant() {
    TenantReactivationConfigEntity config = new TenantReactivationConfigEntity();
    config.setTenantId(tenantId);
    config.setMaxAttempts(5);
    reactivationConfigRepository.save(config);

    TenantReactivationConfigEntity found = reactivationConfigRepository.findByTenantIdOrDefault(tenantId);
    assertThat(found.getId()).isNotNull();
    assertThat(found.getMaxAttempts()).isEqualTo(5);
  }

  @Test
  void deveRegistrarHistoricoDeConsentimentoEmOrdemDecrescente() throws InterruptedException {
    ReactivationConsentHistoryEntity optOut = new ReactivationConsentHistoryEntity();
    optOut.setTenantId(tenantId);
    optOut.setClientId(clientId);
    optOut.setAction("OPT_OUT");
    optOut.setSource("SYSTEM");
    consentHistoryRepository.save(optOut);

    Thread.sleep(5);

    ReactivationConsentHistoryEntity optIn = new ReactivationConsentHistoryEntity();
    optIn.setTenantId(tenantId);
    optIn.setClientId(clientId);
    optIn.setAction("OPT_IN");
    optIn.setSource("WHATSAPP_REPLY");
    consentHistoryRepository.save(optIn);

    List<ReactivationConsentHistoryEntity> history =
        consentHistoryRepository.findByClientId(tenantId, clientId);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).getAction()).isEqualTo("OPT_IN");
    assertThat(history.get(1).getAction()).isEqualTo("OPT_OUT");
  }

  @Test
  void deveContarEBuscarUltimoEnvioDeReativacaoEExpirarLogsAntigos() {
    UUID cycleId = UUID.randomUUID();
    ReactivationSendLogEntity log1 = new ReactivationSendLogEntity();
    log1.setTenantId(tenantId);
    log1.setClientId(clientId);
    log1.setCycleId(cycleId);
    log1.setAttemptNumber(1);
    log1.setSentAt(Instant.now().minusSeconds(120));
    sendLogRepository.save(log1);

    ReactivationSendLogEntity log2 = new ReactivationSendLogEntity();
    log2.setTenantId(tenantId);
    log2.setClientId(clientId);
    log2.setCycleId(cycleId);
    log2.setAttemptNumber(2);
    log2.setSentAt(Instant.now());
    sendLogRepository.save(log2);

    assertThat(sendLogRepository.countByClientIdAndSentAtAfter(clientId, Instant.now().minusSeconds(300)))
        .isEqualTo(2);
    assertThat(sendLogRepository.findLastByClientId(clientId)).isPresent();
    assertThat(sendLogRepository.findLastByClientId(clientId).get().getAttemptNumber()).isEqualTo(2);

    ReactivationSendLogEntity expired = new ReactivationSendLogEntity();
    expired.setTenantId(tenantId);
    expired.setClientId(clientId);
    expired.setCycleId(cycleId);
    expired.setAttemptNumber(3);
    expired.setExpiresAt(Instant.now().minusSeconds(1));
    sendLogRepository.save(expired);

    long deleted = sendLogRepository.deleteExpired(Instant.now());
    assertThat(deleted).isEqualTo(1);
  }

  private WhatsAppBookingReactivationCycleEntity cycle(
      String userIdentifier,
      WhatsAppBookingReactivationStatus status,
      WhatsAppBookingReactivationStage stage,
      Instant abandonedAt,
      String customerName) {
    WhatsAppBookingReactivationCycleEntity c = new WhatsAppBookingReactivationCycleEntity();
    c.setTenantId(tenantId);
    c.setClientId(clientId);
    c.setUserIdentifier(userIdentifier);
    c.setCustomerName(customerName);
    c.setAbandonedAt(abandonedAt);
    c.setLastStage(stage);
    c.setStatus(status);
    return cycleRepository.save(c);
  }

  @Test
  void deveEncontrarUltimoCicloAtivoPorTenantEIdentificadorDeUsuario() {
    cycle(
        "5511900001111",
        WhatsAppBookingReactivationStatus.CONVERTED,
        WhatsAppBookingReactivationStage.SERVICE_SELECTION,
        Instant.now().minusSeconds(600),
        "Maria");
    WhatsAppBookingReactivationCycleEntity active =
        cycle(
            "5511900001111",
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStage.TIME_SELECTION,
            Instant.now(),
            "Maria");

    Optional<WhatsAppBookingReactivationCycleEntity> found =
        cycleRepository.findLatestByTenantAndUserIdentifier(
            tenantId, "5511900001111", List.of(WhatsAppBookingReactivationStatus.ACTIVE));

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(active.getId());
  }

  @Test
  void deveListarCiclosDevidosParaReenvio() {
    WhatsAppBookingReactivationCycleEntity due =
        cycle(
            "5511900002222",
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStage.SERVICE_SELECTION,
            Instant.now().minusSeconds(3600),
            "Joao");
    due.setNextAttemptAt(Instant.now().minusSeconds(60));
    cycleRepository.save(due);

    WhatsAppBookingReactivationCycleEntity notYetDue =
        cycle(
            "5511900003333",
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStage.SERVICE_SELECTION,
            Instant.now(),
            "Ana");
    notYetDue.setNextAttemptAt(Instant.now().plusSeconds(3600));
    cycleRepository.save(notYetDue);

    List<WhatsAppBookingReactivationCycleEntity> dueList = cycleRepository.listDue(Instant.now());

    assertThat(dueList).extracting(WhatsAppBookingReactivationCycleEntity::getId)
        .contains(due.getId())
        .doesNotContain(notYetDue.getId());
  }

  @Test
  void deveListarOperacionalComBuscaTextualEPaginacao() {
    cycle(
        "5511900004444",
        WhatsAppBookingReactivationStatus.ACTIVE,
        WhatsAppBookingReactivationStage.SERVICE_SELECTION,
        Instant.now().minusSeconds(100),
        "Fernanda Souza");
    cycle(
        "5511900005555",
        WhatsAppBookingReactivationStatus.ACTIVE,
        WhatsAppBookingReactivationStage.SERVICE_SELECTION,
        Instant.now().minusSeconds(50),
        "Carlos Lima");

    List<WhatsAppBookingReactivationCycleEntity> matched =
        cycleRepository.listOperational(
            tenantId,
            null,
            null,
            WhatsAppBookingReactivationStatus.ACTIVE,
            null,
            "fernanda",
            0,
            10);

    assertThat(matched).hasSize(1);
    assertThat(matched.get(0).getCustomerName()).isEqualTo("Fernanda Souza");

    long total =
        cycleRepository.countOperational(
            tenantId, null, null, WhatsAppBookingReactivationStatus.ACTIVE, null, null);
    assertThat(total).isEqualTo(2);

    List<WhatsAppBookingReactivationCycleEntity> onePage =
        cycleRepository.listOperational(
            tenantId, null, WhatsAppBookingReactivationStatus.ACTIVE, 1);
    assertThat(onePage).hasSize(1);
    // ordenado por abandonedAt desc: o mais recente (Carlos, -50s) vem primeiro
    assertThat(onePage.get(0).getCustomerName()).isEqualTo("Carlos Lima");
  }

  @Test
  void deveListarApenasCiclosAtivosOuReativadosDoCliente() {
    cycle(
        "5511900006666",
        WhatsAppBookingReactivationStatus.EXHAUSTED,
        WhatsAppBookingReactivationStage.SERVICE_SELECTION,
        Instant.now(),
        "X");
    WhatsAppBookingReactivationCycleEntity active =
        cycle(
            "5511900007777",
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStage.SERVICE_SELECTION,
            Instant.now(),
            "Y");

    List<WhatsAppBookingReactivationCycleEntity> result =
        cycleRepository.listActiveByTenantAndClient(tenantId, clientId);

    assertThat(result).extracting(WhatsAppBookingReactivationCycleEntity::getId)
        .containsExactly(active.getId());
  }

  @Test
  void deveRegistrarEBuscarUltimaTentativaDoCiclo() {
    WhatsAppBookingReactivationCycleEntity cycle =
        cycle(
            "5511900008888",
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStage.SERVICE_SELECTION,
            Instant.now(),
            "Z");

    WhatsAppBookingReactivationAttemptEntity attempt1 = new WhatsAppBookingReactivationAttemptEntity();
    attempt1.setCycleId(cycle.getId());
    attempt1.setTenantId(tenantId);
    attempt1.setAttemptNumber(1);
    attempt1.setScheduledFor(Instant.now().minusSeconds(200));
    attempt1.setSentAt(Instant.now().minusSeconds(180));
    attempt1.setStatus(WhatsAppBookingReactivationAttemptStatus.SENT);
    attemptRepository.save(attempt1);

    WhatsAppBookingReactivationAttemptEntity attempt2 = new WhatsAppBookingReactivationAttemptEntity();
    attempt2.setCycleId(cycle.getId());
    attempt2.setTenantId(tenantId);
    attempt2.setAttemptNumber(2);
    attempt2.setScheduledFor(Instant.now());
    attempt2.setStatus(WhatsAppBookingReactivationAttemptStatus.PENDING);
    attemptRepository.save(attempt2);

    Optional<WhatsAppBookingReactivationAttemptEntity> lastSent =
        attemptRepository.findLatestSentAttempt(cycle.getId());
    assertThat(lastSent).isPresent();
    assertThat(lastSent.get().getAttemptNumber()).isEqualTo(1);

    Optional<WhatsAppBookingReactivationAttemptEntity> lastAny =
        attemptRepository.findLatestAttempt(cycle.getId());
    assertThat(lastAny).isPresent();
    assertThat(lastAny.get().getAttemptNumber()).isEqualTo(2);
  }
}
