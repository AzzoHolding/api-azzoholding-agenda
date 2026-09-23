package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Um template de mensagem na WABA do salao.
 *
 * <p><b>Criar nao e aprovar.</b> A Meta analisa cada template, e ate virar {@code APPROVED} ele
 * nao entrega nada. Guardar o estado aqui e o que permite a tela dizer "aguardando aprovacao" em
 * vez de deixar o salao com mensagens que somem no caminho.
 */
@Entity
@Table(name = "whatsapp_templates")
@Getter
@Setter
public class WhatsAppTemplateEntity {

  /** Para que serve o template. Um por salao. */
  public static final String TESTE = "TESTE";
  public static final String CONFIRMACAO = "CONFIRMACAO";
  public static final String CANCELAMENTO = "CANCELAMENTO";
  public static final String LEMBRETE = "LEMBRETE";

  /** Vocabulario da Meta. So APPROVED envia. */
  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "finalidade", nullable = false, length = 30)
  private String finalidade;

  @Column(name = "nome", nullable = false, length = 120)
  private String nome;

  @Column(name = "idioma", nullable = false, length = 12)
  private String idioma = "pt_BR";

  @Column(name = "meta_template_id", length = 60)
  private String metaTemplateId;

  @Column(name = "status", nullable = false, length = 30)
  private String status = PENDING;

  @Column(name = "motivo_recusa")
  private String motivoRecusa;

  /**
   * As variaveis do corpo, em ordem, separadas por virgula (ex.: {@code cliente,servico,data}).
   *
   * <p>E o que liga {@code {{1}}} ao nome do cliente na hora do envio: sem a ordem guardada, o
   * servico iria no lugar do nome e a Meta aceitaria sem reclamar.
   */
  @Column(name = "variaveis")
  private String variaveis;

  @Column(name = "corpo")
  private String corpo;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant agora = Instant.now();
    if (criadoEm == null) criadoEm = agora;
    atualizadoEm = agora;
  }

  @PreUpdate
  void preUpdate() {
    atualizadoEm = Instant.now();
  }

  public boolean aprovado() {
    return APPROVED.equalsIgnoreCase(status);
  }
}
