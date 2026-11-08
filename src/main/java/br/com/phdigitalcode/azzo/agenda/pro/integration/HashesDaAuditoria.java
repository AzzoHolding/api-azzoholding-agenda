package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;

/**
 * As contas da trilha de auditoria, num lugar so: quem grava ({@link AuditService}), quem sela
 * ({@code SeladorDaAuditoria}) e quem confere ({@code ConferenciaDaAuditoria}) usam exatamente as
 * mesmas funcoes. Sem banco, sem rede: so o evento.
 *
 * <p><b>v1</b> (eventos ate a V164): o hash cobria o conteudo do evento e o anterior, mas nao o ator,
 * o papel, o IP, o navegador, o erro nem a chave de idempotencia. Continua aqui so para conferir os
 * eventos antigos.
 *
 * <p><b>v2</b>: {@code event_hash} cobre TODAS as colunas do evento. Os JSON entram pelo digest de
 * cada um ({@code payload_digests}), para a redacao de LGPD poder trocar o JSON sem tirar do resto
 * do evento a prova de integridade. O elo fica no {@code chain_hash}, calculado pelo selador.
 * Cada campo entra com o tamanho na frente ({@code 5:valor}; nulo vira {@code -1:}): nenhuma
 * combinacao de valores produz a mesma entrada que outra.
 */
public final class HashesDaAuditoria {

  public static final int V1 = 1;
  public static final int V2 = 2;
  private static final String SEM_JSON = "-";

  private HashesDaAuditoria() {}

  /** O Postgres guarda microssegundos: o instante do hash tem a mesma precisao, ou nao confere. */
  public static Instant instanteDoBanco(Instant instante) {
    return instante == null ? null : instante.truncatedTo(ChronoUnit.MICROS);
  }

  /** O hash da v1, igual ao que o {@code AuditService} gravava antes da V164. */
  public static String hashV1(AuditEvent e) {
    return hashV1(e, e.getCreatedAt());
  }

  /** O hash v1 com outro instante: a conferencia testa os nanossegundos que o banco perdeu. */
  public static String hashV1(AuditEvent e, Instant criadoEm) {
    String payload = String.join(
        "|",
        safe(e.getTenantId()),
        safe(e.getModule()),
        safe(e.getAction()),
        safe(e.getEntityType()),
        safe(e.getEntityId()),
        safe(e.getStatus()),
        safe(e.getRequestId()),
        safe(e.getSourceChannel()),
        safe(e.getBeforeJson()),
        safe(e.getAfterJson()),
        safe(e.getMetadataJson()),
        safe(e.getChangedFieldsJson()),
        safe(e.getPrevEventHash()),
        safe(criadoEm));
    return sha256(payload);
  }

  /** Digest de cada JSON do evento, na ordem before, after, metadata. */
  public static String digestsDosJson(String before, String after, String metadata) {
    return digestDoJson(before) + "|" + digestDoJson(after) + "|" + digestDoJson(metadata);
  }

  private static String digestDoJson(String json) {
    return json == null ? SEM_JSON : sha256(json);
  }

  /** O {@code event_hash} da v2: todas as colunas do evento, com os JSON pelos digests guardados. */
  public static String hashDoConteudoV2(AuditEvent e) {
    return sha256(campos(
        "azzo-audit-event-v2",
        safe(e.getId()),
        safe(e.getTenantId()),
        nulavel(e.getActorUserId()),
        e.getActorRole(),
        e.getModule(),
        e.getAction(),
        e.getEntityType(),
        e.getEntityId(),
        e.getStatus(),
        e.getErrorCode(),
        e.getErrorMessage(),
        e.getRequestId(),
        e.getIdempotencyKey(),
        e.getSourceChannel(),
        e.getIpAddress(),
        e.getUserAgent(),
        String.valueOf(e.isHasChanges()),
        e.getChangedFieldsJson(),
        e.getPayloadDigests(),
        e.getCreatedAt() == null ? null : String.valueOf(microssegundos(e.getCreatedAt()))));
  }

  /** O elo: o salao, a posicao, o conteudo do evento e o elo anterior. */
  public static String hashDoElo(AuditEvent e) {
    return sha256(campos(
        "azzo-audit-chain-v2",
        safe(e.getTenantId()),
        e.getChainSeq() == null ? null : String.valueOf(e.getChainSeq()),
        e.getEventHash(),
        e.getPrevEventHash()));
  }

  private static long microssegundos(Instant instante) {
    return ChronoUnit.MICROS.between(Instant.EPOCH, instante);
  }

  private static String campos(String... valores) {
    StringBuilder sb = new StringBuilder();
    for (String valor : valores) {
      if (valor == null) {
        sb.append("-1:");
      } else {
        sb.append(valor.length()).append(':').append(valor);
      }
      sb.append(';');
    }
    return sb.toString();
  }

  private static String nulavel(Object valor) {
    return valor == null ? null : String.valueOf(valor);
  }

  private static String safe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  public static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar hash de auditoria", e);
    }
  }
}
