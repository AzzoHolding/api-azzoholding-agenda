package br.com.phdigitalcode.azzo.agenda.pro.service;

import static com.lowagie.text.Element.ALIGN_CENTER;
import static com.lowagie.text.Element.ALIGN_MIDDLE;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * DANFSe v2.0 — Documento Auxiliar da NFS-e no leiaute nacional da Nota Tecnica SE/CGNFS-e 008/2026,
 * versao 1.02 (ratificada pelo Ato Tecnico Conjunto RFB/CGIBS n. 1, de 31/07/2026). Desde
 * 03/08/2026 a API do ADN nao gera mais o DANFSe: cada emissor gera o seu.
 *
 * <p>Regras da NT seguidas aqui:
 *
 * <ul>
 *   <li>Os campos saem SO do XML da NFS-e autorizada (item 2.1): "nao poderao ser impressas
 *       informacoes que nao constem do arquivo da NFS-e". Campo sem informacao vira "-" (nota 12).
 *   <li>Uma pagina A4 retrato; margem ~0,30 cm; linhas de 0,5 pt e borda de 1 pt; cabecalho,
 *       titulos dos blocos, "Emitente da NFS-e" e "Valor Liquido + IBS/CBS" em cinza claro (5%).
 *   <li>Fontes: titulos de bloco 7 pt negrito maiusculo; titulos de campo 6 pt negrito (7 pt
 *       maiusculo no bloco de identificacao); conteudo 7 pt. A NT pede Arial e Microsoft Sans Serif;
 *       aqui vai Helvetica, de metricas equivalentes as da Arial, embutida no PDF.
 *   <li>Cabecalho: logomarca a esquerda, "DANFSe v2.0 / Documento Auxiliar da NFS-e" ao centro
 *       (9 pt negrito) e municipio, ambiente gerador e tipo de ambiente a direita. Em homologacao
 *       (tpAmb = 2), "NFS-e SEM VALIDADE JURIDICA" em vermelho, 9 pt negrito.
 *   <li>QR Code com https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave= + chave, minimo 1,52 cm,
 *       com o texto de autenticidade em 3 linhas de 6 pt.
 *   <li>Cancelada: marca d'agua "CANCELADA" na diagonal, 50 pt, cinza (item 2.5.1).
 *   <li>PIS/COFINS proprios so para competencia ate 2026 (nota 6); totais aproximados de tributos
 *       sempre nas informacoes complementares (nota 10).
 * </ul>
 *
 * <p>A logomarca e o arquivo oficial do gov.br ("Logo - NFS-e - Horizontal.png"), em
 * {@code resources/danfse}.
 */
@Component
public class DanfseRenderer {

  static final String URL_CONSULTA = "https://www.nfse.gov.br/ConsultaPublica/?tpc=1&chave=";
  private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
  private static final float CM = 72f / 2.54f;
  private static final Color CINZA_CLARO = new Color(242, 242, 242);
  private static final Color VERMELHO = new Color(237, 28, 36);
  private static final Color CINZA_MARCA = new Color(166, 166, 166);

  private static final Font TITULO_BLOCO = new Font(Font.HELVETICA, 7, Font.BOLD);
  private static final Font ROTULO = new Font(Font.HELVETICA, 6, Font.BOLD);
  private static final Font ROTULO_IDENT = new Font(Font.HELVETICA, 7, Font.BOLD);
  private static final Font CONTEUDO = new Font(Font.HELVETICA, 7, Font.NORMAL);
  private static final Font CABECALHO = new Font(Font.HELVETICA, 9, Font.BOLD);
  private static final Font SEM_VALIDADE = new Font(Font.HELVETICA, 9, Font.BOLD, VERMELHO);
  private static final Font MUNICIPIO = new Font(Font.HELVETICA, 8, Font.NORMAL);
  private static final Font PEQUENA = new Font(Font.HELVETICA, 6, Font.NORMAL);

  private static final Map<String, String> SITUACAO = Map.of(
      "100", "NFS-e Gerada", "102", "NFS-e de Decisão Judicial", "103", "NFS-e Avulsa", "107", "NFS-e MEI");
  private static final Map<String, String> EMITENTE = Map.of("1", "Prestador", "2", "Tomador", "3", "Intermediário");
  private static final Map<String, String> FINALIDADE = Map.of("0", "NFS-e regular");
  private static final Map<String, String> SIMPLES = Map.of(
      // Campo de 40 caracteres na NT: as descricoes do leiaute, encurtadas para caber.
      "1", "Não Optante", "2", "Optante - MEI", "3", "Optante - ME/EPP");
  private static final Map<String, String> APURACAO_SN = Map.of(
      "1", "Regime de apuração dos tributos federais e municipal pelo Simples Nacional",
      "2", "Regime de apuração dos tributos federais pelo SN e o ISSQN por fora do SN",
      "3", "Regime de apuração dos tributos federais e municipal por fora do SN");
  private static final Map<String, String> TRIBUTACAO_ISSQN = Map.of(
      "1", "Operação Tributável", "2", "Imunidade", "3", "Exportação de Serviço", "4", "Não Incidência");
  private static final Map<String, String> REGIME_ESPECIAL = Map.of(
      "0", "Nenhum", "1", "Ato Cooperado (Cooperativa)", "2", "Estimativa", "3", "Microempresa Municipal",
      "4", "Notário ou Registrador", "5", "Profissional Autônomo", "6", "Sociedade de Profissionais", "9", "Outros");
  private static final Map<String, String> RETENCAO_ISSQN = Map.of(
      "1", "Não Retido", "2", "Retido pelo Tomador", "3", "Retido pelo Intermediário");
  private static final Map<String, String> AMBIENTE_GERADOR = Map.of("1", "Prefeitura", "2", "Sistema Nacional da NFS-e");
  private static final Map<String, String> TIPO_AMBIENTE = Map.of("1", "Produção", "2", "Homologação");

