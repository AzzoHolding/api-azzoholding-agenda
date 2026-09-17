package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusFechamentoCaixa;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FechamentoCaixaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;

/**
 * As travas do dinheiro que ja foi conferido, e o registro de quem tentou passar por elas.
 *
 * <p>Nasceu de uma fraude que o sistema permitia (analise de 2026-09-16): a cliente paga R$ 200 em
 * dinheiro, a comanda lanca a receita de hoje, alguem EDITA esse lancamento para 2031 (ou o exclui),
 * o esperado em dinheiro de hoje cai R$ 200, o dinheiro vai para o bolso — e o caixa bate, sem
 * diferenca nenhuma a justificar. O caixa so confere o que os lancamentos dizem; mexer no
 * lancamento e mexer no que o caixa confere.
 *
 * <p>As regras:
 *
 * <ul>
 *   <li><b>dia com caixa FECHADO e dia travado</b>: ninguem cria, edita, exclui nem estorna
 *       dinheiro com essa data — nem o dono. O que foi contado e assinado nao muda depois;
 *       correcao entra como lancamento de HOJE, e aparece no caixa de hoje;
 *   <li><b>lancamento manual com data fora de hoje e so do dono</b>, e nunca mais de um ano a
 *       frente para ninguem;
 *   <li><b>toda tentativa barrada vira evento {@code DENIED}</b> na trilha, com o alvo e o que se
 *       tentou fazer. Tentativa bloqueada e o sinal mais valioso de uma auditoria — quem testa a
 *       porta uma vez costuma voltar.
 * </ul>
 *
 * <p>O evento de tentativa e gravado numa transacao PROPRIA ({@code recordDeniedIsolated}): a
 * operacao barrada faz rollback, e a prova de que alguem tentou nao pode ir junto.
 */
@Component
public class TravaFinanceira {

  static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter DIA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  static final int DIAS_MAXIMOS_A_FRENTE = 365;

  private final FechamentoCaixaRepository fechamentoCaixaRepository;
  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;

  public TravaFinanceira(
      FechamentoCaixaRepository fechamentoCaixaRepository,
      AuthenticatedUser authenticatedUser,
      AuditService auditService) {
    this.fechamentoCaixaRepository = fechamentoCaixaRepository;
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
  }

  /**
   * O dia desse instante ainda aceita mexer em dinheiro?
   *
   * <p>Recusa quando o caixa daquele dia esta FECHADO. Sem caixa, ou com caixa ABERTO, passa: o
   * salao que nao usa o fechamento de caixa nao fica travado por uma regra que nao adotou.
   */
  public void exigirDiaAberto(
      UUID tenantId,
      Instant quando,
      String acao,
      String tipoDeEntidade,
      String idDaEntidade,
      Object tentativa) {
    if (tenantId == null) return;
    LocalDate dia = (quando != null ? quando : Instant.now()).atZone(ZONA_BR).toLocalDate();
    boolean fechado =
        fechamentoCaixaRepository
            .findByTenantIdAndBusinessDate(tenantId, dia)
            .map(caixa -> caixa.getStatus() == StatusFechamentoCaixa.CLOSED)
            .orElse(false);
    if (!fechado) return;

    String motivo =
        "O caixa de "
            + DIA_BR.format(dia)
            + " ja foi fechado: o dinheiro desse dia nao pode mais mudar. Registre a correcao como"
            + " um lancamento de hoje.";
    registrarTentativaBloqueada(tenantId, acao, tipoDeEntidade, idDaEntidade, tentativa, motivo);
    throw new IllegalArgumentException(motivo);
  }

  /**
   * A data de um lancamento MANUAL e aceitavel?
   *
   * <p>Mais de um ano a frente nao e para ninguem: nenhum salao lanca despesa de 2031 hoje, e era
   * exatamente o destino de quem tirava dinheiro do dia. Fora do dia de hoje (passado ou futuro)
   * so o dono — a equipe lanca o que aconteceu hoje.
   */
  public void exigirDataDeLancamentoManual(
      UUID tenantId, Instant data, String acao, String idDaEntidade, Object tentativa) {
    if (data == null) return;
    LocalDate hoje = LocalDate.now(ZONA_BR);
    LocalDate dia = data.atZone(ZONA_BR).toLocalDate();

    if (ChronoUnit.DAYS.between(hoje, dia) > DIAS_MAXIMOS_A_FRENTE) {
      String motivo =
          "Data do lancamento muito distante ("
              + DIA_BR.format(dia)
              + "). O limite e de um ano a partir de hoje.";
      registrarTentativaBloqueada(tenantId, acao, "TRANSACTION", idDaEntidade, tentativa, motivo);
      throw new IllegalArgumentException(motivo);
    }

    if (!dia.equals(hoje) && !authenticatedUser.temRole("OWNER")) {
      String motivo =
          "So o dono lanca com data diferente de hoje ("
              + DIA_BR.format(dia)
              + "). A equipe registra o que aconteceu hoje.";
      registrarTentativaBloqueada(tenantId, acao, "TRANSACTION", idDaEntidade, tentativa, motivo);
      throw new IllegalArgumentException(motivo);
    }
  }

  /**
   * Grava a tentativa barrada como {@code DENIED}, numa transacao propria. Nunca lanca: a trilha
   * nao pode ser o motivo de a resposta mudar.
   */
  public void registrarTentativaBloqueada(
      UUID tenantId,
      String acao,
      String tipoDeEntidade,
      String idDaEntidade,
      Object tentativa,
      String motivo) {
    if (tenantId == null) return;
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = acao;
      command.entityType = tipoDeEntidade;
      command.entityId = idDaEntidade;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.errorCode = "BLOQUEADO";
      command.errorMessage = motivo;
      command.after = tentativa;
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("bloqueio", true);
      command.metadata = metadata;
      auditService.recordDeniedIsolated(command);
    } catch (Exception ignored) {
      // A trilha nunca decide a resposta.
    }
  }
}
