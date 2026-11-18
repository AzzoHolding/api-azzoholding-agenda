package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.service.NotificationPublisher;

/**
 * Relatorios do modulo (plano, etapa 4: pendencias do dia, intercorrencias, autorizacoes de marketing) e as rotinas
 * diarias: aviso de ficha esquecida (rascunho de atendimento que ja passou) e retencao dos dados de saude.
 */
@Service
public class RelatoriosDeSaude {

  private static final Logger LOG = LoggerFactory.getLogger(RelatoriosDeSaude.class);
  static final String CANAL_AVISO = "HEALTH_RECORD";

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;
  private final LgpdDeSaude lgpd;
  private final NotificationPublisher avisos;

  public RelatoriosDeSaude(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese,
      LgpdDeSaude lgpd, NotificationPublisher avisos) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
    this.lgpd = lgpd;
    this.avisos = avisos;
  }

  /** Agendamentos do dia com pendencia (anamnese, termo, autorizacao). Profissional ve so os dela. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> pendenciasDoDia(LocalDate data) {
    UUID t = acesso.tenant();
    UUID prof = acesso.profissional() && !acesso.dono() ? acesso.profissionalDoUsuario(t) : null;
    if (acesso.profissional() && !acesso.dono() && prof == null) return List.of();
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> a : jdbc.queryForList("""
        select a.id, a.start_time, a.status, c.name as cliente, p.name as profissional
          from appointments a left join clients c on c.id = a.client_id left join professionals p on p.id = a.professional_id
         where a.tenant_id = :t and a.date = :d and a.status not in ('CANCELLED', 'NO_SHOW')
           and (cast(:p as uuid) is null or a.professional_id = :p)
         order by a.start_time""", m("t", t, "d", data, "p", prof))) {
      Map<String, Object> pend = anamnese.pendencias(t, (UUID) a.get("id"), false);
      if (((List<?>) pend.get("itens")).isEmpty()) continue;
      Map<String, Object> x = new LinkedHashMap<>(a);
      x.put("start_time", Saude.texto(a.get("start_time")));
      x.put("itens", pend.get("itens"));
      x.put("bloqueia", pend.get("bloqueia"));
      r.add(x);
    }
    return r;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> intercorrencias(LocalDate de, LocalDate ate) {
    UUID t = acesso.tenant();
    boolean todas = acesso.tem(AcessoDeSaude.FICHA_VER_OUTROS);
    UUID prof = todas ? null : acesso.profissionalDoUsuario(t);
    if (!todas && prof == null) throw new ApiClientErrorException("Você não tem acesso a este relatório.", 403);
    return jdbc.queryForList("""
        select x.* from (
          select a.date::text as data, c.name as cliente, p.name as profissional, i.gravidade, i.descricao, i.conduta,
                 f.appointment_id, 'NO_ATENDIMENTO' as quando, f.professional_id
            from intercorrencias_da_ficha i join fichas_de_atendimento f on f.id = i.ficha_id
            join appointments a on a.id = f.appointment_id join clients c on c.id = f.client_id
            left join professionals p on p.id = f.professional_id
           where f.tenant_id = :t and a.date between :de and :ate
          union all
          select a.date::text, c.name, p.name, d.gravidade, d.texto, null, f.appointment_id, 'DEPOIS', f.professional_id
            from adendos_da_ficha d join fichas_de_atendimento f on f.id = d.ficha_id
            join appointments a on a.id = f.appointment_id join clients c on c.id = f.client_id
            left join professionals p on p.id = f.professional_id
           where f.tenant_id = :t and d.tipo = 'INTERCORRENCIA' and a.date between :de and :ate
        ) x where (cast(:p as uuid) is null or x.professional_id = :p) order by x.data desc""",
        m("t", t, "de", de, "ate", ate, "p", prof));
  }

  /** Clientes com autorizacao de uso de imagem ativa e as fotos liberadas. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> marketing() {
    UUID t = acesso.tenant();
    if (!acesso.tem(AcessoDeSaude.FOTOS_MARKETING)) throw new ApiClientErrorException("Você não tem acesso a este relatório.", 403);
    return jdbc.queryForList("""
        select c.id as client_id, c.name as cliente, ult.created_at::text as autorizada_em,
               (select count(*) from fotos_da_ficha f where f.client_id = c.id and f.liberada_marketing and f.removida_em is null) as fotos_liberadas
          from clients c
          join lateral (select evento, created_at from assinaturas_do_cliente a where a.tenant_id = c.tenant_id and a.client_id = c.id
                         and a.tipo_documento = 'AUTORIZACAO_MARKETING' order by created_at desc limit 1) ult on ult.evento = 'ASSINATURA'
         where c.tenant_id = :t order by c.name""", Map.of("t", t));
  }

  // ─── Rotinas ───────────────────────────────────────────────────────────────

  /** Ficha ainda em rascunho de atendimento de antes de ontem: um aviso no sino, uma vez. */
  @Scheduled(cron = "${app.saude.ficha-esquecida.cron:0 10 8 * * *}", zone = "America/Sao_Paulo")
  public void avisarFichasEsquecidas() {
    try {
      for (Map<String, Object> f : jdbc.queryForList("""
          select f.id, f.tenant_id, f.appointment_id, f.professional_id, c.name as cliente, a.date::text as data
            from fichas_de_atendimento f join appointments a on a.id = f.appointment_id join clients c on c.id = f.client_id
           where f.status = 'RASCUNHO' and f.aviso_de_esquecida_em is null and a.date < current_date - 1
           limit 500""", Map.of())) {
        avisos.publish((UUID) f.get("tenant_id"), (UUID) f.get("appointment_id"), (UUID) f.get("professional_id"), CANAL_AVISO,
            "ficha:" + f.get("id"), "Ficha do atendimento de " + f.get("cliente") + " (" + f.get("data") + ") ainda está aberta.",
            StatusNotification.SENT, null, java.time.Instant.now(), 86_400L);
        jdbc.update("update fichas_de_atendimento set aviso_de_esquecida_em = now() where id = :f", Map.of("f", f.get("id")));
      }
    } catch (Exception e) {
      LOG.warn("Falha no aviso de fichas esquecidas.", e);
    }
  }

  /** Retencao: sem atendimento ha mais que o prazo do salao (padrao 5 anos), os dados de saude saem. */
  @Scheduled(cron = "${app.saude.retencao.cron:0 40 3 * * *}", zone = "America/Sao_Paulo")
  public void aplicarRetencao() {
    try {
      for (Map<String, Object> c : lgpd.vencidosPelaRetencao(200)) {
        UUID t = (UUID) c.get("tenant_id");
        UUID clientId = (UUID) c.get("client_id");
        Map<String, Object> r = lgpd.apagar(t, clientId);
        acesso.auditar(t, "HEALTH_RETENTION_PURGE", clientId, r, false);
      }
    } catch (Exception e) {
      LOG.warn("Falha na retenção dos dados de saúde.", e);
    }
  }
}
