package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;

class RegistroDeEnvioDoWhatsappTest {

  private final WhatsAppMessageLogRepository repository = mock(WhatsAppMessageLogRepository.class);
  private final RegistroDeEnvioDoWhatsapp registro = new RegistroDeEnvioDoWhatsapp(repository);
  private final UUID tenantId = UUID.randomUUID();
  private final UUID agendamentoId = UUID.randomUUID();

  private WhatsAppMessageLogEntity gravado() {
    ArgumentCaptor<WhatsAppMessageLogEntity> linha = ArgumentCaptor.forClass(WhatsAppMessageLogEntity.class);
    verify(repository).save(linha.capture());
    return linha.getValue();
  }

  @Test
  void envioAceitoGravaComOWamidEStatusEnviada() {
    registro.registrar(
        tenantId, agendamentoId, RegistroDeEnvioDoWhatsapp.CONFIRMACAO, "5521987599613", "Olá!",
        ChannelSendResult.sent("wamid.1"));

    WhatsAppMessageLogEntity linha = gravado();
    assertThat(linha.getAppointmentId()).isEqualTo(agendamentoId);
    assertThat(linha.getEventType()).isEqualTo("APPOINTMENT_CONFIRMATION");
    assertThat(linha.getProviderMessageId()).isEqualTo("wamid.1");
    assertThat(linha.getStatus()).isEqualTo("SENT");
    assertThat(linha.getErrorMessage()).isNull();
  }

  @Test
  void envioRecusadoGravaFalhouComOMotivo() {
    registro.registrar(
        tenantId, agendamentoId, RegistroDeEnvioDoWhatsapp.LEMBRETE, "5521987599613", "Lembrete",
        ChannelSendResult.failed("131026", "Message undeliverable"));

    WhatsAppMessageLogEntity linha = gravado();
    assertThat(linha.getStatus()).isEqualTo("FAILED");
    assertThat(linha.getErrorMessage()).isEqualTo("131026: Message undeliverable");
  }

  @Test
  void falhaAoGravarNaoPropaga() {
    when(repository.save(any())).thenThrow(new IllegalStateException("banco fora"));

    assertThatCode(
            () -> registro.registrar(
                tenantId, agendamentoId, RegistroDeEnvioDoWhatsapp.CANCELAMENTO, "5521987599613", "x",
                ChannelSendResult.sent("wamid.2")))
        .doesNotThrowAnyException();
  }
}
