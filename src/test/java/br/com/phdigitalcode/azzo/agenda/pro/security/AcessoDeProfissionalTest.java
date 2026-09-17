package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;

/** Quem saiu da equipe nao entra mais — e o dono nunca fica trancado fora do proprio salao. */
class AcessoDeProfissionalTest {

  private ProfissionalRepository profissionalRepository;
  private AcessoDeProfissional acesso;
  private Usuario usuario;
  private Profissional cadastro;

  @BeforeEach
  void setUp() {
    profissionalRepository = mock(ProfissionalRepository.class);
    acesso = new AcessoDeProfissional(profissionalRepository);
    usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setRole(PapelUsuario.PROFESSIONAL);
    cadastro = new Profissional();
    cadastro.setUserId(usuario.getId());
    when(profissionalRepository.findByTenantIdAndUserId(any(), any()))
        .thenReturn(Optional.of(cadastro));
  }

  @Test
  void profissionalDesativadoEBarrado() {
    cadastro.setActive(false);
    assertThat(acesso.desativado(usuario)).isTrue();
  }

  @Test
  void profissionalAtivoPassa() {
    cadastro.setActive(true);
    assertThat(acesso.desativado(usuario)).isFalse();
  }

  /** O dono pode ter o proprio cadastro ("Sou eu"): tira-lo da agenda nao o tranca fora. */
  @Test
  void donoComCadastroDesativadoContinuaEntrando() {
    cadastro.setActive(false);
    usuario.setRole(PapelUsuario.OWNER);
    assertThat(acesso.desativado(usuario)).isFalse();
  }

  /** Recepcao (STAFF) sem cadastro de profissional nao e afetada por esta regra. */
  @Test
  void usuarioSemCadastroDeProfissionalPassa() {
    usuario.setRole(PapelUsuario.STAFF);
    when(profissionalRepository.findByTenantIdAndUserId(any(), any())).thenReturn(Optional.empty());
    assertThat(acesso.desativado(usuario)).isFalse();
  }
}
