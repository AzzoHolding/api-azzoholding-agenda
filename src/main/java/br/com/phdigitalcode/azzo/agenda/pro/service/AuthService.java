package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.AtualizarMeuPerfilRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RegisterRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ResetPasswordRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TrocarMinhaSenhaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AuthResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.GenericMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.UsuarioResponse;

/** Espelha {@code modules/auth/application/ServicoAuth.java}. */
public interface AuthService {

  /** Cria a conta SEM sessao: ela so entra depois de confirmar o e-mail. */
  void registrar(RegisterRequest request, String requestId, String ipAddress);

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken);

  GenericMessageResponse requestPasswordReset(String email);

  GenericMessageResponse resetPassword(ResetPasswordRequest request);

  /** Nome e telefone de quem esta logado (tela Perfil). */
  UsuarioResponse atualizarMeuPerfil(UUID userId, AtualizarMeuPerfilRequest request);

  /** Troca a senha conferindo a atual; derruba as sessoes, como a redefinicao pelo link. */
  GenericMessageResponse trocarMinhaSenha(UUID userId, TrocarMinhaSenhaRequest request);
}
