package br.com.phdigitalcode.azzo.agenda.pro.service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RegisterRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ResetPasswordRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AuthResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.GenericMessageResponse;

/** Espelha {@code modules/auth/application/ServicoAuth.java}. */
public interface AuthService {

  /** Cria a conta SEM sessao: ela so entra depois de confirmar o e-mail. */
  void registrar(RegisterRequest request, String requestId, String ipAddress);

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken);

  GenericMessageResponse requestPasswordReset(String email);

  GenericMessageResponse resetPassword(ResetPasswordRequest request);
}
