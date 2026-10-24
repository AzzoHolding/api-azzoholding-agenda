package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

/**
 * BNC-002: a consulta do sinal que vai ser CONSUMIDO precisa ser travada ({@code FOR UPDATE}); sem
 * isso, dois pagamentos simultaneos leem o mesmo sinal livre. Teste de estrutura: o repositorio e
 * uma interface, e o CI nao tem banco para exercitar a trava.
 */
class AppointmentDepositRepositoryLockTest {

  @Test
  void aConsultaParaConsumoEhTravadaParaEscrita() throws NoSuchMethodException {
    Method consulta =
        AppointmentDepositRepository.class.getMethod(
            "findPaidUnusedByAppointmentParaConsumo", UUID.class, String.class);

    Lock trava = consulta.getAnnotation(Lock.class);

    assertThat(trava).as("@Lock na consulta do sinal a consumir").isNotNull();
    assertThat(trava.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  /** A consulta so de leitura (ex.: ver se ha sinal) nao precisa nem deve travar a linha. */
  @Test
  void aConsultaSoDeLeituraNaoTrava() throws NoSuchMethodException {
    Method leitura =
        AppointmentDepositRepository.class.getMethod(
            "findPaidUnusedByAppointment", UUID.class, String.class);

    assertThat(leitura.getAnnotation(Lock.class)).isNull();
  }
}