  /** Gera o DANFSe a partir do XML da NFS-e autorizada. {@code cancelada}: marca d'agua "CANCELADA". */
  public byte[] render(String xmlNfse, boolean cancelada) {
    Xml nfse = Xml.ler(xmlNfse);
    Xml inf = nfse.filho("infNFSe");
    Xml dps = inf.filho("DPS").filho("infDPS");
    Xml emit = inf.filho("emit");
    Xml prest = dps.filho("prest");
    Xml toma = dps.filho("toma");
    Xml valoresNfse = inf.filho("valores");
    Xml valoresDps = dps.filho("valores");
    Xml tribMun = valoresDps.filho("trib").filho("tribMun");
    Xml tribFed = valoresDps.filho("trib").filho("tribFed");
    boolean homologacao = "2".equals(dps.texto("tpAmb"));
    String chave = chaveDoId(inf.atributo("Id"));

    Document documento = new Document(PageSize.A4, 0.30f * CM, 0.30f * CM, 0.30f * CM, 0.30f * CM);
    ByteArrayOutputStream saida = new ByteArrayOutputStream();
    try {
      PdfWriter escritor = PdfWriter.getInstance(documento, saida);
      escritor.setPageEvent(new Moldura(cancelada));
      documento.open();
      PdfContentByte canvas = escritor.getDirectContent();

      documento.add(cabecalho(inf, emit, homologacao));
      documento.add(identificacao(inf, dps, chave, canvas));
      documento.add(pessoa("PRESTADOR / FORNECEDOR", prest, emit, inf, true));
      documento.add(toma.existe()
          ? pessoa("TOMADOR / ADQUIRENTE", toma, null, inf, false)
          : faixa("TOMADOR/ADQUIRENTE DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e"));
      documento.add(faixa(destinatarioProprio(dps)
          ? "O DESTINATÁRIO É O PRÓPRIO TOMADOR/ADQUIRENTE DA OPERAÇÃO"
          : "DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e"));
      documento.add(dps.filho("interm").existe()
          ? pessoa("INTERMEDIÁRIO DA OPERAÇÃO", dps.filho("interm"), null, inf, false)
          : faixa("INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e"));
      documento.add(servico(inf, dps));
      documento.add(tributacaoMunicipal(inf, dps, tribMun, valoresNfse, valoresDps, prest));
      documento.add(tributacaoFederal(tribFed, dps));
      documento.add(ibsCbs(inf, dps));
      documento.add(valorTotal(valoresNfse, valoresDps, inf));
      documento.add(informacoesComplementares(inf, dps));
      documento.close();
      return saida.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao gerar o DANFSe da NFS-e.", ex);
    }
  }

  // ─── blocos ───────────────────────────────────────────────────────────────

  private PdfPTable cabecalho(Xml inf, Xml emit, boolean homologacao) {
    // NT 008, item 2.4.5: quadro da logomarca 5,11 cm (logo 4,00 x 0,85), descricao 10,21 cm e
    // municipio/ambiente 5,09 cm; altura minima de 1,16 cm.
    PdfPTable tabela = tabela(new float[] {5.11f, 10.21f, 5.09f});
    PdfPCell logo = new PdfPCell(logomarca());
    logo.setHorizontalAlignment(ALIGN_CENTER);
    logo.setVerticalAlignment(ALIGN_MIDDLE);
    logo.setMinimumHeight(1.16f * CM);
    tabela.addCell(sombreada(logo));

    Paragraph centro = new Paragraph();
    centro.setAlignment(ALIGN_CENTER);
    centro.add(new Phrase("DANFSe v2.0\n", CABECALHO));
    centro.add(new Phrase("Documento Auxiliar da NFS-e", CABECALHO));
    if (homologacao) centro.add(new Phrase("\nNFS-e SEM VALIDADE JURÍDICA", SEM_VALIDADE));
    PdfPCell meio = new PdfPCell();
    meio.addElement(centro);
    meio.setVerticalAlignment(ALIGN_MIDDLE);
    tabela.addCell(sombreada(meio));

    Paragraph direita = new Paragraph();
    direita.add(new Phrase("Município: " + traco(juntar(" / ", inf.texto("xLocEmi"), emit.filho("enderNac").texto("UF"))) + "\n", MUNICIPIO));
    direita.add(new Phrase("Ambiente gerador: " + descricao(AMBIENTE_GERADOR, inf.texto("ambGer")) + "\n", PEQUENA));
    direita.add(new Phrase("Tipo de ambiente: " + descricao(TIPO_AMBIENTE, inf.filho("DPS").filho("infDPS").texto("tpAmb")), PEQUENA));
    PdfPCell lado = new PdfPCell();
    lado.addElement(direita);
    tabela.addCell(sombreada(lado));
    return tabela;
  }

