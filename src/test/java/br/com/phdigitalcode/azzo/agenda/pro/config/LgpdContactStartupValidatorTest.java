package br.com.phdigitalcode.azzo.agenda.pro.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

/** Espelha {@code modules/lgpd/infrastructure/LgpdContactStartupValidator.java}. */
class LgpdContactStartupValidatorTest {

  private LgpdContactStartupValidator build(String email, String channel, String sla) throws Exception {
    LgpdContactStartupValidator validator = new LgpdContactStartupValidator();
    setField(validator, "lgpdContactEmail", email);
    setField(validator, "lgpdContactChannel", channel);
    setField(validator, "lgpdContactResponseSla", sla);
    return validator;
  }

  private void setField(Object target, String name, Object value) throws Exception {
    Field field = LgpdContactStartupValidator.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  void naoLancaQuandoAsTresConfiguracoesEstaoValidas() throws Exception {
    LgpdContactStartupValidator validator = build("privacidade@azzo.com", "e-mail", "15 dias corridos");
    assertThatCode(validator::onReady).doesNotThrowAnyException();
  }

  @Test
  void lancaQuandoEmailAusente() throws Exception {
    LgpdContactStartupValidator validator = build(null, "e-mail", "15 dias corridos");
    assertThatThrownBy(validator::onReady).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void lancaQuandoEmailEmBranco() throws Exception {
    LgpdContactStartupValidator validator = build("   ", "e-mail", "15 dias corridos");
    assertThatThrownBy(validator::onReady).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void lancaQuandoEmailEhUnset() throws Exception {
    LgpdContactStartupValidator validator = build("__unset__", "e-mail", "15 dias corridos");
    assertThatThrownBy(validator::onReady).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void lancaQuandoEmailEhUnsetCaseInsensitive() throws Exception {
    LgpdContactStartupValidator validator = build("UNSET", "e-mail", "15 dias corridos");
    assertThatThrownBy(validator::onReady).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void lancaQuandoChannelAusente() throws Exception {
    LgpdContactStartupValidator validator = build("privacidade@azzo.com", null, "15 dias corridos");
    assertThatThrownBy(validator::onReady).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void lancaQuandoResponseSlaAusente() throws Exception {
    LgpdContactStartupValidator validator = build("privacidade@azzo.com", "e-mail", "");
    assertThatThrownBy(validator::onReady).isInstanceOf(IllegalStateException.class);
  }
}
