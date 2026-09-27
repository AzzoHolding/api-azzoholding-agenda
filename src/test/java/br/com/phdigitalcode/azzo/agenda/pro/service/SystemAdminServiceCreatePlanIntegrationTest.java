package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SystemAdminDtos;

/**
 * Trava a regressao encontrada em producao em 27/09/2026 ao criar um plano por
 * {@code /admin -> Planos}: {@code SystemAdminService.createPlan} terminava em
 * {@code UnexpectedRollbackException} (400 "Ocorreu um erro inesperado" para o cliente, plano
 * NUNCA criado) porque {@code recordPlanAudit} tentava gravar auditoria com {@code tenantId}
 * nulo — acao de plataforma, sem tenant — contra {@code audit_events.tenant_id NOT NULL}. A
 * excecao de validacao era engolida pelo {@code catch (Exception ignored)}, mas so depois de
 * marcar a transacao inteira como rollback-only (mesma transacao do REQUIRES padrao). Corrigido
 * isolando esse audit "melhor esforco" em transacao propria ({@code AuditService.
 * recordSuccessIsolated}, REQUIRES_NEW), igual ja se fazia para {@code recordDeniedIsolated}.
 *
 * <p>Roda contra um Postgres REAL com as migrations Flyway reais — {@code SystemAdminServiceTest}
 * unitaria mocka todos os repositorios/servicos e nunca bateria nesse problema de transacao.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SystemAdminServiceCreatePlanIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=azzo_app");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private SystemAdminService service;

  @Test
  void criaPlanoDeVendaInternaSemLancarRollbackInesperado() {
    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    request.name = "Azzo Solo";
    request.description = "";
    request.currency = "BRL";
    request.price = new BigDecimal("89.97");
    request.validityMonths = 1;
    request.highlight = "";
    request.featuresJson = "[]";
    request.active = true;
    request.trial = false;
    request.priority = 1;
    request.maxProfessionals = 3;
    request.exclusivoVendaInterna = true;

    SystemAdminDtos.PlanItem created = service.createPlan(request);

    assertThat(created.id).isNotNull();
    assertThat(created.name).isEqualTo("Azzo Solo");
    assertThat(created.trial).isFalse();
    assertThat(created.exclusivoVendaInterna).isTrue();

    SystemAdminDtos.PlanListResponse plans = service.listPlans();
    assertThat(plans.items).anyMatch(item -> item.id.equals(created.id));
  }
}