  private PdfPTable identificacao(Xml inf, Xml dps, String chave, PdfContentByte canvas) throws Exception {
    PdfPTable externa = tabela(new float[] {15.30f, 5.10f});
    externa.addCell(tituloBloco("DADOS DA NFS-e", 2));

    PdfPTable campos = new PdfPTable(new float[] {1, 1, 1});
    campos.setWidthPercentage(100);
    campos.addCell(campo("CHAVE DE ACESSO DA NFS-e", traco(chave), 3, ROTULO_IDENT, false));
    campos.addCell(campo("NÚMERO DA NFS-e", inf.texto("nNFSe"), 1, ROTULO_IDENT, false));
    campos.addCell(campo("COMPETÊNCIA DA NFS-e", data(dps.texto("dCompet")), 1, ROTULO_IDENT, false));
    campos.addCell(campo("DATA E HORA DA EMISSÃO DA NFS-e", dataHora(inf.texto("dhProc")), 1, ROTULO_IDENT, false));
    campos.addCell(campo("NÚMERO DA DPS", dps.texto("nDPS"), 1, ROTULO_IDENT, false));
    campos.addCell(campo("SÉRIE DA DPS", dps.texto("serie"), 1, ROTULO_IDENT, false));
    campos.addCell(campo("DATA E HORA DA EMISSÃO DA DPS", dataHora(dps.texto("dhEmi")), 1, ROTULO_IDENT, false));
    campos.addCell(campo("EMITENTE DA NFS-e", descricao(EMITENTE, dps.texto("tpEmit")), 1, ROTULO_IDENT, true));
    campos.addCell(campo("SITUAÇÃO DA NFS-e", descricao(SITUACAO, inf.texto("cStat")), 1, ROTULO_IDENT, false));
    campos.addCell(campo("FINALIDADE", descricao(FINALIDADE, dps.texto("finNFSe")), 1, ROTULO_IDENT, false));
    PdfPCell esquerda = new PdfPCell(campos);
    esquerda.setPadding(0);
    externa.addCell(esquerda);

    PdfPCell qr = new PdfPCell();
    qr.setHorizontalAlignment(ALIGN_CENTER);
    if (chave != null) {
      Image imagem = qrCode(URL_CONSULTA + chave, canvas);
      imagem.scaleAbsolute(1.60f * CM, 1.60f * CM); // minimo da NT: 1,52 cm
      imagem.setAlignment(Image.ALIGN_CENTER);
      qr.addElement(imagem);
    }
    Paragraph texto = new Paragraph(
        "A autenticidade desta NFS-e pode ser verificada pela leitura deste código QR ou pela consulta da chave"
            + " de acesso no portal nacional da NFS-e",
        PEQUENA);
    texto.setAlignment(ALIGN_CENTER);
    qr.addElement(texto);
    externa.addCell(qr);
    return externa;
  }

  /** Prestador, tomador ou intermediario. O prestador usa os dados do emitente da NFS-e (item 2.1.3). */
  private PdfPTable pessoa(String titulo, Xml pessoa, Xml emit, Xml inf, boolean prestador) {
    PdfPTable tabela = tabela(new float[] {1, 1, 1, 1});
    tabela.addCell(tituloBloco(titulo, 4));
    Xml fonte = emit != null && emit.existe() ? emit : pessoa;
    Xml endereco = prestador ? fonte.filho("enderNac") : pessoa.filho("end").filho("endNac");
    String municipio = prestador
        ? juntar(" / ", inf.texto("xLocEmi"), endereco.texto("UF"))
        : endereco.existe() ? endereco.texto("cMun") : null;
    tabela.addCell(campo("CNPJ / CPF / NIF", documentoFormatado(fonte), 1));
    tabela.addCell(campo("Indicador Municipal (Inscrição)", firstNonBlank(fonte.texto("IM"), pessoa.texto("IM")), 1));
    tabela.addCell(campo("Telefone", firstNonBlank(fonte.texto("fone"), pessoa.texto("fone")), 1));
    tabela.addCell(campo("Código IBGE / CEP", juntar(" / ", endereco.texto("cMun"), cep(endereco.texto("CEP"))), 1));
    tabela.addCell(campo("Nome / Nome Empresarial", reticencias(firstNonBlank(fonte.texto("xNome"), pessoa.texto("xNome")), 77), 2));
    tabela.addCell(campo("Município / Sigla UF", municipio, 2));
    tabela.addCell(campo("Endereço", reticencias(juntar(", ", endereco.texto("xLgr"), endereco.texto("nro"),
        endereco.texto("xCpl"), endereco.texto("xBairro")), 77), 2));
    tabela.addCell(campo("E-mail", firstNonBlank(fonte.texto("email"), pessoa.texto("email")), 2));
    if (prestador) {
      Xml regTrib = pessoa.filho("regTrib");
      tabela.addCell(campo("Simples Nacional na Data de Competência", reticencias(descricao(SIMPLES, regTrib.texto("opSimpNac")), 40), 2));
      tabela.addCell(campo("Regime de Apuração Tributária pelo SN",
          reticencias(descricao(APURACAO_SN, regTrib.texto("regApTribSN")), 80), 2));
    }
    return tabela;
  }

