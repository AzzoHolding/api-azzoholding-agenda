package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;

/**
 * As acoes fiscais na trilha de auditoria geral ({@code audit_events}, modulo FISCAL).
 *
 * <p>Ate 07/11/2026 emitir, cancelar, desbloquear o certificado, buscar notas na Receita e baixar
 * XML/DANFSe nao entravam na trilha: a emissao ficava so em {@code nfse_invoice_events}, sem
 * IP nem navegador, e as demais nao ficavam em lugar nenhum.
 *
 * <p>Falha ao registrar FALHA a acao (o {@link AuditService} relanca): download ou desbloqueio sem
 * rastro nao sai. Na emissao, o registro entra na mesma transacao do evento da nota.
 */
@Service
public class AuditoriaFiscal {

  public static final String NOTA = "NFSE_INVOICE";
  public static final String CERTIFICADO = "FISCAL_CERTIFICATE";
  public static final String NOTA_DA_RECEITA = "NFSE_RECEITA_DOCUMENT";

  private final AuditService auditService;

  public AuditoriaFiscal(AuditService auditService) {
    this.auditService = auditService;
  }

  public void registrar(UUID tenantId, String acao, String tipo, Object id, Map<String, ?> detalhes) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.FISCAL;
    command.action = acao;
    command.entityType = tipo;
    command.entityId = id == null ? null : String.valueOf(id);
    command.sourceChannel = AuditConstants.SourceChannel.API;
    command.metadata = semNulos(detalhes);
    auditService.recordSuccess(command);
  }

  /** Map.of recusa nulo; o detalhe que nao veio simplesmente nao entra. */
  public static Map<String, Object> detalhes(Object... chaveValor) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < chaveValor.length; i += 2) {
      if (chaveValor[i + 1] != null) m.put(String.valueOf(chaveValor[i]), chaveValor[i + 1]);
    }
    return m;
  }

  private static Map<String, ?> semNulos(Map<String, ?> detalhes) {
    if (detalhes == null || detalhes.isEmpty()) return null;
    Map<String, Object> m = new LinkedHashMap<>();
    detalhes.forEach((k, v) -> {
      if (v != null) m.put(k, v);
    });
    return m;
  }
}
