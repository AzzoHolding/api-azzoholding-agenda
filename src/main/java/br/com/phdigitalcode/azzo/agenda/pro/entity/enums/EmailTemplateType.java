package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/** Espelha {@code modules/email/domain/entity/EmailTemplateType.java}. */
public enum EmailTemplateType {
  PASSWORD_RESET("Redefinicao de senha"),
  EMAIL_VERIFICATION("Confirmacao de e-mail");

  private final String label;

  EmailTemplateType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