  private PdfPTable servico(Xml inf, Xml dps) {
    Xml serv = dps.filho("serv");
    Xml cServ = serv.filho("cServ");
    PdfPTable tabela = tabela(new float[] {1, 1, 1, 1});
    tabela.addCell(tituloBloco("SERVIÇO PRESTADO", 4));
    tabela.addCell(campo("Código de Tributação Nacional / Municipal",
        juntar(" / ", codigoNacional(cServ.texto("cTribNac")), cServ.texto("cTribMun")), 1));
    tabela.addCell(campo("Código da NBS", cServ.texto("cNBS"), 1));
    tabela.addCell(campo("Local da Prestação / Sigla UF / País",
        juntar(" / ", inf.texto("xLocPrestacao"), serv.filho("locPrest").texto("cPaisPrestacao") != null
            ? serv.filho("locPrest").texto("cPaisPrestacao") : "BR"), 2));
    String descricaoDoCodigo = firstNonBlank(inf.texto("xTribMun"), inf.texto("xTribNac"));
    PdfPCell descricaoCodigo = new PdfPCell(new Phrase(traco(reticencias(descricaoDoCodigo, 167)), CONTEUDO));
    descricaoCodigo.setColspan(4);
    tabela.addCell(descricaoCodigo);
    tabela.addCell(campo("Descrição do Serviço", reticencias(cServ.texto("xDescServ"), 1297), 4));
    return tabela;
  }

  private PdfPTable tributacaoMunicipal(Xml inf, Xml dps, Xml tribMun, Xml valoresNfse, Xml valoresDps, Xml prest) {
    PdfPTable tabela = tabela(new float[] {1, 1, 1, 1});
    tabela.addCell(tituloBloco("TRIBUTAÇÃO MUNICIPAL (ISSQN)", 4));
    tabela.addCell(campo("Tipo de Tributação do ISSQN", descricao(TRIBUTACAO_ISSQN, tribMun.texto("tribISSQN")), 1));
    tabela.addCell(campo("Município / Sigla UF / País da Incidência do ISSQN",
        juntar(" / ", inf.texto("xLocIncid"), firstNonBlank(tribMun.texto("cPaisResult"), "BR")), 1));
    tabela.addCell(campo("Regime Especial de Tributação do ISSQN",
        descricao(REGIME_ESPECIAL, prest.filho("regTrib").texto("regEspTrib")), 1));
    tabela.addCell(campo("Tipo de Imunidade do ISSQN", tribMun.texto("tpImunidade"), 1));
    // Nota 5: a linha sai quando nao ha dado em nenhum campo dela.
    String suspensao = tribMun.filho("exigSusp").texto("tpSusp");
    String processo = tribMun.filho("exigSusp").texto("nProcesso");
    String beneficio = valoresNfse.texto("tpBM");
    String calculoBm = valoresNfse.texto("vCalcBM");
    if (suspensao != null || processo != null || beneficio != null || calculoBm != null) {
      tabela.addCell(campo("Suspensão da Exigibilidade do ISSQN", suspensao, 1));
      tabela.addCell(campo("Número Processo Suspensão", processo, 1));
      tabela.addCell(campo("Benefício Municipal", beneficio, 1));
      tabela.addCell(campo("Cálculo do BM", reais(calculoBm), 1));
    }
    tabela.addCell(campo("Total Deduções/Reduções", reais(valoresNfse.texto("vCalcAjusteBCISSQN")), 1));
    tabela.addCell(campo("Desconto Incondicionado", reais(valoresDps.filho("vDescCondIncond").texto("vDescIncond")), 1));
    tabela.addCell(campo("BC ISSQN", reais(valoresNfse.texto("vBC")), 1));
    tabela.addCell(campo("Alíquota Aplicada", percentual(valoresNfse.texto("pAliqAplic")), 1));
    tabela.addCell(campo("Retenção do ISSQN", descricao(RETENCAO_ISSQN, tribMun.texto("tpRetISSQN")), 2));
    tabela.addCell(campo("ISSQN Apurado", reais(valoresNfse.texto("vISSQN")), 2));
    return tabela;
  }

