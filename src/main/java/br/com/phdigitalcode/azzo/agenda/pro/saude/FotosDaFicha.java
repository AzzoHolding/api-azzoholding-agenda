package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

/**
 * Fotos de antes, durante e depois (plano, 3.4). So com a autorizacao de fotos ativa; liberar para divulgacao so com
 * a autorizacao de marketing ativa. Saem por rota autenticada, com permissao e registro de acesso.
 */
@Service
public class FotosDaFicha {

  static final Set<String> ETAPAS = Set.of("ANTES", "DURANTE", "DEPOIS");
  static final int MAXIMO_POR_FICHA = 30;

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final FichaDeAtendimento fichas;
  private final AnamneseDaCliente anamnese;
  private final ArquivosProtegidos arquivos;

  public FotosDaFicha(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, FichaDeAtendimento fichas,
      AnamneseDaCliente anamnese, ArquivosProtegidos arquivos) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.fichas = fichas;
    this.anamnese = anamnese;
    this.arquivos = arquivos;
  }

  @Transactional
  public Map<String, Object> enviar(UUID appointmentId, byte[] bytes, String etapa, UUID secaoId, String descricao, String origem) {
    UUID t = acesso.tenant();
    Map<String, Object> ag = fichas.agendamento(t, appointmentId);
    if (!acesso.podeEscreverFicha(t, (UUID) ag.get("professional_id"))) {
      throw new ApiClientErrorException("Só a profissional do atendimento registra fotos.", 403);
    }
    UUID clientId = (UUID) ag.get("client_id");
    if (!ETAPAS.contains(etapa)) throw new IllegalArgumentException("Diga se a foto é de antes, durante ou depois.");
    if (!anamnese.autorizacaoAtiva(t, clientId, "AUTORIZACAO_FOTOS")) {
      throw new IllegalArgumentException("A cliente não autorizou o registro de fotos. Peça a autorização antes de fotografar.");
    }
    UUID fichaId = fichas.abrirInterno(t, ag, null);
    Map<String, Object> ficha = fichas.fichaPorId(t, fichaId);
    if ("ARQUIVADA".equals(ficha.get("status"))) throw new IllegalArgumentException("A ficha deste atendimento está arquivada.");
    if (fichas.contar("fotos_da_ficha", fichaId) >= MAXIMO_POR_FICHA) {
      throw new IllegalArgumentException("Esta ficha já tem " + MAXIMO_POR_FICHA + " fotos.");
    }
    if (secaoId != null) {
      Boolean ok = jdbc.queryForObject("select exists(select 1 from secoes_da_ficha where ficha_id = :f and id = :s)",
          Map.of("f", fichaId, "s", secaoId), Boolean.class);
      if (!Boolean.TRUE.equals(ok)) throw new IllegalArgumentException("Seção da ficha não encontrada.");
    }
    ArquivosProtegidos.Imagem img = ArquivosProtegidos.prepararFoto(bytes);
    UUID arquivo = arquivos.guardar(t, clientId, "FOTO", img);
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into fotos_da_ficha (id, tenant_id, ficha_id, secao_id, client_id, arquivo_id, etapa, descricao, origem, largura, altura, created_by)
        values (:id, :t, :f, :s, :c, :a, :e, :d, :o, :w, :h, :u)""",
        m("id", id, "t", t, "f", fichaId, "s", secaoId, "c", clientId, "a", arquivo, "e", etapa, "d", Saude.limpar(descricao, 200),
            "o", "CAMERA".equals(origem) ? "CAMERA" : "GALERIA", "w", img.largura(), "h", img.altura(), "u", acesso.usuarioId()));
    acesso.auditar(t, "HEALTH_PHOTO_ADD", clientId, m("foto", id, "ficha", fichaId, "etapa", etapa), false);
    return m("id", id, "fotos", daFicha(t, fichaId));
  }

  List<Map<String, Object>> daFicha(UUID t, UUID fichaId) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> f : jdbc.queryForList("""
        select f.id, f.secao_id, f.etapa, f.descricao, f.origem, f.largura, f.altura, f.liberada_marketing, f.created_at,
               u.name as autor
          from fotos_da_ficha f left join users u on u.id = f.created_by
         where f.tenant_id = :t and f.ficha_id = :f and f.removida_em is null order by f.created_at""", Map.of("t", t, "f", fichaId))) {
      Map<String, Object> x = new LinkedHashMap<>(f);
      x.put("created_at", Saude.instante(f.get("created_at")));
      r.add(x);
    }
    return r;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> doAtendimento(UUID appointmentId) {
    UUID t = acesso.tenant();
    Map<String, Object> ag = fichas.agendamento(t, appointmentId);
    if (!acesso.podeVerFotos(t, (UUID) ag.get("professional_id"))) throw new ApiClientErrorException("Você não tem acesso às fotos.", 403);
    Map<String, Object> ficha = fichas.fichaDoAgendamento(t, appointmentId);
    return ficha == null ? List.of() : daFicha(t, (UUID) ficha.get("id"));
  }

  /** Todas as fotos da cliente que a usuaria pode ver, com a data e os servicos do atendimento (evolucao). */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> daCliente(UUID clientId) {
    UUID t = acesso.tenant();
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> f : jdbc.queryForList("""
        select f.id, f.ficha_id, f.etapa, f.descricao, f.liberada_marketing, f.created_at, fi.professional_id,
               fi.appointment_id, a.date as data_do_atendimento, s.servico_nome
          from fotos_da_ficha f join fichas_de_atendimento fi on fi.id = f.ficha_id
          join appointments a on a.id = fi.appointment_id
          left join secoes_da_ficha s on s.id = f.secao_id
         where f.tenant_id = :t and f.client_id = :c and f.removida_em is null
         order by a.date desc, f.created_at""", Map.of("t", t, "c", clientId))) {
      if (!acesso.podeVerFotos(t, (UUID) f.get("professional_id"))) continue;
      Map<String, Object> x = new LinkedHashMap<>(f);
      x.remove("professional_id");
      x.put("created_at", Saude.instante(f.get("created_at")));
      x.put("data_do_atendimento", Saude.texto(f.get("data_do_atendimento")));
      r.add(x);
    }
    return r;
  }

  record Arquivo(byte[] bytes, String contentType) {}

  @Transactional(readOnly = true)
  public Arquivo arquivo(UUID fotoId) {
    UUID t = acesso.tenant();
    Map<String, Object> f = foto(t, fotoId);
    if (!acesso.podeVerFotos(t, (UUID) f.get("professional_id"))) {
      acesso.negar(t, "HEALTH_PHOTO_DENIED", (UUID) f.get("client_id"));
    }
    if (f.get("arquivo_id") == null) throw new IllegalArgumentException("Foto removida.");
    ArquivosProtegidos.Arquivo a = arquivos.ler(t, (UUID) f.get("arquivo_id"));
    acesso.registrarAcesso(t, "FOTO", (UUID) f.get("client_id"), fotoId);
    return new Arquivo(a.bytes(), a.contentType());
  }

  Map<String, Object> foto(UUID t, UUID fotoId) {
    List<Map<String, Object>> r = jdbc.queryForList("""
        select f.id, f.client_id, f.arquivo_id, f.ficha_id, f.liberada_marketing, f.removida_em, fi.professional_id
          from fotos_da_ficha f join fichas_de_atendimento fi on fi.id = f.ficha_id
         where f.tenant_id = :t and f.id = :id""", Map.of("t", t, "id", fotoId));
    if (r.isEmpty() || r.get(0).get("removida_em") != null) throw new IllegalArgumentException("Foto não encontrada.");
    return r.get(0);
  }

  @Transactional
  public Map<String, Object> marketing(UUID fotoId, boolean liberada) {
    UUID t = acesso.tenant();
    if (!acesso.tem(AcessoDeSaude.FOTOS_MARKETING)) throw new ApiClientErrorException("Você não pode liberar fotos para divulgação.", 403);
    Map<String, Object> f = foto(t, fotoId);
    UUID clientId = (UUID) f.get("client_id");
    if (liberada && !anamnese.autorizacaoAtiva(t, clientId, "AUTORIZACAO_MARKETING")) {
      throw new IllegalArgumentException("A cliente não autorizou o uso de imagem em divulgação.");
    }
    jdbc.update("update fotos_da_ficha set liberada_marketing = :l, liberada_em = case when :l then now() else liberada_em end,"
        + " liberada_por = case when :l then :u else liberada_por end where id = :id", m("l", liberada, "u", acesso.usuarioId(), "id", fotoId));
    acesso.auditar(t, liberada ? "HEALTH_PHOTO_MARKETING_ON" : "HEALTH_PHOTO_MARKETING_OFF", clientId, m("foto", fotoId), false);
    return m("id", fotoId, "liberadaMarketing", liberada);
  }

  /** So o dono remove, com motivo. O arquivo cifrado e apagado; o registro fica com o motivo. */
  @Transactional
  public void remover(UUID fotoId, String motivo) {
    UUID t = acesso.tenant();
    if (!acesso.dono()) throw new ApiClientErrorException("Só o dono remove fotos.", 403);
    String mot = Saude.limpar(motivo, 300);
    if (mot == null) throw new IllegalArgumentException("Diga o motivo da remoção.");
    Map<String, Object> f = foto(t, fotoId);
    jdbc.update("update fotos_da_ficha set removida_em = now(), removida_por = :u, motivo_remocao = :m, arquivo_id = null,"
        + " liberada_marketing = false where id = :id", m("u", acesso.usuarioId(), "m", mot, "id", fotoId));
    if (f.get("arquivo_id") != null) jdbc.update("delete from arquivos_protegidos where id = :a", Map.of("a", f.get("arquivo_id")));
    acesso.auditar(t, "HEALTH_PHOTO_REMOVE", (UUID) f.get("client_id"), m("foto", fotoId), false);
  }
}
