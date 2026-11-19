package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

/**
 * Assinatura eletronica simples (Lei 14.063/2020) dos documentos da cliente (plano, 3.5): anamnese (uma versao),
 * ficha fechada, termo do procedimento, autorizacao de fotos e autorizacao de marketing. Guarda o retrato fiel do que
 * foi assinado e o hash dele, a evidencia (data, IP, navegador, metodo, quem estava presente) e o hash da evidencia.
 * Recusa e revogacao sao eventos novos; nada e alterado.
 */
@Service
public class AssinaturasDaCliente {

  static final Set<String> EVENTOS = Set.of("ASSINATURA", "RECUSA", "REVOGACAO");
  static final Set<String> TIPOS = Set.of("ANAMNESE", "FICHA", "TERMO_PROCEDIMENTO", "AUTORIZACAO_FOTOS", "AUTORIZACAO_MARKETING");
  static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(AnamneseDaCliente.ZONA_BR);

  private final NamedParameterJdbcTemplate jdbc;
  private final AcessoDeSaude acesso;
  private final AnamneseDaCliente anamnese;
  private final FichaDeAtendimento fichas;
  private final ModelosDeSaude modelos;
  private final ArquivosProtegidos arquivos;

  public AssinaturasDaCliente(NamedParameterJdbcTemplate jdbc, AcessoDeSaude acesso, AnamneseDaCliente anamnese,
      FichaDeAtendimento fichas, ModelosDeSaude modelos, ArquivosProtegidos arquivos) {
    this.jdbc = jdbc;
    this.acesso = acesso;
    this.anamnese = anamnese;
    this.fichas = fichas;
    this.modelos = modelos;
    this.arquivos = arquivos;
  }

  /** O que a cliente (ou o responsavel) manifestou. */
  public record Pedido(String evento, String tipoDocumento, UUID documentoId, UUID versaoAnamneseId, UUID appointmentId,
      String assinanteNome, String assinanteDocumento, Boolean responsavelLegal, String parentesco, String imagemPng,
      String motivo, Boolean aceite) {}

  /** De onde veio: salao (usuario presente) ou link pessoal. */
  record Origem(String metodo, String ip, String userAgent, UUID usuarioPresente, UUID linkId, UUID contaId) {}

  /** O documento a assinar: retrato (texto fiel) e o hash dele. */
  record Conteudo(String retrato, String hash, UUID modeloDocumentoId, UUID versaoDocumentoId, UUID versaoAnamneseId, UUID fichaId) {}

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** O hash da evidencia junta tudo o que prova a manifestacao. Mudar qualquer campo muda o hash. */
  static String hashDaEvidencia(Map<String, Object> evidencia) {
    return Saude.sha256(Saude.json(new java.util.TreeMap<>(evidencia)));
  }

