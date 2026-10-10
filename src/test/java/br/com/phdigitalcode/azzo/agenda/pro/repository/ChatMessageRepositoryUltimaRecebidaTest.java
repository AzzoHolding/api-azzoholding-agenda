package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * INT-002: {@code MAX(timestamptz)} de consulta nativa volta com tipos diferentes conforme o
 * driver/Hibernate; o metodo publico precisa entregar sempre um {@link Instant}.
 */
class ChatMessageRepositoryUltimaRecebidaTest {

  private final UUID tenant = UUID.randomUUID();
  private final UUID cliente = UUID.randomUUID();
  private final Instant instante = Instant.parse("2026-10-09T12:30:00Z");

  private ChatMessageRepository repositorioDevolvendo(Object bruto) {
    ChatMessageRepository repo = mock(ChatMessageRepository.class, CALLS_REAL_METHODS);
    doReturn(bruto).when(repo).ultimaMensagemRecebidaNoWhatsAppRaw(any(), any());
    return repo;
  }

  @Test
  void nuncaEscreveuDevolveVazio() {
    assertThat(repositorioDevolvendo(null).ultimaMensagemRecebidaNoWhatsApp(tenant, cliente)).isEmpty();
  }

  @Test
  void converteOsTiposQueOHibernateOuODriverPodemDevolver() {
    assertThat(repositorioDevolvendo(instante).ultimaMensagemRecebidaNoWhatsApp(tenant, cliente))
        .contains(instante);
    assertThat(
            repositorioDevolvendo(OffsetDateTime.ofInstant(instante, ZoneOffset.ofHours(-3)))
                .ultimaMensagemRecebidaNoWhatsApp(tenant, cliente))
        .contains(instante);
    assertThat(
            repositorioDevolvendo(ZonedDateTime.ofInstant(instante, ZoneOffset.UTC))
                .ultimaMensagemRecebidaNoWhatsApp(tenant, cliente))
        .contains(instante);
    assertThat(
            repositorioDevolvendo(Timestamp.from(instante)).ultimaMensagemRecebidaNoWhatsApp(tenant, cliente))
        .contains(instante);
    assertThat(
            repositorioDevolvendo(new java.util.Date(instante.toEpochMilli()))
                .ultimaMensagemRecebidaNoWhatsApp(tenant, cliente))
        .contains(instante);
  }

  /** Falhar alto: um tipo desconhecido virando "janela fechada" esconderia o problema. */
  @Test
  void tipoInesperadoFalhaEmVezDeFingirJanelaFechada() {
    assertThatThrownBy(() -> repositorioDevolvendo("2026-10-09").ultimaMensagemRecebidaNoWhatsApp(tenant, cliente))
        .isInstanceOf(IllegalStateException.class);
  }
}