  private PdfPTable tributacaoFederal(Xml tribFed, Xml dps) {
    PdfPTable tabela = tabela(new float[] {1, 1, 1, 1});
    tabela.addCell(tituloBloco("TRIBUTAÇÃO FEDERAL (EXCETO CBS)", 4));
    Xml piscofins = tribFed.filho("piscofins");
    boolean pisCofinsRetido = "1".equals(piscofins.texto("tpRetPisCofins"));
    String contribuicoes = pisCofinsRetido
        ? soma(tribFed.texto("vRetCSLL"), piscofins.texto("vPis"), piscofins.texto("vCofins"))
        : tribFed.texto("vRetCSLL");
    tabela.addCell(campo("IRRF", reais(tribFed.texto("vRetIRRF")), 1));
    tabela.addCell(campo("Contribuição Previdenciária - Retida", reais(tribFed.texto("vRetCP")), 1));
    tabela.addCell(campo("Contribuições Sociais - Retidas", reais(contribuicoes), 1));
    tabela.addCell(campo("Descrição Contrib. Sociais - Retidas", piscofins.texto("tpRetPisCofins"), 1));
    // Nota 6: PIS/COFINS proprios so para competencia ate o fim de 2026.
    LocalDate competencia = dataOuNulo(dps.texto("dCompet"));
    if (competencia == null || competencia.getYear() <= 2026) {
      tabela.addCell(campo("PIS - Débito Apuração Própria", reais(pisCofinsRetido ? "0.00" : piscofins.texto("vPis")), 2));
      tabela.addCell(campo("COFINS - Débito Apuração Própria", reais(pisCofinsRetido ? "0.00" : piscofins.texto("vCofins")), 2));
    }
    return tabela;
  }

  /** IBS/CBS: classificacao vem da DPS; aliquotas e valores, da NFS-e (calculados pela Receita). */
  private PdfPTable ibsCbs(Xml inf, Xml dps) {
    Xml dpsIbs = dps.filho("IBSCBS");
    Xml nfseIbs = inf.filho("IBSCBS");
    Xml trib = dpsIbs.filho("valores").filho("trib");
    PdfPTable tabela = tabela(new float[] {1, 1, 1, 1});
    tabela.addCell(tituloBloco("TRIBUTAÇÃO IBS / CBS", 4));
    tabela.addCell(campo("CST / cClassTrib", juntar(" / ", trib.texto("CST"), trib.texto("cClassTrib")), 1));
    tabela.addCell(campo("Indicador de Operação / Código IBGE Incidência / Município Incidência / Sigla UF",
        juntar(" / ", dpsIbs.texto("cIndOp"), nfseIbs.texto("cLocalidadeIncid"), nfseIbs.texto("xLocalidadeIncid")), 3));
    tabela.addCell(campo("Exclusões e Reduções da Base de Cálculo", reais(nfseIbs.busca("vCalcReeRepRes")), 1));
    tabela.addCell(campo("Base de Cálculo Após Exclusões e Reduções", reais(nfseIbs.busca("vBC")), 1));
    tabela.addCell(campo("Red. Alíquota IBS / Red. Alíquota CBS",
        juntar(" / ", percentual(nfseIbs.busca("pRedAliqUF")), percentual(nfseIbs.busca("pRedAliqMun")),
            percentual(nfseIbs.busca("pRedAliqCBS"))), 1));
    tabela.addCell(campo("Alíquota - IBS UF / IBS Mun",
        juntar(" / ", percentual(nfseIbs.busca("pIBSUF")), percentual(nfseIbs.busca("pIBSMun"))), 1));
    tabela.addCell(campo("Alíq. Efetiva Municipal - IBS", percentual(nfseIbs.busca("pAliqEfetMun")), 1));
    tabela.addCell(campo("Valor Apurado Municipal - IBS", reais(nfseIbs.busca("vIBSMun")), 1));
    tabela.addCell(campo("Alíq. Efetiva Estadual - IBS", percentual(nfseIbs.busca("pAliqEfetUF")), 1));
    tabela.addCell(campo("Valor Apurado Estadual - IBS", reais(nfseIbs.busca("vIBSUF")), 1));
    tabela.addCell(campo("Valor Total Apurado - IBS", reais(nfseIbs.busca("vIBSTot")), 1));
    tabela.addCell(campo("Alíquota - CBS", percentual(nfseIbs.busca("pCBS")), 1));
    tabela.addCell(campo("Alíquota Efetiva - CBS", percentual(nfseIbs.busca("pAliqEfetCBS")), 1));
    tabela.addCell(campo("Valor Total Apurado - CBS", reais(nfseIbs.busca("vCBS")), 1));
    return tabela;
  }