  /** Texto legivel das respostas de uma versao da anamnese (o retrato do que a cliente confirmou). */
  static String retratoDaAnamnese(String nomeDoModelo, int numero, List<Map<String, Object>> perguntas, Map<String, Object> respostas) {
    StringBuilder sb = new StringBuilder();
    sb.append("Anamnese: ").append(nomeDoModelo).append(" (versão ").append(numero).append(")\n\n");
    for (Map<String, Object> p : perguntas) {
      if (!AnamneseDaCliente.visivel(p, respostas)) continue;
      Object r = respostas.get(String.valueOf(p.get("id")));
      String valor = r == null ? "—" : r instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList())
          : "SIM".equals(r) ? "Sim" : "NAO".equals(r) ? "Não" : r.toString();
      sb.append(p.get("texto")).append("\n").append("  ").append(valor).append("\n");
    }
    return sb.toString().trim();
  }

  // ─── Registro ──────────────────────────────────────────────────────────────

  /** No salao: a usuaria logada estava presente; a cliente desenhou a assinatura na tela. */
  @Transactional
  public Map<String, Object> registrarNoSalao(UUID clientId, Pedido p, String ip, String userAgent) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    if (("ANAMNESE".equals(p.tipoDocumento()) && !acesso.podeVerSaude(t, clientId))) acesso.negar(t, "HEALTH_SIGN_DENIED", clientId);
    String metodo = "ASSINATURA".equals(p.evento()) ? (p.imagemPng() == null || p.imagemPng().isBlank() ? "ACEITE_NO_SALAO" : "DESENHO_NO_SALAO")
        : "ACEITE_NO_SALAO";
    if ("ASSINATURA".equals(p.evento()) && "ACEITE_NO_SALAO".equals(metodo)) {
      throw new IllegalArgumentException("No salão, a cliente assina desenhando na tela.");
    }
    return registrar(t, clientId, p, new Origem(metodo, ip, userAgent, acesso.usuarioId(), null, null));
  }

  Map<String, Object> registrar(UUID t, UUID clientId, Pedido p, Origem o) {
    String evento = p.evento() == null ? "ASSINATURA" : p.evento();
    if (!EVENTOS.contains(evento)) throw new IllegalArgumentException("Evento inválido.");
    if (!TIPOS.contains(p.tipoDocumento())) throw new IllegalArgumentException("Documento inválido.");
    if ("REVOGACAO".equals(evento) && !p.tipoDocumento().startsWith("AUTORIZACAO_")) {
      throw new IllegalArgumentException("Só as autorizações de fotos e de marketing podem ser revogadas.");
    }
    if ("ASSINATURA".equals(evento) && ("LINK".equals(o.metodo()) || "AREA".equals(o.metodo())) && !Boolean.TRUE.equals(p.aceite())) {
      throw new IllegalArgumentException("Marque que leu e concorda para assinar.");
    }
    Conteudo c = conteudo(t, clientId, p);

    Map<String, Object> cli = jdbc.queryForMap("select name, birth_date from clients where tenant_id = :t and id = :c", Map.of("t", t, "c", clientId));
    LocalDate nasc = cli.get("birth_date") == null ? null : ((java.sql.Date) cli.get("birth_date")).toLocalDate();
    boolean menor = AnamneseDaCliente.menor(nasc, LocalDate.now(AnamneseDaCliente.ZONA_BR));
    String nome = Saude.limpar(p.assinanteNome(), 160);
    String documento = Saude.limpar(p.assinanteDocumento(), 20);
    String parentesco = Saude.limpar(p.parentesco(), 40);
    if (Boolean.TRUE.equals(p.responsavelLegal())) {
      if (nome == null || documento == null || parentesco == null) {
        throw new IllegalArgumentException("Responsável legal: informe nome, documento e parentesco.");
      }
    } else if (menor && "ASSINATURA".equals(evento)) {
      throw new IllegalArgumentException("A cliente é menor de idade: quem assina é o responsável legal.");
    }
    if (nome == null) nome = String.valueOf(cli.get("name"));

    UUID imagemId = null;
    String hashImagem = null;
    if ("DESENHO_NO_SALAO".equals(o.metodo()) || (p.imagemPng() != null && !p.imagemPng().isBlank())) {
      ArquivosProtegidos.Imagem img = ArquivosProtegidos.prepararAssinatura(decodificar(p.imagemPng()));
      imagemId = arquivos.guardar(t, clientId, "ASSINATURA", img);
      hashImagem = Saude.sha256(img.bytes());
    }
    Instant agora = Instant.now();
    Map<String, Object> evidencia = new LinkedHashMap<>();
    evidencia.put("salao", t.toString());
    evidencia.put("cliente", clientId.toString());
    evidencia.put("evento", evento);
    evidencia.put("documento", p.tipoDocumento());
    evidencia.put("versaoDocumento", String.valueOf(c.versaoDocumentoId()));
    evidencia.put("versaoAnamnese", String.valueOf(c.versaoAnamneseId()));
    evidencia.put("ficha", String.valueOf(c.fichaId()));
    evidencia.put("hashConteudo", c.hash());
    evidencia.put("assinante", nome);
    evidencia.put("documentoDoAssinante", String.valueOf(documento));
    evidencia.put("responsavelLegal", Boolean.TRUE.equals(p.responsavelLegal()));
    evidencia.put("metodo", o.metodo());
    evidencia.put("ip", String.valueOf(o.ip()));
    evidencia.put("navegador", String.valueOf(o.userAgent()));
    evidencia.put("usuarioPresente", String.valueOf(o.usuarioPresente()));
    evidencia.put("link", String.valueOf(o.linkId()));
    if (o.contaId() != null) evidencia.put("conta", o.contaId().toString());
    evidencia.put("hashImagem", String.valueOf(hashImagem));
    evidencia.put("em", agora.toString());
    String hashEvidencia = hashDaEvidencia(evidencia);

    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into assinaturas_do_cliente (id, tenant_id, client_id, evento, tipo_documento, modelo_documento_id,
            versao_documento_id, versao_anamnese_id, ficha_id, appointment_id, retrato, hash_conteudo, assinante_nome,
            assinante_documento, responsavel_legal, parentesco, metodo, arquivo_assinatura_id, ip, user_agent,
            usuario_presente, link_id, conta_id, motivo, hash_evidencia, created_at)
        values (:id, :t, :c, :e, :tipo, :md, :vd, :va, :f, :a, :r, :h, :n, :doc, :resp, :par, :met, :img, :ip, :ua,
                :u, :l, :conta, :mot, :he, :em)""",
        m("id", id, "t", t, "c", clientId, "e", evento, "tipo", p.tipoDocumento(), "md", c.modeloDocumentoId(),
            "vd", c.versaoDocumentoId(), "va", c.versaoAnamneseId(), "f", c.fichaId(), "a", p.appointmentId(),
            "r", c.retrato(), "h", c.hash(), "n", nome, "doc", documento, "resp", Boolean.TRUE.equals(p.responsavelLegal()), "par", parentesco,
            "met", o.metodo(), "img", imagemId, "ip", Saude.limpar(o.ip(), 60), "ua", Saude.limpar(o.userAgent(), 300),
            "u", o.usuarioPresente(), "l", o.linkId(), "conta", o.contaId(), "mot", Saude.limpar(p.motivo(), 300), "he", hashEvidencia,
            "em", java.sql.Timestamp.from(agora)));
    // Revogou o marketing: nenhuma foto continua liberada para divulgacao.
    if ("AUTORIZACAO_MARKETING".equals(p.tipoDocumento()) && !"ASSINATURA".equals(evento)) {
      jdbc.update("update fotos_da_ficha set liberada_marketing = false where tenant_id = :t and client_id = :c and liberada_marketing",
          Map.of("t", t, "c", clientId));
    }
    acesso.auditar(t, "HEALTH_SIGNATURE_" + evento, clientId, m("assinatura", id, "documento", p.tipoDocumento(),
        "hashConteudo", c.hash(), "hashEvidencia", hashEvidencia, "metodo", o.metodo()), false);
    return m("id", id, "evento", evento, "tipoDocumento", p.tipoDocumento(), "hashConteudo", c.hash(),
        "hashEvidencia", hashEvidencia, "criadaEm", agora.toString());
  }

  private static byte[] decodificar(String dataUrl) {
    if (dataUrl == null || dataUrl.isBlank()) throw new IllegalArgumentException("Faltou a assinatura desenhada.");
    String b64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
    try {
      return Base64.getDecoder().decode(b64.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Assinatura inválida.");
    }
  }

  /** Monta o retrato do documento exato que esta sendo assinado (ou recusado). */
  Conteudo conteudo(UUID t, UUID clientId, Pedido p) {
    switch (p.tipoDocumento()) {
      case "ANAMNESE" -> {
        if (p.versaoAnamneseId() == null) throw new IllegalArgumentException("Diga qual versão da anamnese a cliente confirma.");
        List<Map<String, Object>> r = jdbc.queryForList("""
            select v.numero, v.respostas, v.versao_do_modelo_id, m.nome from versoes_de_anamnese v
              join anamneses a on a.id = v.anamnese_id join modelos_de_anamnese m on m.id = a.modelo_id
             where v.tenant_id = :t and v.client_id = :c and v.id = :v""", Map.of("t", t, "c", clientId, "v", p.versaoAnamneseId()));
        if (r.isEmpty()) throw new IllegalArgumentException("Versão da anamnese não encontrada.");
        Map<String, Object> v = r.get(0);
        String retrato = retratoDaAnamnese(String.valueOf(v.get("nome")), (Integer) v.get("numero"),
            modelos.perguntasDaVersao(t, (UUID) v.get("versao_do_modelo_id")), Saude.mapa(v.get("respostas")));
        return new Conteudo(retrato, Saude.sha256(retrato), null, null, p.versaoAnamneseId(), null);
      }
      case "FICHA" -> {
        if (p.appointmentId() == null) throw new IllegalArgumentException("Diga de qual atendimento é a ficha.");
        Map<String, Object> ficha = fichas.fichaDoAgendamento(t, p.appointmentId());
        if (ficha == null || !clientId.equals(ficha.get("client_id"))) throw new IllegalArgumentException("Ficha não encontrada.");
        if (!"FECHADA".equals(ficha.get("status"))) throw new IllegalArgumentException("A cliente assina a ficha depois do atendimento concluído.");
        if (!acesso.podeVerFicha(t, (UUID) ficha.get("professional_id"))) {
          throw new ApiClientErrorException("Você não tem acesso a esta ficha.", 403);
        }
        String retrato = retratoDaFicha(t, ficha);
        return new Conteudo(retrato, Saude.sha256(retrato), null, null, null, (UUID) ficha.get("id"));
      }
      default -> {
        UUID modelo = p.documentoId();
        Map<String, Object> doc = modelo != null ? modelos.documentoDoSalao(t, modelo) : modelos.documentoAtivoDoTipo(t, p.tipoDocumento());
        if (doc == null || doc.get("versao_atual_id") == null) {
          throw new IllegalArgumentException("O salão ainda não cadastrou este termo. Cadastre em Configurações > Anamnese e termos.");
        }
        if (!p.tipoDocumento().equals(doc.get("tipo"))) throw new IllegalArgumentException("O termo não é do tipo pedido.");
        return new Conteudo(String.valueOf(doc.get("conteudo")), String.valueOf(doc.get("hash_conteudo")), (UUID) doc.get("id"),
            (UUID) doc.get("versao_atual_id"), null, null);
      }
    }
  }

  String retratoDaFicha(UUID t, Map<String, Object> ficha) {
    Map<String, Object> d = fichas.detalhe(t, ficha);
    StringBuilder sb = new StringBuilder("Ficha de atendimento\n\n");
    for (Object o : (List<?>) d.get("secoes")) {
      Map<?, ?> s = (Map<?, ?>) o;
      sb.append("Serviço: ").append(s.get("servico_nome")).append("\n");
      if (s.get("procedimento") != null) sb.append("  Procedimento: ").append(s.get("procedimento")).append("\n");
      if (s.get("tecnicas") != null) sb.append("  Técnicas: ").append(s.get("tecnicas")).append("\n");
      if (s.get("produtos") instanceof List<?> ps && !ps.isEmpty()) {
        sb.append("  Produtos: ").append(String.join(", ", ps.stream().map(x -> String.valueOf(((Map<?, ?>) x).get("nome"))).toList())).append("\n");
      }
      if (s.get("observacoes") != null) sb.append("  Observações: ").append(s.get("observacoes")).append("\n");
    }
    if (d.get("cuidados") != null) sb.append("\nCuidados e recomendações: ").append(d.get("cuidados")).append("\n");
    for (Object o : (List<?>) d.get("intercorrencias")) {
      Map<?, ?> i = (Map<?, ?>) o;
      sb.append("Intercorrência (").append(i.get("gravidade")).append("): ").append(i.get("descricao")).append("\n");
    }
    for (Object o : (List<?>) d.get("adendos")) {
      Map<?, ?> a = (Map<?, ?>) o;
      sb.append("Adendo (").append(a.get("tipo")).append(", ").append(Saude.instante(a.get("created_at"))).append("): ")
          .append(a.get("texto")).append("\n");
    }
    return sb.toString().trim();
  }

  // ─── Leitura ───────────────────────────────────────────────────────────────

  /** Historico de assinaturas, recusas e revogacoes. Conteudo de anamnese e ficha so para quem ve a saude. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> historico(UUID clientId) {
    UUID t = acesso.tenant();
    anamnese.exigirCliente(t, clientId);
    return historico(t, clientId);
  }

  List<Map<String, Object>> historico(UUID t, UUID clientId) {
    List<Map<String, Object>> r = new ArrayList<>();
    for (Map<String, Object> a : jdbc.queryForList("""
        select a.id, a.evento, a.tipo_documento, a.modelo_documento_id, d.nome as documento, vd.numero as versao_documento,
               a.versao_anamnese_id, a.ficha_id, a.appointment_id, a.assinante_nome, a.responsavel_legal, a.parentesco,
               a.metodo, a.motivo, a.hash_conteudo, a.hash_evidencia, a.created_at, a.arquivo_assinatura_id is not null as tem_imagem
          from assinaturas_do_cliente a
          left join modelos_de_documento d on d.id = a.modelo_documento_id
          left join versoes_de_documento vd on vd.id = a.versao_documento_id
         where a.tenant_id = :t and a.client_id = :c order by a.created_at desc""", Map.of("t", t, "c", clientId))) {
      Map<String, Object> x = new LinkedHashMap<>(a);
      x.put("created_at", Saude.instante(a.get("created_at")));
      r.add(x);
    }
    return r;
  }

  Map<String, Object> linha(UUID t, UUID clientId, UUID assinaturaId) {
    List<Map<String, Object>> r = jdbc.queryForList("""
        select a.*, d.nome as documento, vd.numero as versao_documento, c.name as cliente, tn.name as salao
          from assinaturas_do_cliente a
          join clients c on c.id = a.client_id join tenants tn on tn.id = a.tenant_id
          left join modelos_de_documento d on d.id = a.modelo_documento_id
          left join versoes_de_documento vd on vd.id = a.versao_documento_id
         where a.tenant_id = :t and a.client_id = :c and a.id = :a""", Map.of("t", t, "c", clientId, "a", assinaturaId));
    if (r.isEmpty()) throw new IllegalArgumentException("Documento assinado não encontrado.");
    return r.get(0);
  }

  void exigirVerConteudo(UUID t, UUID clientId, Map<String, Object> a) {
    String tipo = String.valueOf(a.get("tipo_documento"));
    if ("ANAMNESE".equals(tipo) || "FICHA".equals(tipo)) acesso.exigirSaudeVer(t, clientId);
  }

  @Transactional(readOnly = true)
  public byte[] pdf(UUID clientId, UUID assinaturaId) {
    UUID t = acesso.tenant();
    Map<String, Object> a = linha(t, clientId, assinaturaId);
    exigirVerConteudo(t, clientId, a);
    acesso.registrarAcesso(t, "DOCUMENTO_ASSINADO", clientId, assinaturaId);
    return pdf(t, a);
  }

  @Transactional(readOnly = true)
  public byte[] imagem(UUID clientId, UUID assinaturaId) {
    UUID t = acesso.tenant();
    Map<String, Object> a = linha(t, clientId, assinaturaId);
    exigirVerConteudo(t, clientId, a);
    if (a.get("arquivo_assinatura_id") == null) throw new IllegalArgumentException("Esta assinatura não tem imagem.");
    return arquivos.ler(t, (UUID) a.get("arquivo_assinatura_id")).bytes();
  }

  /** PDF do documento assinado: o retrato, a assinatura desenhada e a evidencia com os hashes. */
  byte[] pdf(UUID t, Map<String, Object> a) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
      PdfWriter.getInstance(doc, out);
      doc.open();
      Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
      Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10.5f);
      Font miudo = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
      String tipo = String.valueOf(a.get("tipo_documento"));
      String nome = a.get("documento") != null ? String.valueOf(a.get("documento")) : rotulo(tipo);
      doc.add(new Paragraph(String.valueOf(a.get("salao")), miudo));
      doc.add(new Paragraph(nome + (a.get("versao_documento") != null ? " (versão " + a.get("versao_documento") + ")" : ""), titulo));
      doc.add(new Paragraph("Cliente: " + a.get("cliente"), normal));
      doc.add(new Paragraph(" "));
      doc.add(new Paragraph(String.valueOf(a.get("retrato")), normal));
      doc.add(new Paragraph(" "));
      String evento = String.valueOf(a.get("evento"));
      String quando = DATA_HORA.format(((java.sql.Timestamp) a.get("created_at")).toInstant());
      doc.add(new Paragraph(switch (evento) {
        case "ASSINATURA" -> "Assinado por " + a.get("assinante_nome") + " em " + quando;
        case "RECUSA" -> "Recusado por " + a.get("assinante_nome") + " em " + quando;
        default -> "Revogado por " + a.get("assinante_nome") + " em " + quando;
      }, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f)));
      if (Boolean.TRUE.equals(a.get("responsavel_legal"))) {
        doc.add(new Paragraph("Responsável legal (" + a.get("parentesco") + "), documento " + a.get("assinante_documento"), normal));
      }
      if (a.get("arquivo_assinatura_id") != null) {
        Image img = Image.getInstance(arquivos.ler(t, (UUID) a.get("arquivo_assinatura_id")).bytes());
        img.scaleToFit(220, 90);
        doc.add(img);
      }
      doc.add(new Paragraph(" "));
      doc.add(new Paragraph("Evidência da manifestação", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
      doc.add(new Paragraph("Método: " + a.get("metodo") + " · IP: " + a.get("ip") + " · Navegador: " + a.get("user_agent"), miudo));
      doc.add(new Paragraph("Hash do conteúdo (SHA-256): " + a.get("hash_conteudo"), miudo));
      doc.add(new Paragraph("Hash da evidência (SHA-256): " + a.get("hash_evidencia"), miudo));
      doc.add(new Paragraph("Assinatura eletrônica simples (Lei 14.063/2020). Registro " + a.get("id") + ".", miudo));
      doc.close();
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar o PDF do documento.", e);
    }
  }

  static String rotulo(String tipo) {
    return switch (tipo) {
      case "ANAMNESE" -> "Anamnese";
      case "FICHA" -> "Ficha de atendimento";
      case "AUTORIZACAO_FOTOS" -> "Autorização de registro de fotos";
      case "AUTORIZACAO_MARKETING" -> "Autorização de uso de imagem em divulgação";
      default -> "Termo de consentimento do procedimento";
    };
  }
}
