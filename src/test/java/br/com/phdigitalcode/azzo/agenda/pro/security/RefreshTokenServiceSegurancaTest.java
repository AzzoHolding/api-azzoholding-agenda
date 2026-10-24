package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RefreshToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RefreshTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/** Pentest 14/10/2026: SEG-015 — rotacao sem trava e sem deteccao de reuso do refresh token. */
class RefreshTokenServiceSegurancaTest {

  private RefreshTokenRepository repositorio;
  private UsuarioRepository usuarios;
  private RefreshTokenService servico;
  private final UUID userId = UUID.randomUUID();
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    repositorio = mock(RefreshTokenRepository.class);
    usuarios = mock(UsuarioRepository.class);
    servico = new RefreshTokenService(
        repositorio, usuarios, mock(TokenRevocationService.class), mock(AcessoDeProfissional.class));
    ReflectionTestUtils.setField(servico, "refreshTokenTtlDays", 30);
    Usuario u = new Usuario();
    u.setId(userId);
    u.setTenantId(tenantId);
    when(usuarios.findById(userId)).thenReturn(Optional.of(u));
  }

  private RefreshToken token(Instant revogadoEm, String sucessor) {
    RefreshToken t = new RefreshToken();
    t.setId(UUID.randomUUID());
    t.setUserId(userId);
    t.setTenantId(tenantId);
    t.setRevokedAt(revogadoEm);
    t.setReplacedByTokenHash(sucessor);
    return t;
  }

  @Test
  void rotacaoNormalReivindicaETrocaOToken() {
    RefreshToken ativo = token(null, null);
    when(repositorio.findActiveByHash(anyString(), any())).thenReturn(Optional.of(ativo));
    when(repositorio.reivindicar(eq(ativo.getId()), anyString(), any())).thenReturn(1);

    RefreshTokenService.RefreshSession sessao = servico.rotateAndGetSession("token-cru");

    assertThat(sessao.refreshTokenRaw()).isNotBlank();
    verify(repositorio).save(any(RefreshToken.class));
  }

  @Test
  void quemPerdeACorridaNaoGanhaSegundaSessao() {
    RefreshToken ativo = token(null, null);
    when(repositorio.findActiveByHash(anyString(), any())).thenReturn(Optional.of(ativo));
    when(repositorio.reivindicar(eq(ativo.getId()), anyString(), any())).thenReturn(0);

    assertThatThrownBy(() -> servico.rotateAndGetSession("token-cru"))
        .isInstanceOf(ApiClientErrorException.class);
    verify(repositorio, never()).save(any(RefreshToken.class));
  }

  @Test
  void tokenJaTrocadoQueVoltaDepoisDaJanelaDerrubaTodasAsSessoes() {
    when(repositorio.findActiveByHash(anyString(), any())).thenReturn(Optional.empty());
    when(repositorio.findByHash(anyString()))
        .thenReturn(Optional.of(token(Instant.now().minusSeconds(3600), "hash-do-sucessor")));

    assertThatThrownBy(() -> servico.rotateAndGetSession("token-roubado"))
        .isInstanceOf(ApiClientErrorException.class);
    verify(repositorio).revokeAllByUser(eq(userId), any());
  }

  @Test
  void duasAbasRenovandoJuntasNaoDerrubamASessao() {
    when(repositorio.findActiveByHash(anyString(), any())).thenReturn(Optional.empty());
    when(repositorio.findByHash(anyString()))
        .thenReturn(Optional.of(token(Instant.now().minusSeconds(2), "hash-do-sucessor")));

    assertThatThrownBy(() -> servico.rotateAndGetSession("token-da-outra-aba"))
        .isInstanceOf(ApiClientErrorException.class);
    verify(repositorio, never()).revokeAllByUser(any(), any());
  }

  @Test
  void tokenSoRevogadoNoLogoutNaoEReuso() {
    when(repositorio.findActiveByHash(anyString(), any())).thenReturn(Optional.empty());
    when(repositorio.findByHash(anyString())).thenReturn(Optional.of(token(Instant.now().minusSeconds(3600), null)));

    assertThatThrownBy(() -> servico.rotateAndGetSession("token-velho"))
        .isInstanceOf(ApiClientErrorException.class);
    verify(repositorio, never()).revokeAllByUser(any(), any());
  }
}
