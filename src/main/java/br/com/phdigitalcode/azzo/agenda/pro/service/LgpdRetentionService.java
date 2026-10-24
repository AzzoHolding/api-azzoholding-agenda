package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationSendLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantSpecialClosureDateRepository;

/** Espelha {@code scheduler/LgpdRetentionService.java}. */
@Service
public class LgpdRetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(LgpdRetentionService.class);

  private final DataSource dataSource;
  private final TenantSpecialClosureDateRepository tenantSpecialClosureDateRepository;
  private final ReactivationSendLogRepository reactivationSendLogRepository;
  private final ProcessadorImportacaoClientesService processadorImportacaoClientes;

  public LgpdRetentionService(
      DataSource dataSource,
      TenantSpecialClosureDateRepository tenantSpecialClosureDateRepository,
      ReactivationSendLogRepository reactivationSendLogRepository,
      ProcessadorImportacaoClientesService processadorImportacaoClientes) {
    this.dataSource = dataSource;
    this.tenantSpecialClosureDateRepository = tenantSpecialClosureDateRepository;
    this.reactivationSendLogRepository = reactivationSendLogRepository;
    this.processadorImportacaoClientes = processadorImportacaoClientes;
  }

  public void purgarDadosExpirados() {
    int webhooks = purgar("DELETE FROM webhook_event_logs WHERE expires_at < NOW()");
    int whatsapp = purgar("DELETE FROM whatsapp_message_log WHERE expires_at < NOW()");
    LOG.info("LGPD purge concluido: {} webhook_event_logs, {} whatsapp_message_log removidos", webhooks, whatsapp);
    purgarLogsReativacao();
    purgarDadosSemPrazo();

    long fechamentos = purgarFechamentosAntigos();
    LOG.info("Purga LGPD: {} fechamentos especiais com data anterior a 2 anos removidos", fechamentos);
  }

  /**
   * Remove fechamentos especiais com data de closure superior a 2 anos.
   * LGPD: o campo reason não é logado — apenas o count de registros removidos.
   */
  @Transactional
  public long purgarFechamentosAntigos() {
    LocalDate limite = LocalDate.now().minusYears(2);
    long count = tenantSpecialClosureDateRepository.deleteByClosureDateBefore(limite);
    LOG.info("Purga LGPD: {} fechamentos especiais com data anterior a 2 anos removidos", count);
    return count;
  }

  @Transactional
  public void purgarLogsReativacao() {
    long deleted = reactivationSendLogRepository.deleteExpired(Instant.now());
    if (deleted > 0) {
      LOG.info("LGPD: {} registros de log de reativacao purgados", deleted);
    }
  }

  /**
   * O que nao tinha prazo nenhum ate 2026-10-13 (analise LGPD): notificacoes (telefone e texto com
   * o nome do cliente), e-mails de senha/confirmacao (e-mail, nome e link com token), linhas com
   * erro da importacao de clientes (o valor original da planilha) e as proprias planilhas.
   * Os prazos estao na Politica de Privacidade.
   */
  void purgarDadosSemPrazo() {
    int notificacoes = purgar("DELETE FROM notifications WHERE created_at < NOW() - INTERVAL '180 days'");
    int emails = purgar(
        "DELETE FROM email_jobs WHERE status IN ('PROCESSED', 'FAILED') AND created_at < NOW() - INTERVAL '30 days'");
    int errosDeImportacao = purgar(
        "DELETE FROM importacao_cliente_erro_linha WHERE created_at < NOW() - INTERVAL '30 days'");
    // INT-025: senha do certificado A1 em sessao que ja acabou (inclusive as que venceram sem ninguem
    // consultar, que continuam com status ACTIVE).
    purgar("UPDATE nfse_certificate_unlock_sessions SET password_enc = NULL "
        + "WHERE password_enc IS NOT NULL AND (status <> 'ACTIVE' OR expires_at <= NOW())");
    int planilhas = 0;
    try {
      planilhas = processadorImportacaoClientes.limparArquivosDeJobsFinalizados();
    } catch (RuntimeException e) {
      LOG.error("LGPD purge falhou ao limpar planilhas de importacao de clientes", e);
    }
    LOG.info(
        "LGPD purge: {} notifications, {} email_jobs, {} linhas de erro de importacao, {} planilhas removidas",
        notificacoes, emails, errosDeImportacao, planilhas);
  }

  public void logDadosExpirados() {
    String sql = """
        SELECT tabela, registros_expirados, mais_antigo
        FROM v_lgpd_expired_data
        WHERE registros_expirados > 0
        """;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        LOG.info(
            "LGPD auditoria: tabela={} expirados={} mais_antigo={}",
            rs.getString("tabela"),
            rs.getLong("registros_expirados"),
            rs.getObject("mais_antigo"));
      }
    } catch (Exception e) {
      LOG.error("LGPD auditoria falhou ao consultar v_lgpd_expired_data", e);
    }
  }

  private int purgar(String sql) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      int removidos = ps.executeUpdate();
      LOG.debug("LGPD purge: sql='{}' removidos={}", sql, removidos);
      return removidos;
    } catch (Exception e) {
      LOG.error("LGPD purge falhou: sql='{}'", sql, e);
      return 0;
    }
  }
}
