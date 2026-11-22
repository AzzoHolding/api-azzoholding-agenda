package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseDocumentoDaReceitaEntity;

/**
 * Le o XML de um documento distribuido pelo ADN e preenche os campos que a tela lista e a apuracao
 * soma. Sem rede e sem banco: so o XML (e o CNPJ do salao, para saber o papel dele na nota).
 */
final class LeitorDoDocumentoDaReceita {

  /** Grupo do evento de cancelamento: e101101 (pelo emitente) ou e105102 (por substituicao). */
  private static final Pattern EVENTO_DE_CANCELAMENTO = Pattern.compile("<(?:\\w+:)?e(?:1011|1051)\\d{2}[\\s>/]");

  private LeitorDoDocumentoDaReceita() {}

  static void preencher(NfseDocumentoDaReceitaEntity doc, String xml, String cnpjDoSalao) {
    if (xml == null || xml.isBlank()) return;
    if ("EVENTO".equals(doc.getTipoDocumento())) {
      preencherEvento(doc, xml);
      return;
    }
    if (!"NFSE".equals(doc.getTipoDocumento()) && !xml.contains("infNFSe")) return;
    DanfseRenderer.Xml nfse = DanfseRenderer.Xml.ler(xml);
    DanfseRenderer.Xml inf = nfse.filho("infNFSe");
    if (!inf.existe()) return;
    DanfseRenderer.Xml dps = inf.filho("DPS").filho("infDPS");
    DanfseRenderer.Xml emit = inf.filho("emit");
    DanfseRenderer.Xml prest = dps.filho("prest");
    DanfseRenderer.Xml toma = dps.filho("toma");
    DanfseRenderer.Xml interm = dps.filho("interm");

    String chave = DanfseRenderer.chaveDoId(inf.atributo("Id"));
    if (chave != null) doc.setChaveAcesso(chave);
    doc.setNumeroNfse(inf.texto("nNFSe"));
    doc.setCompetencia(data(dps.texto("dCompet")));
    doc.setEmitidaEm(instante(inf.texto("dhProc")));

    String prestador = documento(emit.existe() ? emit : prest);
    if (prestador == null) prestador = documento(prest);
    doc.setPrestadorDocumento(prestador);
    doc.setPrestadorNome(limite(primeiro(emit.texto("xNome"), prest.texto("xNome")), 255));
    doc.setTomadorDocumento(documento(toma));
    doc.setTomadorNome(limite(toma.texto("xNome"), 255));

    doc.setValorServicos(decimal(dps.filho("valores").filho("vServPrest").texto("vServ")));
    doc.setValorIss(decimal(inf.filho("valores").texto("vISSQN")));
    String retencao = dps.filho("valores").filho("trib").filho("tribMun").texto("tpRetISSQN");
    doc.setIssRetido("2".equals(retencao) || "3".equals(retencao));
    doc.setDescricao(limite(dps.filho("serv").filho("cServ").texto("xDescServ"), 500));
    doc.setPapel(papel(cnpjDoSalao, prestador, documento(toma), documento(interm)));
  }

  private static void preencherEvento(NfseDocumentoDaReceitaEntity doc, String xml) {
    DanfseRenderer.Xml evento = DanfseRenderer.Xml.ler(xml);
    String chave = evento.busca("chNFSe");
    doc.setChaveDoEvento(chave == null ? null : chave.replaceAll("\\D", ""));
    String tipo = doc.getTipoEvento() == null ? "" : doc.getTipoEvento();
    boolean cancelamento = tipo.toLowerCase().contains("cancel") || EVENTO_DE_CANCELAMENTO.matcher(xml).find();
    doc.setCancelada(cancelamento);
  }

  /** Quando e por que a nota foi cancelada, lido do evento. */
  /**
   * O que o evento de cancelamento diz. {@code quando}: quando foi pedido (dhEvento); {@code
   * processadoEm}: quando a Receita registrou (dhProc). {@code autor}: CNPJ/CPF de quem pediu.
   * {@code chaveSubstituta}: a nota que substituiu esta (so no cancelamento por substituicao).
   */
  record Cancelamento(
      String quando,
      String motivo,
      String justificativa,
      String codigoMotivo,
      String tipo,
      String processadoEm,
      String autor,
      String numeroDoEvento,
      String chaveSubstituta) {

    Cancelamento(String quando, String motivo, String justificativa) {
      this(quando, motivo, justificativa, null, null, null, null, null, null);
    }
  }

