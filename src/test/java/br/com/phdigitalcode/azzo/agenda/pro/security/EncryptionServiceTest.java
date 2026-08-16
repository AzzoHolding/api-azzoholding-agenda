package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EncryptionServiceTest {

  private final EncryptionService service = new EncryptionService("MyTestEncryptionKeyForUnitTests!"); // 32 bytes

  @Test
  void encryptDepoisDecryptRetornaValorOriginal() {
    String original = "segredo-totp-base32";
    String encrypted = service.encrypt(original);
    assertThat(encrypted).isNotEqualTo(original);
    assertThat(service.decrypt(encrypted)).isEqualTo(original);
  }

  @Test
  void encryptDoMesmoValorGeraSaidasDiferentes() {
    // IV aleatorio por chamada -> nao deve haver dois ciphertexts iguais para o mesmo input.
    String a = service.encrypt("valor");
    String b = service.encrypt("valor");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void valorVazioNaoEhCriptografado() {
    assertThat(service.encrypt("")).isEmpty();
    assertThat(service.decrypt("")).isEmpty();
  }

  @Test
  void chaveInvalidaFalhaNaConstrucao() {
    assertThatThrownBy(() -> new EncryptionService("chave-muito-curta"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void decryptDeValorCorrompidoLancaExcecao() {
    assertThatThrownBy(() -> service.decrypt("nao-e-base64-valido-e-nem-payload-valido!!"))
        .isInstanceOf(RuntimeException.class);
  }
}
