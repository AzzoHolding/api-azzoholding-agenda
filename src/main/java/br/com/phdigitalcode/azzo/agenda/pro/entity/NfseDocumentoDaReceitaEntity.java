package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Um documento que o Ambiente de Dados Nacional (ADN) distribuiu para o CNPJ do salao: uma NFS-e
 * (como prestador, tomador ou intermediario) ou um evento dela (cancelamento). Ver V163.
 */
@Entity
@Table(name = "nfse_documentos_da_receita")
@Getter
@Setter
public class NfseDocumentoDaReceitaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  /** HOMOLOGACAO ou PRODUCAO. */
  @Column(name = "ambiente", nullable = false)
  private String ambiente;

  @Column(name = "nsu", nullable = false)
  private Long nsu;

  @Column(name = "chave_acesso")
  private String chaveAcesso;

  /** NFSE, EVENTO ou o que mais o ADN mandar. */
  @Column(name = "tipo_documento", nullable = false)
  private String tipoDocumento;

  @Column(name = "tipo_evento")
  private String tipoEvento;

  /** PRESTADOR, TOMADOR, INTERMEDIARIO ou OUTRO — o papel do salao na nota. Nulo em evento. */
  @Column(name = "papel")
  private String papel;

  @Column(name = "numero_nfse")
  private String numeroNfse;

  @Column(name = "competencia")
  private LocalDate competencia;

  @Column(name = "emitida_em")
  private Instant emitidaEm;

  @Column(name = "valor_servicos")
  private BigDecimal valorServicos;

  @Column(name = "valor_iss")
  private BigDecimal valorIss;

  @Column(name = "iss_retido", nullable = false)
  private boolean issRetido;

  @Column(name = "prestador_documento")
  private String prestadorDocumento;

  @Column(name = "prestador_nome")
  private String prestadorNome;

  @Column(name = "tomador_documento")
  private String tomadorDocumento;

  @Column(name = "tomador_nome")
  private String tomadorNome;

  @Column(name = "descricao")
  private String descricao;

  @Column(name = "cancelada", nullable = false)
  private boolean cancelada;

  /** Em evento: a chave da NFS-e a que ele se refere. */
  @Column(name = "chave_do_evento")
  private String chaveDoEvento;

  @Column(name = "xml_enc")
  private byte[] xmlEnc;

  @Column(name = "recebido_em", nullable = false)
  private Instant recebidoEm;

  @PrePersist
  void antesDeGravar() {
    if (id == null) id = UUID.randomUUID();
    if (recebidoEm == null) recebidoEm = Instant.now();
  }
}