  private PdfPTable valorTotal(Xml valoresNfse, Xml valoresDps, Xml inf) {
    PdfPTable tabela = tabela(new float[] {1, 1, 1, 1});
    tabela.addCell(tituloBloco("VALOR TOTAL DA NFS-e", 4));
    Xml descontos = valoresDps.filho("vDescCondIncond");
    Xml nfseIbs = inf.filho("IBSCBS");
    String totalIbsCbs = nfseIbs.existe() ? soma(nfseIbs.busca("vIBSTot"), nfseIbs.busca("vCBS")) : null;
    tabela.addCell(campo("Valor da Operação / Serviço", reais(valoresDps.filho("vServPrest").texto("vServ")), 1));
    tabela.addCell(campo("Desconto Incondicionado", reais(descontos.texto("vDescIncond")), 1));
    tabela.addCell(campo("Desconto Condicionado", reais(descontos.texto("vDescCond")), 1));
    tabela.addCell(campo("Total das Retenções (ISSQN / Federais)", reais(valoresNfse.texto("vTotalRet")), 1));
    tabela.addCell(campo("Valor Líquido da NFS-e", reais(valoresNfse.texto("vLiq")), 1));
    tabela.addCell(campo("Total do IBS/CBS", reais(totalIbsCbs), 1));
    PdfPCell liquido = campo("Valor Líquido da NFS-e + IBS/CBS",
        reais(nfseIbs.existe() ? firstNonBlank(nfseIbs.busca("vTotNF"), soma(valoresNfse.texto("vLiq"), totalIbsCbs)) : valoresNfse.texto("vLiq")), 2);
    tabela.addCell(sombreada(liquido));
    return tabela;
  }

  private PdfPTable informacoesComplementares(Xml inf, Xml dps) {
    PdfPTable tabela = tabela(new float[] {1});
    tabela.addCell(tituloBloco("INFORMAÇÕES COMPLEMENTARES", 1));
    List<String> partes = new ArrayList<>();
    Xml infoCompl = dps.filho("serv").filho("infoCompl");
    adicionar(partes, "Inf. Cont.: ", infoCompl.texto("xInfComp"));
    adicionar(partes, "NFS-e Subst.: ", dps.filho("subst").texto("chSubstda"));
    adicionar(partes, "Doc. Ref.: ", infoCompl.texto("docRef"));
    adicionar(partes, "Cod. Obra: ", dps.filho("serv").filho("obra").texto("cObra"));
    adicionar(partes, "Cod. Evt.: ", dps.filho("serv").filho("atvEvento").texto("idAtvEvt"));
    adicionar(partes, "Doc. Tec.: ", infoCompl.texto("idDocTec"));
    adicionar(partes, "Núm. Ped.: ", infoCompl.texto("xPed"));
    adicionar(partes, "Inf. A. T. Mun.: ", inf.texto("xOutInf"));
    String texto = reticencias(String.join(" | ", partes), 1997);
    Paragraph conteudo = new Paragraph();
    if (texto != null && !texto.isBlank()) conteudo.add(new Phrase(texto + "\n", CONTEUDO));
    conteudo.add(new Phrase(totaisAproximados(dps.filho("valores").filho("trib").filho("totTrib")), CONTEUDO));
    PdfPCell celula = new PdfPCell();
    celula.addElement(conteudo);
    celula.setMinimumHeight(1.6f * CM);
    tabela.addCell(celula);
    return tabela;
  }

  /** Nota 10: sempre presente, em R$ ou %. */
  static String totaisAproximados(Xml totTrib) {
    String prefixo = "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: ";
    Xml valores = totTrib.filho("vTotTrib");
    Xml percentuais = totTrib.filho("pTotTrib");
    if (valores.existe()) {
      return prefixo + "Federais: " + reais(valores.texto("vTotTribFed")) + "; Estaduais: "
          + reais(valores.texto("vTotTribEst")) + "; Municipais: " + reais(valores.texto("vTotTribMun"));
    }
    if (percentuais.existe()) {
      return prefixo + "Federais: " + percentual(percentuais.texto("pTotTribFed")) + "; Estaduais: "
          + percentual(percentuais.texto("pTotTribEst")) + "; Municipais: " + percentual(percentuais.texto("pTotTribMun"));
    }
    if (totTrib.texto("pTotTribSN") != null) {
      return prefixo + percentual(totTrib.texto("pTotTribSN")) + " (Simples Nacional)";
    }
    return prefixo + "não informados pelo emitente";
  }

  // ─── celulas ──────────────────────────────────────────────────────────────

  private PdfPTable tabela(float[] larguras) {
    PdfPTable tabela = new PdfPTable(larguras);
    tabela.setWidthPercentage(100);
    tabela.setSplitLate(false);
    return tabela;
  }

