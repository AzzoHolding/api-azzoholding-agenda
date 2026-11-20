package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linha do tempo da cliente (plano, secao 7): uma consulta sobre as fontes, sem copia de dados. Versoes de anamnese,
 * fichas (com recomendacoes e intercorrencias), notas antigas do atendimento, fotos e documentos assinados.
 * Cada item respeita a permissao: quem nao ve a saude recebe so o que foi feito e quando.
 */
@Service
public class LinhaDoTempoDaCliente {

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;

  public LinhaDoTempoDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> linhaDoTempo(UUID clientId, int limite) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    boolean saude = acesso.podeVerSaude(t, clientId);
    int lim = Math.max(10, Math.min(limite <= 0 ? 60 : limite, 200));
    List<Map<String, Object>> itens = new ArrayList<>();

    for (Map<String, Object> v : jdbc.queryForList("""
        select v.id, v.numero, v.completa, v.canal, v.created_at, m.nome, u.name as autor,
               exists(select 1 from assinaturas_do_cliente s where s.versao_anamnese_id = v.id and s.evento = 'ASSINATURA') as confirmada
          from versoes_de_anamnese v join anamneses a on a.id = v.anamnese_id join modelos_de_anamnese m on m.id = a.modelo_id
          left join users u on u.id = v.preenchida_por
         where v.tenant_id = :t and v.client_id = :c order by v.created_at desc limit :l""", Map.of("t", t, "c", clientId, "l", lim))) {
      itens.add(m("tipo", "ANAMNESE", "em", Saude.instante(v.get("created_at")), "id", v.get("id"),
          "titulo", "Anamnese " + v.get("nome") + " — versão " + v.get("numero"),
          "detalhe", (Boolean.TRUE.equals(v.get("completa")) ? "completa" : "incompleta")
              + (Boolean.TRUE.equals(v.get("confirmada")) ? ", confirmada pela cliente" : "")
              + ("LINK".equals(v.get("canal")) ? ", preenchida pela cliente no link" : v.get("autor") != null ? ", por " + v.get("autor") : "")));
    }

    for (Map<String, Object> f : jdbc.queryForList("""
        select f.id, f.appointment_id, f.status, f.professional_id, f.cuidados, f.created_at, a.date, a.start_time, p.name as profissional,
               (select string_agg(coalesce(s.servico_nome, 'Serviço'), ', ' order by s.ordem) from secoes_da_ficha s where s.ficha_id = f.id) as servicos,
               (select count(*) from intercorrencias_da_ficha i where i.ficha_id = f.id)
                 + (select count(*) from adendos_da_ficha d where d.ficha_id = f.id and d.tipo = 'INTERCORRENCIA') as intercorrencias,
               (select count(*) from fotos_da_ficha x where x.ficha_id = f.id and x.removida_em is null) as fotos
          from fichas_de_atendimento f join appointments a on a.id = f.appointment_id
          left join professionals p on p.id = f.professional_id
         where f.tenant_id = :t and f.client_id = :c order by a.date desc, a.start_time desc limit :l""", Map.of("t", t, "c", clientId, "l", lim))) {
      boolean ver = acesso.podeVerFicha(t, (UUID) f.get("professional_id"));
      Number inter = (Number) f.get("intercorrencias");
      Number fotos = (Number) f.get("fotos");
      itens.add(m("tipo", "FICHA", "em", f.get("date") + "T" + f.get("start_time"), "id", f.get("id"),
          "appointmentId", f.get("appointment_id"), "status", f.get("status"),
          "titulo", "Atendimento: " + (f.get("servicos") == null ? "serviço" : f.get("servicos")),
          "detalhe", "com " + (f.get("profissional") == null ? "profissional" : f.get("profissional"))
              + ("ARQUIVADA".equals(f.get("status")) ? " (arquivada: agendamento cancelado)" : ""),
          "recomendacoes", ver ? f.get("cuidados") : null,
          "intercorrencias", inter == null ? 0 : inter.intValue(), "fotos", fotos == null ? 0 : fotos.intValue(), "podeVer", ver));
    }

    // Notas antigas (antes da ficha). So para quem ve a ficha desse atendimento.
    for (Map<String, Object> n : jdbc.queryForList("""
        select n.id, n.appointment_id, n.service_execution_notes, n.client_feedback_notes, n.internal_followup_notes,
               n.created_at, a.professional_id
          from appointment_customer_notes n join appointments a on a.id = n.appointment_id
         where n.tenant_id = :t and n.client_id = :c
           and not exists (select 1 from fichas_de_atendimento f where f.appointment_id = n.appointment_id)
         order by n.created_at desc limit :l""", Map.of("t", t, "c", clientId, "l", lim))) {
      boolean ver = acesso.podeVerFicha(t, (UUID) n.get("professional_id"));
      itens.add(m("tipo", "NOTA_ANTIGA", "em", Saude.instante(n.get("created_at")), "id", n.get("id"),
          "appointmentId", n.get("appointment_id"), "titulo", "Registro antigo do atendimento",
          "detalhe", ver ? junta(n) : "Registro do atendimento (sem acesso ao conteúdo)", "podeVer", ver));
    }

    for (Map<String, Object> a : jdbc.queryForList("""
        select a.id, a.evento, a.tipo_documento, d.nome, a.assinante_nome, a.created_at
          from assinaturas_do_cliente a left join modelos_de_documento d on d.id = a.modelo_documento_id
         where a.tenant_id = :t and a.client_id = :c order by a.created_at desc limit :l""", Map.of("t", t, "c", clientId, "l", lim))) {
      String doc = a.get("nome") != null ? String.valueOf(a.get("nome")) : AssinaturasDaCliente.rotulo(String.valueOf(a.get("tipo_documento")));
      String verbo = switch (String.valueOf(a.get("evento"))) {
        case "ASSINATURA" -> "Assinado";
        case "RECUSA" -> "Recusado";
        default -> "Revogado";
      };
      itens.add(m("tipo", "DOCUMENTO", "em", Saude.instante(a.get("created_at")), "id", a.get("id"),
          "evento", a.get("evento"), "titulo", verbo + ": " + doc, "detalhe", "por " + a.get("assinante_nome")));
    }

    itens.sort(Comparator.comparing((Map<String, Object> x) -> String.valueOf(x.get("em"))).reversed());
    List<Map<String, Object>> cortados = itens.size() > lim ? itens.subList(0, lim) : itens;
    if (saude) acesso.registrarAcesso(t, "LINHA_DO_TEMPO", clientId, null);
    return m("itens", cortados, "podeVerSaude", saude);
  }

  private static String junta(Map<String, Object> n) {
    List<String> partes = new ArrayList<>();
    if (n.get("service_execution_notes") != null) partes.add("Como foi: " + n.get("service_execution_notes"));
    if (n.get("client_feedback_notes") != null) partes.add("O que a cliente achou: " + n.get("client_feedback_notes"));
    if (n.get("internal_followup_notes") != null) partes.add("Para a próxima vez: " + n.get("internal_followup_notes"));
    return String.join(" · ", partes);
  }
}
