package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * Cobre o formato de claims exigido pelo contrato original (ver {@code ProvedorJwt} do Quarkus):
 * iss=azzo-agenda-pro, sub=userId, groups=[ROLE], tid/tenant_id redundantes, name, upn=email.
 */
class JwtServiceTest {

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "accessTokenTtlMinutes", 15);
    ReflectionTestUtils.setField(jwtService, "privateKeyLocation", "classpath:privateKey.pem");
    ReflectionTestUtils.setField(jwtService, "publicKeyLocation", "classpath:publicKey.pem");
    ReflectionTestUtils.invokeMethod(jwtService, "init");
  }

  private Usuario buildUsuario() {
    Usuario usuario = new Usuario();
    usuario.setId(UUID.randomUUID());
    usuario.setTenantId(UUID.randomUUID());
    usuario.setEmail("owner@example.com");
    usuario.setName("Dono do Salao");
    usuario.setRole(PapelUsuario.OWNER);
    usuario.setPasswordHash("hash");
    usuario.setCreatedAt(Instant.now());
    return usuario;
  }

  @Test
  void tokenGeradoContemTodasAsClaimsDoContratoOriginal() {
    Usuario usuario = buildUsuario();
    String token = jwtService.gerarToken(usuario);

    Claims claims = jwtService.parseAndValidate(token);

    assertThat(claims.getIssuer()).isEqualTo("azzo-agenda-pro");
    assertThat(claims.getSubject()).isEqualTo(usuario.getId().toString());
    assertThat(claims.get("upn")).isEqualTo(usuario.getEmail());
    assertThat(claims.get("name")).isEqualTo(usuario.getName());
    assertThat(claims.get("tid")).isEqualTo(usuario.getTenantId().toString());
    assertThat(claims.get("tenant_id")).isEqualTo(usuario.getTenantId().toString());
    assertThat(jwtService.extractRoles(claims)).containsExactly("OWNER");
    assertThat(jwtService.extractTenantId(claims)).isEqualTo(usuario.getTenantId());
  }

  @Test
  void tokenAdulteradoFalhaNaValidacao() {
    Usuario usuario = buildUsuario();
    String token = jwtService.gerarToken(usuario);
    String tampered = token.substring(0, token.length() - 4) + "abcd";

    assertThatThrownBy(() -> jwtService.parseAndValidate(tampered)).isInstanceOf(JwtException.class);
  }

  @Test
  void ttlMinimoDe5MinutosEhRespeitadoMesmoComConfigMenor() {
    ReflectionTestUtils.setField(jwtService, "accessTokenTtlMinutes", 1);
    assertThat(jwtService.accessTokenExpiresInSeconds()).isEqualTo(5 * 60);
  }
}
