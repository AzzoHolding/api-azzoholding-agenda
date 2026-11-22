package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

/** Onde a ultima busca na Receita parou, por ambiente (o maior NSU recebido). Ver V163. */
@Entity
@Table(name = "nfse_receita_cursores")
@IdClass(NfseReceitaCursorEntity.Chave.class)
@Getter
@Setter
public class NfseReceitaCursorEntity {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Id
  @Column(name = "ambiente", nullable = false)
  private String ambiente;

  @Column(name = "ultimo_nsu", nullable = false)
  private long ultimoNsu;

  @Column(name = "ultima_busca_em")
  private Instant ultimaBuscaEm;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Chave implements Serializable {
    private UUID tenantId;
    private String ambiente;
  }
}
