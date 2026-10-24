package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Teste do webhook simulado de 24/10/2026: o modo manual sumia da lista do chat. */
class ConversationWithClientRowTest {

  @Test
  void modoManualEmTimestampSemFusoELidoNoFusoDaJvm() {
    Object[] linha = new Object[16];
    linha[0] = UUID.randomUUID();
    linha[7] = LocalDateTime.of(2026, 10, 25, 17, 40, 41);
    linha[9] = "CLIENTE_PEDIU_ATENDENTE";
    linha[10] = 1;

    ChatConversationRepository.ConversationWithClientRow r = ChatConversationRepository.ConversationWithClientRow.from(linha);

    // A linha chega no fuso da JVM (ver toInstant).
    assertThat(r.manualModeUntil())
        .isEqualTo(LocalDateTime.of(2026, 10, 25, 17, 40, 41).atZone(java.time.ZoneId.systemDefault()).toInstant());
    assertThat(r.manualModeReason()).isEqualTo("CLIENTE_PEDIU_ATENDENTE");
  }
}
