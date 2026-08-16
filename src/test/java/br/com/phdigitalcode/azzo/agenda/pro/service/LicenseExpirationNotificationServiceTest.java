package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.integration.CredentialsEmailService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * Cobre {@code LicenseExpirationNotificationScheduler} do original (corpo migrado para
 * {@link LicenseExpirationNotificationService}).
 *
 * <p>Os testes usam um {@link EntityManager} mockado para a consulta de OWNERs, o que permite
 * exercitar o caminho feliz que <b>o original nunca alcanca</b> — ver o aviso no javadoc do
 * service: a JPQL real filtra {@code u.active}, campo inexistente, e estoura. O teste
 * {@link #falhaAoBuscarOwnersNaoAbortaAsDemaisJanelas} reproduz exatamente esse cenario.
 */
class LicenseExpirationNotificationServiceTest {

  private CheckoutOrderRepository checkoutOrderRepository;
  private TenantRepository tenantRepository;
  private CredentialsEmailService credentialsEmailService;
  private EntityManager entityManager;
  private TypedQuery<Usuario> ownersQuery;
  private LicenseExpirationNotificationService service;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    checkoutOrderRepository = mock(CheckoutOrderRepository.class);
    tenantRepository = mock(TenantRepository.class);
    credentialsEmailService = mock(CredentialsEmailService.class);
    entityManager = mock(EntityManager.class);
    ownersQuery = mock(TypedQuery.class);

    when(credentialsEmailService.isEnabled()).thenReturn(true);
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of());
    when(entityManager.createQuery(anyString(), eq(Usuario.class))).thenReturn(ownersQuery);
    when(ownersQuery.setParameter(anyString(), any())).thenReturn(ownersQuery);
    when(ownersQuery.getResultList()).thenReturn(List.of());

    service =
        new LicenseExpirationNotificationService(
            checkoutOrderRepository, tenantRepository, credentialsEmailService);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
  }

  @Test
  void emailDesabilitadoNaoConsultaNada() {
    when(credentialsEmailService.isEnabled()).thenReturn(false);

    service.notificarVencimentos();

    verify(checkoutOrderRepository, never()).listarVencendoNaJanela(any(), any(), any());
    verify(entityManager, never()).createQuery(anyString(), eq(Usuario.class));
    verifyNoInteractions(tenantRepository);
  }

  @Test
  void consultaUmaJanelaDeUmDiaParaCadaAlertaDe7_3_e1Dias() {
    Instant antes = Instant.now();

    service.notificarVencimentos();

    ArgumentCaptor<Instant> inicio = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> fim = ArgumentCaptor.forClass(Instant.class);
    verify(checkoutOrderRepository, times(3))
        .listarVencendoNaJanela(eq(StatusCheckout.CONFIRMED), inicio.capture(), fim.capture());

    List<Instant> inicios = inicio.getAllValues();
    assertThat(inicios)
        .containsExactlyInAnyOrder(
            antes.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS),
            antes.plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS),
            antes.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
    for (int i = 0; i < inicios.size(); i++) {
      assertThat(fim.getAllValues().get(i)).isEqualTo(inicios.get(i).plus(1, ChronoUnit.DAYS));
    }
  }

  @Test
  void pedidoSemTenantEhIgnorado() {
    CheckoutOrder semTenant = new CheckoutOrder();
    semTenant.setTenantId(null);
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of(semTenant));

    service.notificarVencimentos();

    verifyNoInteractions(tenantRepository);
    verify(credentialsEmailService, never())
        .sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void tenantInexistenteNaoGeraEmail() {
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of(pedido(Instant.parse("2026-06-10T12:00:00Z"))));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    service.notificarVencimentos();

    verify(credentialsEmailService, never())
        .sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void cadaOwnerComEmailRecebeAvisoComVencimentoNoFusoBrasileiro() {
    // 2026-06-10T02:00Z e 09/06 no fuso de Sao Paulo — o formatador precisa converter.
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of(pedido(Instant.parse("2026-06-10T02:00:00Z"))), List.of(), List.of());
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Salao & Cia")));
    when(ownersQuery.getResultList())
        .thenReturn(List.of(owner("a@x.com"), owner("  "), owner(null)));

    service.notificarVencimentos();

    ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
    verify(credentialsEmailService)
        .sendHtmlEmail(
            eq("a@x.com"),
            assunto.capture(),
            html.capture(),
            eq(null),
            eq(null),
            eq(null),
            eq("LICENSE_EXPIRING_SOON"));
    assertThat(assunto.getValue()).startsWith("Sua licenca vence ");
    assertThat(html.getValue()).contains("09/06/2026");
    // Nome do tenant escapado (& -> &amp;).
    assertThat(html.getValue()).contains("Salao &amp; Cia");
    assertThat(html.getValue()).contains("https://app.azzoholding.com.br/licenca");
  }

  @Test
  void semOwnersCaiParaOEmailDoProprioTenant() {
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of(pedido(Instant.parse("2026-06-10T12:00:00Z"))), List.of(), List.of());
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Salao B")));
    when(ownersQuery.getResultList()).thenReturn(List.of());

    service.notificarVencimentos();

    verify(credentialsEmailService)
        .sendHtmlEmail(
            eq("tenant@x.com"), anyString(), anyString(), eq(null), eq(null), eq(null),
            eq("LICENSE_EXPIRING_SOON"));
  }

  @Test
  void avisoDeUmDiaUsaTextoDeAmanha() {
    // A janela de 1 dia e a unica que produz "vence amanha"; as outras devolvem lista vazia.
    // A distancia em dias e recalculada dentro do answer para nao depender do instante do stub
    // (ancoragem: truncar para o dia elimina qualquer deriva de sub-dia).
    when(checkoutOrderRepository.listarVencendoNaJanela(eq(StatusCheckout.CONFIRMED), any(), any()))
        .thenAnswer(
            inv -> {
              Instant inicio = inv.getArgument(1);
              long dias =
                  ChronoUnit.DAYS.between(Instant.now().truncatedTo(ChronoUnit.DAYS), inicio);
              return dias == 1
                  ? List.of(pedido(Instant.parse("2026-06-10T12:00:00Z")))
                  : List.of();
            });
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Salao C")));
    when(ownersQuery.getResultList()).thenReturn(List.of(owner("c@x.com")));

    service.notificarVencimentos();

    ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
    verify(credentialsEmailService)
        .sendHtmlEmail(
            eq("c@x.com"), assunto.capture(), html.capture(), any(), any(), any(), anyString());
    assertThat(assunto.getValue()).isEqualTo("Sua licenca vence amanha");
    assertThat(html.getValue()).contains("amanha, 10/06/2026");
  }

  @Test
  void pedidoSemValidUntilMostraTravessao() {
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of(pedido(null)), List.of(), List.of());
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Salao D")));
    when(ownersQuery.getResultList()).thenReturn(List.of(owner("d@x.com")));

    service.notificarVencimentos();

    ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
    verify(credentialsEmailService)
        .sendHtmlEmail(eq("d@x.com"), anyString(), html.capture(), any(), any(), any(), anyString());
    assertThat(html.getValue()).contains("—");
  }

  /**
   * Cenario real do original: a consulta de OWNERs estoura (campo {@code active} inexistente). A
   * excecao e engolida por tenant e as tres janelas continuam sendo processadas.
   */
  @Test
  void falhaAoBuscarOwnersNaoAbortaAsDemaisJanelas() {
    when(checkoutOrderRepository.listarVencendoNaJanela(any(), any(), any()))
        .thenReturn(List.of(pedido(Instant.parse("2026-06-10T12:00:00Z"))));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant("Salao E")));
    when(ownersQuery.getResultList())
        .thenThrow(new IllegalArgumentException("Could not resolve attribute 'active'"));

    service.notificarVencimentos();

    verify(checkoutOrderRepository, times(3)).listarVencendoNaJanela(any(), any(), any());
    verify(credentialsEmailService, never())
        .sendHtmlEmail(any(), any(), any(), any(), any(), any(), any());
  }

  private CheckoutOrder pedido(Instant validUntil) {
    CheckoutOrder order = new CheckoutOrder();
    order.setTenantId(tenantId);
    order.setValidUntil(validUntil);
    return order;
  }

  private Tenant tenant(String nome) {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setName(nome);
    tenant.setEmail("tenant@x.com");
    return tenant;
  }

  private Usuario owner(String email) {
    Usuario usuario = new Usuario();
    usuario.setEmail(email);
    return usuario;
  }
}