  private PdfPCell tituloBloco(String titulo, int colunas) {
    PdfPCell celula = new PdfPCell(new Phrase(titulo, TITULO_BLOCO));
    celula.setColspan(colunas);
    celula.setBorderWidth(0.5f);
    celula.setPaddingTop(1f);
    celula.setPaddingBottom(2f);
    return sombreada(celula);
  }

  /** Um bloco que so diz que nao ha dados (item 2.3). */
  private PdfPTable faixa(String texto) {
    PdfPTable tabela = tabela(new float[] {1});
    PdfPCell celula = new PdfPCell(new Phrase(texto, TITULO_BLOCO));
    celula.setBorderWidth(0.5f);
    celula.setMinimumHeight(0.32f * CM);
    celula.setPaddingBottom(2f);
    tabela.addCell(sombreada(celula));
    return tabela;
  }

  private PdfPCell campo(String rotulo, String valor, int colunas) {
    return campo(rotulo, valor, colunas, ROTULO, false);
  }

  private PdfPCell campo(String rotulo, String valor, int colunas, Font fonteRotulo, boolean sombrear) {
    Paragraph conteudo = new Paragraph();
    conteudo.setLeading(8f);
    conteudo.add(new Phrase(rotulo + "\n", fonteRotulo));
    conteudo.add(new Phrase(traco(valor), CONTEUDO));
    PdfPCell celula = new PdfPCell();
    celula.addElement(conteudo);
    celula.setColspan(colunas);
    celula.setBorderWidth(0.5f);
    celula.setPaddingTop(0f);
    celula.setPaddingBottom(2f);
    return sombrear ? sombreada(celula) : celula;
  }

  private PdfPCell sombreada(PdfPCell celula) {
    celula.setBackgroundColor(CINZA_CLARO);
    return celula;
  }

  /** Logomarca oficial da NFS-e (gov.br), como pede o item 2.4.3 da NT 008. */
  private static Image logomarca() {
    try (var entrada = DanfseRenderer.class.getResourceAsStream("/danfse/logo-nfse-horizontal.png")) {
      if (entrada == null) throw new IllegalStateException("Logomarca da NFS-e ausente do classpath.");
      Image imagem = Image.getInstance(entrada.readAllBytes());
      imagem.scaleAbsolute(4.00f * CM, 4.00f * CM * imagem.getHeight() / imagem.getWidth());
      return imagem;
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Falha ao ler a logomarca da NFS-e.", ex);
    }
  }

  private Image qrCode(String conteudo, PdfContentByte canvas) throws Exception {
    BitMatrix matriz = new QRCodeWriter().encode(
        conteudo, BarcodeFormat.QR_CODE, 0, 0,
        Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
    int lado = matriz.getWidth();
    PdfTemplate modelo = canvas.createTemplate(lado, lado);
    modelo.setColorFill(Color.BLACK);
    for (int y = 0; y < lado; y++) {
      for (int x = 0; x < lado; x++) {
        if (matriz.get(x, y)) modelo.rectangle(x, lado - y - 1, 1, 1);
      }
    }
    modelo.fill();
    return Image.getInstance(modelo);
  }

  /** Borda de 1 pt na pagina e, se cancelada, a marca d'agua diagonal. */
  private static final class Moldura extends PdfPageEventHelper {
    private final boolean cancelada;

    Moldura(boolean cancelada) {
      this.cancelada = cancelada;
    }

    @Override
    public void onEndPage(PdfWriter escritor, Document documento) {
      PdfContentByte canvas = escritor.getDirectContent();
      Rectangle pagina = documento.getPageSize();
      float margem = 0.20f * CM;
      canvas.setLineWidth(1f);
      canvas.rectangle(margem, margem, pagina.getWidth() - 2 * margem, pagina.getHeight() - 2 * margem);
      canvas.stroke();
      if (cancelada) {
        try {
          BaseFont fonte = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
          canvas.saveState();
          // Semitransparente: a marca fica visivel sem esconder os dados da nota.
          com.lowagie.text.pdf.PdfGState transparencia = new com.lowagie.text.pdf.PdfGState();
          transparencia.setFillOpacity(0.35f);
          canvas.setGState(transparencia);
          canvas.beginText();
          canvas.setColorFill(CINZA_MARCA);
          canvas.setFontAndSize(fonte, 90);
          canvas.showTextAligned(ALIGN_CENTER, "CANCELADA", pagina.getWidth() / 2, pagina.getHeight() / 2, 45);
          canvas.endText();
          canvas.restoreState();
        } catch (Exception ex) {
          throw new IllegalStateException("Falha ao desenhar a marca d'agua do DANFSe.", ex);
        }
      }
    }
  }

  // ─── formatacao ───────────────────────────────────────────────────────────

  static String chaveDoId(String id) {
    if (id == null) return null;
    return id.startsWith("NFS") ? id.substring(3) : id;
  }

  private static String documentoFormatado(Xml pessoa) {
    String cnpj = pessoa.texto("CNPJ");
    if (cnpj != null && cnpj.length() == 14) {
      return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
          + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }
    String cpf = pessoa.texto("CPF");
    if (cpf != null && cpf.length() == 11) {
      return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }
    return firstNonBlank(pessoa.texto("NIF"), cnpj, cpf);
  }

  static String codigoNacional(String codigo) {
    if (codigo == null || codigo.length() != 6) return codigo;
    return codigo.substring(0, 2) + "." + codigo.substring(2, 4) + "." + codigo.substring(4);
  }

  private static String cep(String cep) {
    if (cep == null || cep.length() != 8) return cep;
    return cep.substring(0, 2) + "." + cep.substring(2, 5) + "-" + cep.substring(5);
  }

  static String reais(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      NumberFormat formato = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
      return formato.format(new BigDecimal(valor.trim())).replace(' ', ' ');
    } catch (NumberFormatException ex) {
      return valor;
    }
  }

