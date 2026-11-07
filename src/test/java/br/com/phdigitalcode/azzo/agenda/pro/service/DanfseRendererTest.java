package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** DANFSe v2.0 (NT 008/2026) gerado a partir de um XML de NFS-e no leiaute 1.01. */
class DanfseRendererTest {

  private static final String CHAVE = "33033022264351855000140000000000000126110328087087";

  private final DanfseRenderer renderer = new DanfseRenderer();

  @Test
  void homologacaoSaiEmUmaPaginaComOsBlocosEOAvisoDeSemValidade() throws Exception {
    String texto = texto(renderer.render(xml("2", "2026-11-06"), false), 1);

    assertThat(texto)
        .contains("DANFSe v2.0")
        .contains("Documento Auxiliar da NFS-e")
        .contains("NFS-e SEM VALIDADE JURÍDICA")
        .contains(CHAVE)
        .contains("DADOS DA NFS-e")
        .contains("PRESTADOR / FORNECEDOR")
        .contains("12.345.678/0001-95")
        .contains("SALAO TESTE LTDA")
        .contains("TOMADOR / ADQUIRENTE")
        .contains("O DESTINATÁRIO É O PRÓPRIO TOMADOR/ADQUIRENTE DA OPERAÇÃO")
        .contains("INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e")
        .contains("06.01.01 / 001")
        .contains("Corte de cabelo")
        .contains("Operação Tributável")
        .contains("Optante - ME/EPP")
        .contains("NFS-e Gerada")
        .contains("Prestador")
        .contains("PIS - Débito Apuração Própria")
        .contains("Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012")
        .contains("Homologação");
    assertThat(texto.replace(' ', ' ')).contains("R$ 100,00");
  }

  @Test
  void producaoNaoMostraOAvisoDeHomologacao() throws Exception {
    String texto = texto(renderer.render(xml("1", "2026-11-06"), false), 1);

    assertThat(texto).doesNotContain("SEM VALIDADE").contains("Produção");
  }

  @Test
  void canceladaGanhaAMarcaDagua() throws Exception {
    String texto = texto(renderer.render(xml("2", "2026-11-06"), true), 1);

    assertThat(texto).contains("CANCELADA");
  }

  @Test
  void pisCofinsPropriosSaemDepoisDe2026() throws Exception {
    String texto = texto(renderer.render(xml("2", "2027-01-15"), false), 1);

    assertThat(texto).doesNotContain("PIS - Débito Apuração Própria");
  }

  /** {@code -Ddanfse.amostra=<pasta>} grava os PDFs de exemplo para conferir o desenho a olho. */
  @Test
  void gravaAmostrasQuandoPedido() throws Exception {
    String pasta = System.getProperty("danfse.amostra");
    if (pasta == null) return;
    java.nio.file.Files.write(java.nio.file.Path.of(pasta, "danfse-homologacao.pdf"), renderer.render(xml("2", "2026-11-06"), false));
    java.nio.file.Files.write(java.nio.file.Path.of(pasta, "danfse-cancelada.pdf"), renderer.render(xml("2", "2026-11-06"), true));
  }

  @Test
  void formatacoes() {
    assertThat(DanfseRenderer.chaveDoId("NFS" + CHAVE)).isEqualTo(CHAVE);
    assertThat(DanfseRenderer.codigoNacional("060101")).isEqualTo("06.01.01");
    assertThat(DanfseRenderer.reais("1234.5").replace(' ', ' ')).isEqualTo("R$ 1.234,50");
    assertThat(DanfseRenderer.reais(null)).isNull();
  }

  private static String texto(byte[] pdf, int paginasEsperadas) throws Exception {
    PdfReader leitor = new PdfReader(pdf);
    assertThat(leitor.getNumberOfPages()).isEqualTo(paginasEsperadas);
    StringBuilder texto = new StringBuilder();
    PdfTextExtractor extrator = new PdfTextExtractor(leitor);
    for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) texto.append(extrator.getTextFromPage(pagina));
    leitor.close();
    return texto.toString();
  }

  private static String xml(String tpAmb, String competencia) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.01">
          <infNFSe Id="NFS%1$s">
            <xLocEmi>Niterói</xLocEmi>
            <xLocPrestacao>Niterói</xLocPrestacao>
            <nNFSe>1</nNFSe>
            <cLocIncid>3303302</cLocIncid>
            <xLocIncid>Niterói</xLocIncid>
            <xTribNac>Barbearia, cabeleireiros, manicuros, pedicuros e congêneres.</xTribNac>
            <verAplic>SefinNac_Pre_1.4.0</verAplic>
            <ambGer>2</ambGer>
            <tpEmis>1</tpEmis>
            <procEmi>1</procEmi>
            <cStat>100</cStat>
            <dhProc>2026-11-06T10:15:30-03:00</dhProc>
            <nDFSe>12345</nDFSe>
            <emit>
              <CNPJ>12345678000195</CNPJ>
              <xNome>SALAO TESTE LTDA</xNome>
              <enderNac>
                <xLgr>Rua da Praia</xLgr><nro>100</nro><xBairro>Icaraí</xBairro>
                <cMun>3303302</cMun><UF>RJ</UF><CEP>24220000</CEP>
              </enderNac>
              <fone>21999990000</fone>
              <email>salao@exemplo.com.br</email>
            </emit>
            <valores>
              <vBC>100.00</vBC>
              <pAliqAplic>5.00</pAliqAplic>
              <vISSQN>5.00</vISSQN>
              <vLiq>100.00</vLiq>
            </valores>
            <DPS versao="1.01">
              <infDPS Id="DPS330330221234567800019500001000000000000001">
                <tpAmb>%2$s</tpAmb>
                <dhEmi>2026-11-06T10:15:00-03:00</dhEmi>
                <verAplic>AZZO-1.0</verAplic>
                <serie>1</serie>
                <nDPS>1</nDPS>
                <dCompet>%3$s</dCompet>
                <tpEmit>1</tpEmit>
                <cLocEmi>3303302</cLocEmi>
                <finNFSe>0</finNFSe>
                <prest>
                  <CNPJ>12345678000195</CNPJ>
                  <regTrib><opSimpNac>3</opSimpNac><regApTribSN>1</regApTribSN><regEspTrib>0</regEspTrib></regTrib>
                </prest>
                <toma>
                  <CPF>11144477735</CPF>
                  <xNome>CLIENTE DE TESTE</xNome>
                </toma>
                <serv>
                  <locPrest><cLocPrestacao>3303302</cLocPrestacao></locPrest>
                  <cServ>
                    <cTribNac>060101</cTribNac>
                    <cTribMun>001</cTribMun>
                    <xDescServ>Corte de cabelo</xDescServ>
                  </cServ>
                </serv>
                <valores>
                  <vServPrest><vServ>100.00</vServ></vServPrest>
                  <trib>
                    <tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>1</tpRetISSQN></tribMun>
                    <totTrib><pTotTribSN>6.00</pTotTribSN></totTrib>
                  </trib>
                </valores>
              </infDPS>
            </DPS>
          </infNFSe>
        </NFSe>
        """.formatted(CHAVE, tpAmb, competencia).strip();
  }
}
