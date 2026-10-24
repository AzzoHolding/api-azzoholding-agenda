package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;

/**
 * Reproducao contra Postgres real do 400 "Ocorreu um erro inesperado" visto em produção em
 * {@code GET /api/v1/tenant/whatsapp/message-log} (sem status/de/ate) —
 * {@code PSQLException: could not determine data type of parameter $4}. Um teste com o
 * repositorio mockado nunca bateria nisso; ver {@code ServicoTenantWhatsappTest} para o
 * comportamento do lado do servico e {@code WhatsAppMessageLogRepositoryQueryTest} para a forma
 * da query.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WhatsAppMessageLogRepositoryFiltrarIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private WhatsAppMessageLogRepository repository;
  @Autowired private EntityManager entityManager;

  @Test
  @Transactional
  void filtrarSemStatusNemPeriodoNaoLancaErroDeTipoDeParametro() {
    UUID tenantId = UUID.randomUUID();
    entityManager
        .createNativeQuery("INSERT INTO tenants (id, name, slug) VALUES (:id, :name, :slug)")
        .setParameter("id", tenantId)
        .setParameter("name", "Salao de Teste")
        .setParameter("slug", "salao-teste-" + tenantId)
        .executeUpdate();

    WhatsAppMessageLogEntity log = new WhatsAppMessageLogEntity();
    log.setId(UUID.randomUUID());
    log.setTenantId(tenantId);
    log.setEventType("CONFIRMATION");
    log.setDestinationPhone("5511999998888");
    log.setStatus("SENT");
    log.setSentAt(Instant.now());
    entityManager.persist(log);
    entityManager.flush();

    // Exatamente a requisicao que falhava: sem status, sem de/ate — so tenantId e limit.
    List<WhatsAppMessageLogEntity> resultado =
        repository.filtrar(
            tenantId, null, Instant.EPOCH, Instant.now().plusSeconds(100L * 365 * 24 * 60 * 60),
            PageRequest.of(0, 51));

    assertThat(resultado).hasSize(1);
    assertThat(resultado.get(0).getDestinationPhone()).isEqualTo("5511999998888");
  }
}
