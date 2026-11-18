package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LGPD dos dados de saude (plano, 6.4): exportacao ao titular, anonimizacao e retencao. Chamado pelos servicos de LGPD
 * que ja existem (ServicoExportacaoTitular, ServicoAnonimizacaoTitular, VinculosDeExclusao) e pela rotina de
 * retencao. As tabelas de historico so aceitam UPDATE/DELETE com app.lgpd_anonimizacao ligado nesta transacao.
 *
 * <p>Da prova de consentimento sobram so o tipo, o evento, a data e os hashes, sem dado pessoal: o salao continua
 * provando que tinha base legal naquele periodo.
 */
@Component
public class LgpdDeSaude {

  private final NamedParameterJdbcTemplate jdbc;

  public LgpdDeSaude(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Quantos registros de saude a cliente tem (bloqueiam a exclusao: o caminho e anonimizar). */
  public long registros(UUID t, UUID clientId) {
    Long n = jdbc.queryForObject("""
        select (select count(*) from anamneses where tenant_id = :t and client_id = :c)
             + (select count(*) from fichas_de_atendimento where tenant_id = :t and client_id = :c)
             + (select count(*) from assinaturas_do_cliente where tenant_id = :t and client_id = :c)""",
        Map.of("t", t, "c", clientId), Long.class);
    return n == null ? 0 : n;
  }

  /** O que entra na exportacao ao titular: anamneses (todas as versoes), fichas, fotos (lista) e documentos. */
  public Map<String, Object> exportar(UUID t, UUID clientId) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("anamneses", jdbc.queryForList("""
        select m.nome as modelo, v.numero as versao, v.respostas::text as respostas, v.completa, v.canal, v.created_at::text as em
          from versoes_de_anamnese v join anamneses a on a.id = v.anamnese_id join modelos_de_anamnese m on m.id = a.modelo_id
         where v.tenant_id = :t and v.client_id = :c order by v.created_at""", Map.of("t", t, "c", clientId)));
    r.put("alertas", jdbc.queryForList("select tipo, texto, ativo, created_at::text as em from alertas_do_cliente"
        + " where tenant_id = :t and client_id = :c order by created_at", Map.of("t", t, "c", clientId)));
    r.put("fichas", jdbc.queryForList("""
        select a.date::text as data, f.status, f.observacoes, f.opiniao_cliente, f.cuidados,
               (select string_agg(coalesce(s.servico_nome, '') || ': ' || coalesce(s.procedimento, ''), ' | ') from secoes_da_ficha s where s.ficha_id = f.id) as procedimentos,
               (select string_agg(i.gravidade || ': ' || i.descricao, ' | ') from intercorrencias_da_ficha i where i.ficha_id = f.id) as intercorrencias,
               (select string_agg(d.tipo || ': ' || d.texto, ' | ') from adendos_da_ficha d where d.ficha_id = f.id) as adendos
          from fichas_de_atendimento f join appointments a on a.id = f.appointment_id
         where f.tenant_id = :t and f.client_id = :c order by a.date""", Map.of("t", t, "c", clientId)));
    r.put("fotos", jdbc.queryForList("select etapa, descricao, liberada_marketing, created_at::text as em from fotos_da_ficha"
        + " where tenant_id = :t and client_id = :c and removida_em is null order by created_at", Map.of("t", t, "c", clientId)));
    r.put("documentosAssinados", jdbc.queryForList("""
        select tipo_documento, evento, assinante_nome, metodo, hash_conteudo, hash_evidencia, created_at::text as em
          from assinaturas_do_cliente where tenant_id = :t and client_id = :c order by created_at""", Map.of("t", t, "c", clientId)));
    return r;
  }

  /**
   * Apaga os dados de saude da cliente: respostas, alertas, fichas, fotos e imagens de assinatura. Das assinaturas
   * fica so a prova minima (tipo, evento, data, hashes). Os links sao revogados.
   */
  @Transactional
  public Map<String, Object> apagar(UUID t, UUID clientId) {
    jdbc.queryForObject("select set_config('app.lgpd_anonimizacao', 'on', true)", Map.of(), String.class);
    Map<String, Object> p = Map.of("t", t, "c", clientId);
    int versoes = jdbc.update("update versoes_de_anamnese set respostas = '{}'::jsonb, faltando = '[]'::jsonb"
        + " where tenant_id = :t and client_id = :c", p);
    int alertas = jdbc.update("delete from alertas_do_cliente where tenant_id = :t and client_id = :c", p);
    jdbc.update("""
        update secoes_da_ficha s set procedimento = null, tecnicas = null, produtos = '[]'::jsonb, observacoes = null
          from fichas_de_atendimento f where f.id = s.ficha_id and f.tenant_id = :t and f.client_id = :c""", p);
    jdbc.update("""
        delete from intercorrencias_da_ficha i using fichas_de_atendimento f
         where f.id = i.ficha_id and f.tenant_id = :t and f.client_id = :c""", p);
    jdbc.update("""
        update adendos_da_ficha d set texto = '[removido pela LGPD]', motivo = null
          from fichas_de_atendimento f where f.id = d.ficha_id and f.tenant_id = :t and f.client_id = :c""", p);
    int fichas = jdbc.update("update fichas_de_atendimento set observacoes = null, opiniao_cliente = null, cuidados = null,"
        + " justificativa_inicio = null where tenant_id = :t and client_id = :c", p);
    int fotos = jdbc.update("update fotos_da_ficha set arquivo_id = null, descricao = null, liberada_marketing = false,"
        + " removida_em = coalesce(removida_em, now()), motivo_remocao = 'LGPD' where tenant_id = :t and client_id = :c", p);
    jdbc.update("""
        update assinaturas_do_cliente set retrato = null, assinante_nome = null, assinante_documento = null, parentesco = null,
               arquivo_assinatura_id = null, ip = null, user_agent = null, motivo = null
         where tenant_id = :t and client_id = :c""", p);
    int arquivos = jdbc.update("delete from arquivos_protegidos where tenant_id = :t and client_id = :c", p);
    jdbc.update("update links_do_cliente set revogado_em = coalesce(revogado_em, now()) where tenant_id = :t and client_id = :c", p);
    return m("versoesDeAnamnese", versoes, "alertas", alertas, "fichas", fichas, "fotos", fotos, "arquivos", arquivos);
  }

  /** Clientes sem atendimento ha mais que a retencao do salao e com dados de saude ainda guardados. */
  List<Map<String, Object>> vencidosPelaRetencao(int limite) {
    return jdbc.queryForList("""
        select c.tenant_id, c.id as client_id from clients c
          join tenant_operational_settings s on s.tenant_id = c.tenant_id
         where (exists (select 1 from versoes_de_anamnese v where v.client_id = c.id and v.respostas <> '{}'::jsonb)
                or exists (select 1 from arquivos_protegidos a where a.client_id = c.id)
                or exists (select 1 from fichas_de_atendimento f where f.client_id = c.id
                            and (f.observacoes is not null or f.cuidados is not null or f.opiniao_cliente is not null)))
           and coalesce((select max(a.date) from appointments a where a.client_id = c.id), c.created_at::date)
               < (current_date - make_interval(years => s.saude_retencao_anos))
         limit :l""", Map.of("l", limite));
  }
}
