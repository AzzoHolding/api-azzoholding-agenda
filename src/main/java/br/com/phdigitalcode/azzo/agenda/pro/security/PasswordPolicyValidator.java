package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/** Porte verbatim de {@code modules/auth/application/security/PasswordPolicyValidator.java}. */
@Service
public class PasswordPolicyValidator {

  private static final int MIN_LENGTH = 8;
  private static final int MAX_LENGTH = 72;

  private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
  private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
  private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
  private static final Pattern SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

  public void validateOrThrow(String password) {
    if (!isValid(password)) {
      throw new IllegalArgumentException(
          "Senha invalida. Requisitos: minimo de 8 caracteres, letra maiuscula, letra minuscula, numero e caractere especial.");
    }
  }

  public boolean isValid(String password) {
    if (password == null) return false;
    if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) return false;
    if (!UPPERCASE.matcher(password).matches()) return false;
    if (!LOWERCASE.matcher(password).matches()) return false;
    if (!DIGIT.matcher(password).matches()) return false;
    return SPECIAL.matcher(password).matches();
  }
}
