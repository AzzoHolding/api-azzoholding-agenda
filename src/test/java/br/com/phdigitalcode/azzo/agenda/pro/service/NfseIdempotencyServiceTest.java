package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseIdempotencyRequestEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseIdempotencyRequestRepository;

/**
 * Cobre {@code modules/nfse/application/NfseIdempotencyService.java} (Fronteira 2 do porte de
 * {@code nfse}, ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26).
 *
 * <p>Mesma armadilha ja documentada em {@link FiscalIdempotencyServiceTest}: {@code findByKey} e
 * {@code default} em {@link NfseIdempotencyRequestRepository} e delega para {@code
 * findByTenantIdAndOperationAndIdempotencyKey}, mas o mock do Mockito nao executa o corpo do
 * {@code default} — por isso este teste estuba {@code findByKey} diretamente, o mesmo metodo que o
 * service realmente chama, e nao a query derivada por baixo dele.
 */
@ExtendWith(MockitoExtension.class)
class NfseIdempotencyServiceTest {

  @Mock private NfseIdempotencyRequestRepository nfseIdempotencyRequestRepository;

  private NfseIdempotencyService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new NfseIdempotencyService(nfseIdempotencyRequestRepository, new ObjectMapper());
  }

  @Test
  void aMesmaChaveDevolveARespostaGravadaSemReexecutarAOperacao() {
    Map<String, NfseIdempotencyRequestEntity> armazenamento = comArmazenamentoEmMemoria();
    AtomicInteger execucoes = new AtomicInteger();

    String primeira =
        service.execute(
            tenantId,
            "NFSE_AUTHORIZE",
            "key-1",
            () -> {
              execucoes.incrementAndGet();
              return "ok-first";
            },
            String.class);

    String segunda =
        service.execute(
            tenantId,
            "NFSE_AUTHORIZE",
            "key-1",
            () -> {
              execucoes.incrementAndGet();
              return "ok-second";
            },
            String.class);

    assertThat(primeira).isEqualTo("ok-first");
    assertThat(segunda).isEqualTo("ok-first");
    assertThat(execucoes.get()).isEqualTo(1);
    assertThat(armazenamento).hasSize(1);
  }

  @Test
  void aIdempotenciaEIsoladaPorTenant() {
    comArmazenamentoEmMemoria();
    AtomicInteger execucoes = new AtomicInteger();
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();

    String a =
        service.execute(
            tenantA,
            "NFSE_CANCEL",
            "same-key",
            () -> "A-" + execucoes.incrementAndGet(),
            String.class);
    String b =
        service.execute(
            tenantB,
            "NFSE_CANCEL",
            "same-key",
            () -> "B-" + execucoes.incrementAndGet(),
            String.class);

    assertThat(a).isEqualTo("A-1");
    assertThat(b).isEqualTo("B-2");
    assertThat(execucoes.get()).isEqualTo(2);
  }

  @Test
  void normalizaAOperacaoParaMaiusculaMasPreservaACaixaDaChave() {
    when(nfseIdempotencyRequestRepository.findByKey(any(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(nfseIdempotencyRequestRepository.saveAndFlush(any(NfseIdempotencyRequestEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(tenantId, "  nfse_cancel  ", "  Key-MiXeD  ", () -> "ok", String.class);

    ArgumentCaptor<NfseIdempotencyRequestEntity> gravada =
        ArgumentCaptor.forClass(NfseIdempotencyRequestEntity.class);
    verify(nfseIdempotencyRequestRepository).saveAndFlush(gravada.capture());
    assertThat(gravada.getValue().getOperation()).isEqualTo("NFSE_CANCEL");
    assertThat(gravada.getValue().getIdempotencyKey()).isEqualTo("Key-MiXeD");
    assertThat(gravada.getValue().getResponseJson()).isEqualTo("\"ok\"");
  }

  /**
   * ⚠️ Argumento de idempotencia faltando <b>nao</b> e erro: a operacao roda direto e nada e
   * gravado. Do original — a requisicao perde a protecao em silencio.
   */
  @Test
  void semChaveOuOperacaoAOperacaoRodaDiretoSemGravarNada() {
    AtomicInteger execucoes = new AtomicInteger();

    assertThat(
            service.execute(
                null, "OP", "key", () -> "r" + execucoes.incrementAndGet(), String.class))
        .isEqualTo("r1");
    assertThat(
            service.execute(
                tenantId, "  ", "key", () -> "r" + execucoes.incrementAndGet(), String.class))
        .isEqualTo("r2");
    assertThat(
            service.execute(
                tenantId, "OP", null, () -> "r" + execucoes.incrementAndGet(), String.class))
        .isEqualTo("r3");

    verifyNoInteractions(nfseIdempotencyRequestRepository);
  }

  @Test
  void rejeitaSupplierOuResponseTypeNulos() {
    assertThatThrownBy(() -> service.execute(tenantId, "OP", "key", null, String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("supplier obrigatorio");
    assertThatThrownBy(() -> service.execute(tenantId, "OP", "key", () -> "ok", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("responseType obrigatorio");

    verify(nfseIdempotencyRequestRepository, never()).saveAndFlush(any());
  }

  @Test
  void jsonCorrompidoNaLinhaGravadaViraIllegalState() {
    NfseIdempotencyRequestEntity existente = new NfseIdempotencyRequestEntity();
    existente.setResponseJson("{ isto nao e json");
    when(nfseIdempotencyRequestRepository.findByKey(tenantId, "OP", "key"))
        .thenReturn(Optional.of(existente));

    assertThatThrownBy(() -> service.execute(tenantId, "OP", "key", () -> "ok", String.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Falha ao desserializar resposta idempotente NFS-e");
  }

  /** Liga o mock a um mapa real para que a segunda chamada enxergue o que a primeira gravou. */
  private Map<String, NfseIdempotencyRequestEntity> comArmazenamentoEmMemoria() {
    Map<String, NfseIdempotencyRequestEntity> armazenamento = new HashMap<>();

    when(nfseIdempotencyRequestRepository.findByKey(any(), anyString(), anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    armazenamento.get(
                        invocation.getArgument(0)
                            + "|"
                            + invocation.getArgument(1)
                            + "|"
                            + invocation.getArgument(2))));

    when(nfseIdempotencyRequestRepository.saveAndFlush(any(NfseIdempotencyRequestEntity.class)))
        .thenAnswer(
            invocation -> {
              NfseIdempotencyRequestEntity entity = invocation.getArgument(0);
              armazenamento.put(
                  entity.getTenantId() + "|" + entity.getOperation() + "|" + entity.getIdempotencyKey(),
                  entity);
              return entity;
            });

    return armazenamento;
  }
}
