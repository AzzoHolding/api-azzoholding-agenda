package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Um uso de cupom: numa comanda ou num agendamento online (V180). */
@Entity
@Table(name = "usos_de_cupom")
@Getter
@Setter
public class UsoDeCupom {

  public static final String STATUS_ATIVO = "ATIVO";
  public static final String STATUS_DESFEITO = "DESFEITO";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "cupom_id", nullable = false)
  private UUID cupomId;

  @Column(name = "comanda_id")
  private UUID comandaId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "client_id")
  private UUID clientId;

  @Column(name = "valor_desconto", nullable = false)
  private BigDecimal valorDesconto = BigDecimal.ZERO;

  @Column(name = "status", nullable = false)
  private String status = STATUS_ATIVO;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "desfeito_em")
  private Instant desfeitoEm;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
