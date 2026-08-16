package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalSequenceControlEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalSequenceControlRepository;

/**
 * Cobre {@code modules/fiscal/application/FiscalSequenceService.java}.
 *
 * <p>Alem das guardas de argumento e da normalizacao, trava dois pontos que o porte poderia ter
 * perdido: a criacao da sequencia usa {@code saveAndFlush} (INSERT imediato, como o
 * {@code persist()} do Panache — armadilha 3), e a sequencia ja existente <b>nao</b> e salva de
 * novo, apenas incrementada por dirty checking.
 */
@ExtendWith(MockitoExtension.class)
class FiscalSequenceServiceTest {

  @Mock private FiscalSequenceControlRepository fiscalSequenceControlRepository;

  private FiscalSequenceService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new FiscalSequenceService(fiscalSequenceControlRepository);
  }

  @Test
  void aPrimeiraEmissaoDaSerieRecebeONumeroUm() {
    when(fiscalSequenceControlRepository.findForUpdate(any(), anyString(), anyInt(), anyString()))
        .thenReturn(Optional.empty());
    when(fiscalSequenceControlRepository.saveAndFlush(any(FiscalSequenceControlEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    int numero = service.nextNumber(tenantId, "55", 1, "HOMOLOGACAO");

    assertThat(numero).isEqualTo(1);

    ArgumentCaptor<FiscalSequenceControlEntity> criada =
        ArgumentCaptor.forClass(FiscalSequenceControlEntity.class);
    verify(fiscalSequenceControlRepository).saveAndFlush(criada.capture());
    assertThat(criada.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(criada.getValue().getModelo()).isEqualTo("55");
    assertThat(criada.getValue().getSerie()).isEqualTo(1);
    assertThat(criada.getValue().getAmbiente()).isEqualTo("HOMOLOGACAO");
  }

  @Test
  void aSequenciaExistenteEApenasIncrementadaSemNovoSave() {
    FiscalSequenceControlEntity existente = new FiscalSequenceControlEntity();
    existente.setTenantId(tenantId);
    existente.setUltimoNumero(41);
    when(fiscalSequenceControlRepository.findForUpdate(tenantId, "55", 1, "PRODUCAO"))
        .thenReturn(Optional.of(existente));

    int numero = service.nextNumber(tenantId, "55", 1, "PRODUCAO");

    assertThat(numero).isEqualTo(42);
    assertThat(existente.getUltimoNumero()).isEqualTo(42);
    verify(fiscalSequenceControlRepository, never()).saveAndFlush(any());
    verify(fiscalSequenceControlRepository, never()).save(any());
  }

  @Test
  void normalizaModeloEAmbienteAntesDeConsultar() {
    FiscalSequenceControlEntity existente = new FiscalSequenceControlEntity();
    existente.setUltimoNumero(0);
    when(fiscalSequenceControlRepository.findForUpdate(tenantId, "NFCE", 2, "PRODUCAO"))
        .thenReturn(Optional.of(existente));

    service.nextNumber(tenantId, "  nfce  ", 2, "  producao  ");

    verify(fiscalSequenceControlRepository).findForUpdate(tenantId, "NFCE", 2, "PRODUCAO");
  }

  @Test
  void rejeitaArgumentosObrigatoriosAusentes() {
    assertThatThrownBy(() -> service.nextNumber(null, "55", 1, "PRODUCAO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tenantId obrigatorio");
    assertThatThrownBy(() -> service.nextNumber(tenantId, "  ", 1, "PRODUCAO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("modelo obrigatorio");
    assertThatThrownBy(() -> service.nextNumber(tenantId, "55", 1, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ambiente obrigatorio");
    assertThatThrownBy(() -> service.nextNumber(tenantId, "55", 0, "PRODUCAO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("serie deve ser maior que zero");

    verify(fiscalSequenceControlRepository, never())
        .findForUpdate(any(), anyString(), anyInt(), anyString());
  }

  /**
   * Porte do teste de concorrencia do original. Aqui o {@code synchronized} do servico e a unica
   * defesa (o repositorio em memoria nao tem lock de linha), e o que se prova e que 50 chamadas
   * paralelas produzem 1..50 sem repetir nem pular — nota fiscal nao aceita buraco na numeracao.
   */
  @Test
  void geraNumeracaoContinuaSobConcorrencia() throws Exception {
    FiscalSequenceService servicoComRepoEmMemoria =
        new FiscalSequenceService(repositorioEmMemoria());

    int requisicoes = 50;
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      List<Callable<Integer>> tarefas = new ArrayList<>();
      for (int i = 0; i < requisicoes; i++) {
        tarefas.add(() -> servicoComRepoEmMemoria.nextNumber(tenantId, "55", 1, "HOMOLOGACAO"));
      }

      List<Integer> valores = new ArrayList<>();
      for (Future<Integer> future : executor.invokeAll(tarefas)) {
        valores.add(future.get());
      }

      Collections.sort(valores);
      assertThat(valores).hasSize(requisicoes);
      for (int i = 0; i < requisicoes; i++) {
        assertThat(valores.get(i)).isEqualTo(i + 1);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Repositorio com estado real, compartilhado entre as threads — o mock com {@code thenReturn}
   * fixo nao serviria, porque a segunda chamada precisa enxergar a linha que a primeira criou. Toda
   * a stub e feita antes de as threads comecarem.
   */
  private FiscalSequenceControlRepository repositorioEmMemoria() {
    Map<String, FiscalSequenceControlEntity> armazenamento = new ConcurrentHashMap<>();
    FiscalSequenceControlRepository repositorio = org.mockito.Mockito.mock(
        FiscalSequenceControlRepository.class);

    when(repositorio.findForUpdate(any(), anyString(), anyInt(), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(armazenamento.get(chave(
            invocation.getArgument(0),
            invocation.getArgument(1),
            invocation.getArgument(2),
            invocation.getArgument(3)))));

    when(repositorio.saveAndFlush(any(FiscalSequenceControlEntity.class)))
        .thenAnswer(invocation -> {
          FiscalSequenceControlEntity entity = invocation.getArgument(0);
          armazenamento.put(
              chave(
                  entity.getTenantId(), entity.getModelo(), entity.getSerie(), entity.getAmbiente()),
              entity);
          return entity;
        });

    return repositorio;
  }

  private static String chave(UUID tenantId, String modelo, Integer serie, String ambiente) {
    return tenantId + "|" + modelo + "|" + serie + "|" + ambiente;
  }
}
