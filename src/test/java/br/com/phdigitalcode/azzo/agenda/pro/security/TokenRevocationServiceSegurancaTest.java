package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/** Pentest 14/10/2026: SEG-016 — token de usuario excluido seguia valido ate expirar. */
class TokenRevocationServiceSegurancaTest {

  @Test
  void usuarioQueNaoExisteMaisTemOTokenRevogado() {
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    UUID excluido = UUID.randomUUID();
    when(usuarios.findById(excluido)).thenReturn(Optional.empty());

    assertThat(new TokenRevocationService(usuarios).isRevoked(excluido, Instant.now().getEpochSecond())).isTrue();
  }

  @Test
  void usuarioExistenteSemRevogacaoContinuaValido() {
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    Usuario u = new Usuario();
    u.setId(UUID.randomUUID());
    when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

    assertThat(new TokenRevocationService(usuarios).isRevoked(u.getId(), Instant.now().getEpochSecond())).isFalse();
  }
}
