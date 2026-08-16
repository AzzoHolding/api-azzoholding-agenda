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

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalIdempotencyRequestEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalIdempotencyRequestRepository;

/**
 * Cobre {@code modules/fiscal/application/FiscalIdempotencyService.java}.
 *
 * <p>⚠️ <b>Armadilha 7, na direcao que engana:</b> o servico chama {@code findByKey}, que e
 * {@code default} na interface. O mock do Mockito <b>nao executa o corpo do {@code default}</b>,
 * entao a delegacao para {@code findByTenantIdAndOperationAndIdempotencyKey} nunca acontece —
 * estubar a consulta derivada estuba um metodo que ninguem chama, e {@code findByKey} devolve
 * {@code Optional.empty()} calado, fazendo a idempotencia parecer quebrada. O que se estuba aqui e
 * sempre <b>o metodo que o codigo de producao invoca</b>. A delegacao do {@code default} fica
 * coberta pelo teste do repositorio, nao por este.
 */
@ExtendWith(MockitoExtension.class)
class FiscalIdempotencyServiceTest {

  @Mock private FiscalIdempotencyRequestRepository fiscalIdempotencyRequestRepository;

  private FiscalIdempotencyService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FiscalIdempotencyService(fiscalIdempotencyRequestRepository, new ObjectMapper());
  }

  @Test
  void aMesmaChaveDevolveARespostaGravadaSemReexecutarAOperacao() {
    Map<String, FiscalIdempotencyRequestEntity> armazenamento = comArmazenamentoEmMemoria();
    AtomicInteger execucoes = new AtomicInteger();

    String primeira =
        service.execute(
            tenantId,
            "FISCAL_CREATE_INVOICE",
            "key-1",
            () -> {
              execucoes.incrementAndGet();
              return "ok-first";
            },
            String.class);

    String segunda =
        service.execute(
            tenantId,
            "FISCAL_CREATE_INVOICE",
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
            "FISCAL_AUTHORIZE_INVOICE",
            "same-key",
            () -> "A-" + execucoes.incrementAndGet(),
            String.class);
    String b =
        service.execute(
            tenantB,
            "FISCAL_AUTHORIZE_INVOICE",
            "same-key",
            () -> "B-" + execucoes.incrementAndGet(),
            String.class);

    assertThat(a).isEqualTo("A-1");
    assertThat(b).isEqualTo("B-2");
    assertThat(execucoes.get()).isEqualTo(2);
  }

  @Test
  void normalizaAOperacaoParaMaiusculaMasPreservaACaixaDaChave() {
    when(fiscalIdempotencyRequestRepository.findByKey(any(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(fiscalIdempotencyRequestRepository.saveAndFlush(
            any(FiscalIdempotencyRequestEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(tenantId, "  fiscal_cancel  ", "  Key-MiXeD  ", () -> "ok", String.class);

    ArgumentCaptor<FiscalIdempotencyRequestEntity> gravada =
        ArgumentCaptor.forClass(FiscalIdempotencyRequestEntity.class);
    verify(fiscalIdempotencyRequestRepository).saveAndFlush(gravada.capture());
    assertThat(gravada.getValue().getOperation()).isEqualTo("FISCAL_CANCEL");
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

    verifyNoInteractions(fiscalIdempotencyRequestRepository);
  }

  @Test
  void rejeitaSupplierOuResponseTypeNulos() {
    assertThatThrownBy(() -> service.execute(tenantId, "OP", "key", null, String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("supplier obrigatorio");
    assertThatThrownBy(() -> service.execute(tenantId, "OP", "key", () -> "ok", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("responseType obrigatorio");

    verify(fiscalIdempotencyRequestRepository, never()).saveAndFlush(any());
  }

  @Test
  void jsonCorrompidoNaLinhaGravadaViraIllegalState() {
    FiscalIdempotencyRequestEntity existente = new FiscalIdempotencyRequestEntity();
    existente.setResponseJson("{ isto nao e json");
    when(fiscalIdempotencyRequestRepository.findByKey(tenantId, "OP", "key"))
        .thenReturn(Optional.of(existente));

    assertThatThrownBy(() -> service.execute(tenantId, "OP", "key", () -> "ok", String.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Falha ao desserializar resposta idempotente fiscal");
  }

  /** Liga o mock a um mapa real para que a segunda chamada enxergue o que a primeira gravou. */
  private Map<String, FiscalIdempotencyRequestEntity> comArmazenamentoEmMemoria() {
    Map<String, FiscalIdempotencyRequestEntity> armazenamento = new HashMap<>();

    when(fiscalIdempotencyRequestRepository.findByKey(any(), anyString(), anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    armazenamento.get(
                        invocation.getArgument(0)
                            + "|"
                            + invocation.getArgument(1)
                            + "|"
                            + invocation.getArgument(2))));

    when(fiscalIdempotencyRequestRepository.saveAndFlush(
            any(FiscalIdempotencyRequestEntity.class)))
        .thenAnswer(
            invocation -> {
              FiscalIdempotencyRequestEntity entity = invocation.getArgument(0);
              armazenamento.put(
                  entity.getTenantId()
                      + "|"
                      + entity.getOperation()
                      + "|"
                      + entity.getIdempotencyKey(),
                  entity);
              return entity;
            });

    return armazenamento;
  }
}