  private static String percentual(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      return new BigDecimal(valor.trim()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
    } catch (NumberFormatException ex) {
      return valor;
    }
  }

  private static String soma(String... valores) {
    BigDecimal total = null;
    for (String valor : valores) {
      if (valor == null || valor.isBlank()) continue;
      try {
        total = (total == null ? BigDecimal.ZERO : total).add(new BigDecimal(valor.trim()));
      } catch (NumberFormatException ignorado) {
        // valor ilegivel nao entra na soma
      }
    }
    return total == null ? null : total.toPlainString();
  }

  private static String data(String valor) {
    LocalDate data = dataOuNulo(valor);
    return data == null ? valor : DATA.format(data);
  }

  private static LocalDate dataOuNulo(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      return LocalDate.parse(valor.trim().substring(0, 10));
    } catch (Exception ex) {
      return null;
    }
  }

  private static String dataHora(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      return DATA_HORA.format(OffsetDateTime.parse(valor.trim()).atZoneSameInstant(ZONA));
    } catch (Exception ex) {
      return valor;
    }
  }

  private static String descricao(Map<String, String> tabela, String codigo) {
    if (codigo == null) return null;
    return tabela.getOrDefault(codigo.trim(), codigo);
  }

  private static String reticencias(String valor, int limite) {
    if (valor == null) return null;
    // NT 008: o campo tem {@code limite} caracteres; acima disso, corta e termina com "...".
    return valor.length() <= limite ? valor : valor.substring(0, limite - 3) + "...";
  }

  private static String traco(String valor) {
    return valor == null || valor.isBlank() ? "-" : valor;
  }

  private static String juntar(String separador, String... partes) {
    List<String> preenchidas = new ArrayList<>();
    for (String parte : partes) if (parte != null && !parte.isBlank()) preenchidas.add(parte.trim());
    return preenchidas.isEmpty() ? null : String.join(separador, preenchidas);
  }

  private static void adicionar(List<String> partes, String prefixo, String valor) {
    if (valor != null && !valor.isBlank()) partes.add(prefixo + valor.trim());
  }

  private static String firstNonBlank(String... valores) {
    for (String valor : valores) if (valor != null && !valor.isBlank()) return valor.trim();
    return null;
  }

  private static boolean destinatarioProprio(Xml dps) {
    String indDest = dps.filho("IBSCBS").texto("indDest");
    return indDest == null || "0".equals(indDest);
  }

  // ─── leitura do XML sem namespace ─────────────────────────────────────────

  /** Navegacao simples pelo XML da NFS-e, pelo nome local das tags. Tag ausente vira um no vazio. */
  static final class Xml {
    private final Element elemento;

    private Xml(Element elemento) {
      this.elemento = elemento;
    }

    static Xml ler(String xml) {
      try {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return new Xml(fabrica.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement());
      } catch (Exception ex) {
        throw new IllegalArgumentException("XML da NFS-e ilegivel para o DANFSe.", ex);
      }
    }

    boolean existe() {
      return elemento != null;
    }

    Xml filho(String nome) {
      if (elemento == null) return this;
      for (Node no = elemento.getFirstChild(); no != null; no = no.getNextSibling()) {
        if (no instanceof Element filho && nome.equals(filho.getLocalName())) return new Xml(filho);
      }
      return new Xml(null);
    }

    String texto(String nome) {
      Xml filho = filho(nome);
      if (filho.elemento == null) return null;
      String texto = filho.elemento.getTextContent();
      return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Primeiro descendente com o nome, em qualquer nivel. */
    String busca(String nome) {
      if (elemento == null) return null;
      var nos = elemento.getElementsByTagNameNS("*", nome);
      if (nos.getLength() == 0) return null;
      String texto = nos.item(0).getTextContent();
      return texto == null || texto.isBlank() ? null : texto.trim();
    }

    String atributo(String nome) {
      if (elemento == null) return null;
      String valor = elemento.getAttribute(nome);
      return valor == null || valor.isBlank() ? null : valor;
    }
  }
}
