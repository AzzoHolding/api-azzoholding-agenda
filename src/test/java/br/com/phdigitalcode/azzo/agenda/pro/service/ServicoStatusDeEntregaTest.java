package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;

/**
 * Aceitar nao e entregar. A Cloud API responde 200 com um {@code wamid} e decide depois; o unico
 * lugar onde o destino da mensagem aparece e o status que ela manda pelo webhook.
 *
 * <p>Em 2026-09 isso custou dias: tres envios com wamid valido, nenhum no celular, e a tela
 * dizendo "Aceita" sem nada que distinguisse de uma entrega de verdade.
 */
class ServicoStatusDeEntregaTest {

  private final UUID tenantId = UUID.randomUUID();
  private static final String WAMID = "wamid.ABC";

  private WhatsAppMessageLogRepository repository;
  private ServicoStatusDeEntrega servico;

  @BeforeEach
  void setUp() {
    repository = mock(WhatsAppMessageLogRepository.class);
    servico = new ServicoStatusDeEntrega(repository);
  }

  private WhatsAppMessageLogEntity linha(String status) {
    WhatsAppMessageLogEntity linha = new WhatsAppMessageLogEntity();
    linha.setTenantId(tenantId);
    linha.setProviderMessageId(WAMID);
    linha.setStatus(status);
    return linha;
  }

  private void existe(WhatsAppMessageLogEntity linha) {
    when(repository.findByTenantIdAndProviderMessageId(tenantId, WAMID))
        .thenReturn(Optional.of(linha));
  }

  @Test
  @DisplayName("entregue e lida avancam o status da linha")
  void entregueELidaAvancam() {
    WhatsAppMessageLogEntity linha = linha(ServicoStatusDeEntrega.ENVIADA);
    existe(linha);

    assertThat(servico.aplicar(tenantId, WAMID, "delivered", null, null)).isTrue();
    assertThat(linha.getStatus()).isEqualTo(ServicoStatusDeEntrega.ENTREGUE);

    assertThat(servico.aplicar(tenantId, WAMID, "read", null, null)).isTrue();
    assertThat(linha.getStatus()).isEqualTo(ServicoStatusDeEntrega.LIDA);
  }

  /**
   * Status chega fora de ordem: a Meta pode mandar {@code delivered} depois de {@code read}. A tela
   * nao pode voltar atras e passar a mentir para menos.
   */
  @Test
  @DisplayName("status atrasado nao faz a linha retroceder")
  void statusAtrasadoNaoRetrocede() {
    WhatsAppMessageLogEntity linha = linha(ServicoStatusDeEntrega.LIDA);
    existe(linha);

    servico.aplicar(tenantId, WAMID, "delivered", null, null);

    assertThat(linha.getStatus()).isEqualTo(ServicoStatusDeEntrega.LIDA);
  }

  /** O motivo e a unica coisa que permite agir: marketing descartado e template recusado falham igual. */
  @Test
  @DisplayName("falha grava o codigo e o motivo da Meta")
  void falhaGravaOMotivo() {
    WhatsAppMessageLogEntity linha = linha(ServicoStatusDeEntrega.ENTREGUE);
    existe(linha);

    servico.aplicar(
        tenantId, WAMID, "failed", "131049",
        "This message was not delivered to maintain healthy ecosystem engagement");

    assertThat(linha.getStatus()).isEqualTo(ServicoStatusDeEntrega.FALHOU);
    assertThat(linha.getErrorMessage()).contains("131049").contains("healthy ecosystem");
  }

  /** Falha vence qualquer progresso: e o que a pessoa precisa ver. */
  @Test
  @DisplayName("falha se aplica mesmo depois de lida")
  void falhaVenceOProgresso() {
    WhatsAppMessageLogEntity linha = linha(ServicoStatusDeEntrega.LIDA);
    existe(linha);

    servico.aplicar(tenantId, WAMID, "failed", "131026", "Message undeliverable");

    assertThat(linha.getStatus()).isEqualTo(ServicoStatusDeEntrega.FALHOU);
  }

  @Test
  @DisplayName("status que nao conhecemos nao mexe na linha")
  void statusDesconhecidoNaoMexe() {
    existe(linha(ServicoStatusDeEntrega.ENVIADA));

    assertThat(servico.aplicar(tenantId, WAMID, "deleted", null, null)).isFalse();
    verify(repository, never()).save(any());
  }

  /** Mensagem de chat tem wamid e nao esta neste log: nao encontrar e normal, e nao erro. */
  @Test
  @DisplayName("wamid sem linha no log devolve falso sem quebrar")
  void wamidSemLinhaDevolveFalso() {
    when(repository.findByTenantIdAndProviderMessageId(tenantId, WAMID))
        .thenReturn(Optional.empty());

    assertThat(servico.aplicar(tenantId, WAMID, "delivered", null, null)).isFalse();
  }
}
