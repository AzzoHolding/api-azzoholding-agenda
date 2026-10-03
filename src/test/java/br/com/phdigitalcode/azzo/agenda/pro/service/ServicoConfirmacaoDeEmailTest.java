package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailVerificationToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailVerificationTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

class ServicoConfirmacaoDeEmailTest {

  private EmailVerificationTokenRepository tokens;
  private UsuarioRepository usuarios;
  private EmailJobService emailJobs;
  private ServicoConfirmacaoDeEmail servico;
  private Usuario usuario;

  @BeforeEach
  void setUp() {
    tokens = mock(EmailVerificationTokenRepository.class);
    usuarios = mock(UsuarioRepository.class);
    emailJobs = mock(EmailJobService.class);
    servico =
        new ServicoConfirmacaoDeEmail(
            tokens, usuarios, emailJobs, mock(AuditService.class), "https://app.azzo.test/");

    usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setName("Ana");
    usuario.setEmail("ana@salao.test");
    when(tokens.save(any(EmailVerificationToken.class))).thenAnswer(inv -> {
      EmailVerificationToken t = inv.getArgument(0);
      t.setId(UUID.randomUUID());
      return t;
    });
  }

  @Test
  void iniciarMarcaPendenteEEnfileiraOLinkComOTokenPuroSoNoEmail() {
    servico.iniciar(usuario);

    assertThat(usuario.isEmailConfirmationPending()).isTrue();
    ArgumentCaptor<EmailVerificationToken> token = ArgumentCaptor.forClass(EmailVerificationToken.class);
    verify(tokens).save(token.capture());
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
    verify(emailJobs).enqueueEmailVerification(any(Usuario.class), any(EmailVerificationToken.class), link.capture());
    assertThat(link.getValue()).startsWith("https://app.azzo.test/confirmar-email?token=");
    String puro = link.getValue().substring(link.getValue().indexOf("token=") + 6);
    // O banco guarda so o HASH, nunca o valor que vai no link.
    assertThat(token.getValue().getTokenHash()).isNotEqualTo(puro).hasSize(64);
    assertThat(token.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
  }

  @Test
  void confirmarLiberaAContaEConsomeOToken() {
    usuario.setEmailConfirmationPending(true);
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUserId(usuario.getId());
    when(tokens.findActiveByHash(anyString(), any(Instant.class))).thenReturn(Optional.of(token));
    when(usuarios.findById(usuario.getId())).thenReturn(Optional.of(usuario));

    servico.confirmar("token-puro");

    assertThat(usuario.isEmailConfirmationPending()).isFalse();
    assertThat(token.getUsedAt()).isNotNull();
    verify(usuarios).save(usuario);
    verify(tokens).markAllActiveAsUsedByUser(any(UUID.class), any(Instant.class));
  }

  @Test
  void tokenInvalidoOuExpiradoEhRecusado() {
    when(tokens.findActiveByHash(anyString(), any(Instant.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servico.confirmar("qualquer"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("invalido ou expirado");
    assertThatThrownBy(() -> servico.confirmar(" "))
        .isInstanceOf(ApiClientErrorException.class);
    verify(usuarios, never()).save(any());
  }

  @Test
  void reenviarGeraLinkNovoSoParaContaPendenteEODizOMesmoParaTodos() {
    usuario.setEmailConfirmationPending(true);
    when(usuarios.findByEmail("ana@salao.test")).thenReturn(Optional.of(usuario));
    when(usuarios.findByEmail("fantasma@x.test")).thenReturn(Optional.empty());

    String paraPendente = servico.reenviar("Ana@Salao.test").message;
    String paraInexistente = servico.reenviar("fantasma@x.test").message;

    assertThat(paraPendente).isEqualTo(paraInexistente);
    // Um link por conta pendente; o anterior e invalidado.
    verify(emailJobs, org.mockito.Mockito.times(1))
        .enqueueEmailVerification(any(Usuario.class), any(EmailVerificationToken.class), anyString());
    verify(tokens).markAllActiveAsUsedByUser(any(UUID.class), any(Instant.class));
  }

  @Test
  void reenviarParaContaJaConfirmadaNaoEnviaNada() {
    usuario.setEmailConfirmationPending(false);
    when(usuarios.findByEmail("ana@salao.test")).thenReturn(Optional.of(usuario));

    servico.reenviar("ana@salao.test");

    verify(emailJobs, never()).enqueueEmailVerification(any(), any(), anyString());
  }
}
