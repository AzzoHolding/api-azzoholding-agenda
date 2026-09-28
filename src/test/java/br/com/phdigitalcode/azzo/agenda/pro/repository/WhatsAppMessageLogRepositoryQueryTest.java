package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * Achado em 28/09/2026, mesma familia do {@code AuditEventRepositoryQueryTest} (2026-09-20), mas
 * em JPQL — o comentario anterior desta query dizia que "em JPQL o Hibernate tipa o parametro, e
 * o padrao funciona", só que nao funcionava para {@code Instant}: {@code :de is null}/
 * {@code :ate is null} gerava {@code PSQLException: could not determine data type of parameter}
 * em produção (tela de mensagens do WhatsApp sempre respondia 400). Diferente da auditoria, a
 * correção aqui não foi CAST — foi nunca deixar {@code de}/{@code ate} chegarem nulos
 * (ver {@code ServicoTenantWhatsappTest.dataInvalidaNaoViraFiltroNemErro}); {@code :status is
 * null} continua seguro (String).
 */
class WhatsAppMessageLogRepositoryQueryTest {

  @Test
  @DisplayName("a consulta nao checa nulidade de parametro Instant (de/ate)")
  void consultaNaoChecaNulidadeDeParametroTemporal() throws Exception {
    Method metodo =
        WhatsAppMessageLogRepository.class.getMethod(
            "filtrar", UUID.class, String.class, Instant.class, Instant.class, Pageable.class);
    String jpql = metodo.getAnnotation(Query.class).value();

    assertThat(jpql).doesNotContainIgnoringCase(":de is null").doesNotContainIgnoringCase(":ate is null");
    assertThat(jpql).contains("m.sentAt >= :de").contains("m.sentAt <= :ate");
    // :status is null continua o padrao seguro (String) — nao deve ser removido junto.
    assertThat(jpql).containsIgnoringCase(":status is null");
  }
}
