package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code domain/entity/Tenant.java} do Quarkus original (tabela {@code tenants}).
 *
 * <p>RESOLUCAO DA PENDENCIA registrada na Etapa 4 (security/common + auth): esta e agora a
 * entidade COMPLETA de {@code tenants} (todas as colunas escalares), substituindo a projecao
 * minima criada anteriormente para o fluxo de registro. {@code AuthServiceImpl} continua
 * funcionando sem alteracao porque usa apenas os getters/setters que ja existiam na projecao
 * (id/name/slug/phone/email/planStatusId/createdAt), agora um subconjunto desta classe.
 *
 * <p>DECISAO DELIBERADA: ao contrario do original (que mapeia {@code @OneToMany} para
 * Usuario/Profissional/Servico/Cliente/Agendamento/Transacao/Notification e {@code @OneToOne}
 * para TenantAddress/TenantWhatsAppConfig/TenantTelegramConfig), esta entidade NAO mapeia
 * nenhuma colecao bidirecional. Todo o codigo do projeto (original e portado) já filtra por
 * {@code tenant_id} explicitamente em cada repositorio/query — nunca navega o grafo de objetos a
 * partir de {@code Tenant} — entao as colecoes bidirecionais so adicionariam risco de N+1,
 * problemas de equals/hashCode e proxies Hibernate sem nenhum ganho funcional. Ver nota de risco
 * "cuidado com relacionamentos bidirecionais" no guia de qualidade do projeto.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false, unique = true)
  private String slug;

  @Column(name = "description")
  private String description;

  @Column(name = "logo")
  private String logo;

  @Column(name = "phone")
  private String phone;

  @Column(name = "whatsapp")
  private String whatsapp;

  @Column(name = "email")
  private String email;

  @Column(name = "website")
  private String website;

  @Column(name = "instagram")
  private String instagram;

  @Column(name = "facebook")
  private String facebook;

  @Column(name = "document")
  private String document;

  @Column(name = "plan_status_id")
  private UUID planStatusId;

  @Column(name = "asaas_customer_id", unique = true)
  private String asaasCustomerId;

  @Column(name = "trial_document_hash")
  private String trialDocumentHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "onboarding_complete", nullable = false)
  private Boolean onboardingComplete = false;

  @Column(name = "onboarding_step", nullable = false)
  private Integer onboardingStep = 0;

  @Column(name = "onboarding_skipped", nullable = false)
  private Boolean onboardingSkipped = false;

  @Column(name = "onboarding_completed_at")
  private LocalDateTime onboardingCompletedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (onboardingComplete == null) onboardingComplete = false;
    if (onboardingStep == null) onboardingStep = 0;
    if (onboardingSkipped == null) onboardingSkipped = false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Tenant other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