  /**
   * O evento traz {@code dhEvento} (ou {@code dhProc}), e no grupo {@code e101101} (cancelamento) ou
   * {@code e105102} (cancelamento por substituicao) o {@code cMotivo}, a descricao {@code xDesc} e a
   * justificativa {@code xMotivo}.
   */
  static Cancelamento cancelamento(String xml) {
    if (xml == null || xml.isBlank()) return null;
    DanfseRenderer.Xml evento = DanfseRenderer.Xml.ler(xml);
    Instant quando = instante(primeiro(evento.busca("dhEvento"), evento.busca("dhProc")));
    String codigo = evento.busca("cMotivo");
    boolean substituicao = xml.contains("e105102");
    String motivo = motivoDoCancelamento(codigo, substituicao, evento.busca("xDesc"));
    Instant processado = instante(evento.busca("dhProc"));
    String substituta = evento.busca("chSubstituta");
    return new Cancelamento(
        quando == null ? null : quando.toString(),
        motivo,
        evento.busca("xMotivo"),
        codigo,
        substituicao ? "Cancelamento por substituição" : "Cancelamento",
        processado == null ? null : processado.toString(),
        primeiro(evento.busca("CNPJAutor"), evento.busca("CPFAutor")),
        primeiro(evento.busca("nSeqEvento"), evento.busca("nPedRegEvento")),
        substituta == null ? null : substituta.replaceAll("\\D", ""));
  }

  /** Tabela do evento 101101 (leiaute nacional): 1, 2 e 9. Substituicao usa outra tabela: o texto do evento. */
  static String motivoDoCancelamento(String codigo, boolean substituicao, String descricaoDoEvento) {
    if (substituicao) return descricaoDoEvento != null ? descricaoDoEvento : "Cancelamento por substituição";
    if (codigo == null) return descricaoDoEvento;
    return switch (codigo.trim().replaceFirst("^0+(?=\\d)", "")) {
      case "1" -> "Erro na emissão";
      case "2" -> "Serviço não prestado";
      case "9" -> "Outros";
      default -> descricaoDoEvento != null ? descricaoDoEvento : "Código " + codigo.trim();
    };
  }

  /** O papel do salao: a consulta e pela raiz do CNPJ, entao a comparacao tambem. */
  static String papel(String cnpjDoSalao, String prestador, String tomador, String intermediario) {
    String raiz = raiz(cnpjDoSalao);
    if (raiz == null) return "OUTRO";
    if (raiz.equals(raiz(prestador))) return "PRESTADOR";
    if (raiz.equals(raiz(tomador))) return "TOMADOR";
    if (raiz.equals(raiz(intermediario))) return "INTERMEDIARIO";
    return "OUTRO";
  }

  private static String raiz(String documento) {
    if (documento == null) return null;
    String digitos = documento.replaceAll("\\D", "");
    if (digitos.length() == 14) return digitos.substring(0, 8);
    return digitos.isEmpty() ? null : digitos;
  }

  private static String documento(DanfseRenderer.Xml pessoa) {
    if (pessoa == null || !pessoa.existe()) return null;
    return primeiro(pessoa.texto("CNPJ"), pessoa.texto("CPF"), pessoa.texto("NIF"));
  }

  private static String primeiro(String... valores) {
    for (String valor : valores) if (valor != null && !valor.isBlank()) return valor.trim();
    return null;
  }

  private static LocalDate data(String valor) {
    try {
      return valor == null ? null : LocalDate.parse(valor.trim().substring(0, 10));
    } catch (Exception ex) {
      return null;
    }
  }

  private static Instant instante(String valor) {
    try {
      return valor == null ? null : OffsetDateTime.parse(valor.trim()).toInstant();
    } catch (Exception ex) {
      return null;
    }
  }

  private static BigDecimal decimal(String valor) {
    try {
      return valor == null ? null : new BigDecimal(valor.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private static String limite(String valor, int maximo) {
    if (valor == null) return null;
    return valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
