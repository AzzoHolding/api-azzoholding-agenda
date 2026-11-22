package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseDocumentoDaReceitaEntity;

/** Distribuicao do ADN: leitura do lote (JSON) e do XML de cada documento. */
class NotasDaReceitaTest {

  private static final String CHAVE = "33033022264351855000140000000000000526110000000001";
  private static final String CNPJ_DO_SALAO = "64351855000140";

  @Test
  void loteComNotaEEventoVemDescompactado() throws Exception {
    String corpo = """
        {"StatusProcessamento":"DOCUMENTOS_LOCALIZADOS","LoteDFe":[
          {"NSU":11,"ChaveAcesso":"%s","TipoDocumento":"NFSE","ArquivoXml":"%s"},
          {"NSU":12,"ChaveAcesso":"%s","TipoDocumento":"EVENTO","TipoEvento":"CANCELAMENTO","ArquivoXml":"%s"}]}
        """.formatted(CHAVE, gzipBase64(nfse(CNPJ_DO_SALAO, "11144477735")), CHAVE, gzipBase64(evento()));

    ClienteDaDistribuicaoAdn.Lote lote = ClienteDaDistribuicaoAdn.lerLote(200, corpo);

    assertThat(lote.rejeicao()).isNull();
    assertThat(lote.fim()).isFalse();
    assertThat(lote.documentos()).hasSize(2);
    assertThat(lote.documentos().get(0).nsu()).isEqualTo(11);
    assertThat(lote.documentos().get(0).xml()).contains("<nNFSe>5</nNFSe>");
    assertThat(lote.documentos().get(1).tipoDocumento()).isEqualTo("EVENTO");
  }

  @Test
  void fimDaFilaERejeicao() {
    assertThat(ClienteDaDistribuicaoAdn.lerLote(404, "{\"StatusProcessamento\":\"NENHUM_DOCUMENTO_LOCALIZADO\"}").fim())
        .isTrue();
    ClienteDaDistribuicaoAdn.Lote rejeitado = ClienteDaDistribuicaoAdn.lerLote(
        400,
        "{\"StatusProcessamento\":\"REJEICAO\",\"Erros\":[{\"Codigo\":\"E2245\",\"Descricao\":\"Não há procuração\"}]}");
    assertThat(rejeitado.rejeicao()).isEqualTo("E2245 – Não há procuração");
    assertThat(ClienteDaDistribuicaoAdn.lerLote(429, "").rejeicao()).isEqualTo("LIMITE");
  }

  @Test
  void notaEmitidaPeloSalaoTemPapelDePrestadorEValores() throws Exception {
    NfseDocumentoDaReceitaEntity doc = new NfseDocumentoDaReceitaEntity();
    doc.setTipoDocumento("NFSE");

    LeitorDoDocumentoDaReceita.preencher(doc, nfse(CNPJ_DO_SALAO, "11144477735"), CNPJ_DO_SALAO);

    assertThat(doc.getChaveAcesso()).isEqualTo(CHAVE);
    assertThat(doc.getNumeroNfse()).isEqualTo("5");
    assertThat(doc.getCompetencia()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(doc.getPapel()).isEqualTo("PRESTADOR");
    assertThat(doc.getValorServicos()).isEqualByComparingTo(new BigDecimal("80.00"));
    assertThat(doc.getValorIss()).isEqualByComparingTo(new BigDecimal("4.00"));
    assertThat(doc.isIssRetido()).isFalse();
    assertThat(doc.getTomadorNome()).isEqualTo("CLIENTE DO PORTAL");
    assertThat(doc.getDescricao()).isEqualTo("Escova");
  }

  /** O salao como tomador: nota de um fornecedor contra o CNPJ dele (pela raiz). */
  @Test
  void notaRecebidaTemPapelDeTomador() {
    NfseDocumentoDaReceitaEntity doc = new NfseDocumentoDaReceitaEntity();
    doc.setTipoDocumento("NFSE");

    LeitorDoDocumentoDaReceita.preencher(doc, nfse("12345678000195", "64351855000221"), CNPJ_DO_SALAO);

    assertThat(doc.getPapel()).isEqualTo("TOMADOR");
  }

  @Test
  void eventoDeCancelamentoApontaANota() {
    NfseDocumentoDaReceitaEntity doc = new NfseDocumentoDaReceitaEntity();
    doc.setTipoDocumento("EVENTO");

    LeitorDoDocumentoDaReceita.preencher(doc, evento(), CNPJ_DO_SALAO);

    assertThat(doc.isCancelada()).isTrue();
    assertThat(doc.getChaveDoEvento()).isEqualTo(CHAVE);
  }

  @Test
  void motivoEJustificativaDoCancelamento() {
    LeitorDoDocumentoDaReceita.Cancelamento dados = LeitorDoDocumentoDaReceita.cancelamento("""
        <evento xmlns="http://www.sped.fazenda.gov.br/nfse"><infEvento><pedRegEvento><infPedReg>
          <dhEvento>2026-11-07T14:30:00-03:00</dhEvento><CNPJAutor>64351855000140</CNPJAutor><chNFSe>%s</chNFSe>
          <e101101><xDesc>Cancelamento de NFS-e</xDesc><cMotivo>2</cMotivo><xMotivo>Cliente desistiu do servico</xMotivo></e101101>
        </infPedReg></pedRegEvento></infEvento></evento>
        """.formatted(CHAVE));

    assertThat(dados.quando()).isEqualTo("2026-11-07T17:30:00Z");
    assertThat(dados.motivo()).isEqualTo("Serviço não prestado");
    assertThat(dados.justificativa()).isEqualTo("Cliente desistiu do servico");
    assertThat(dados.codigoMotivo()).isEqualTo("2");
    assertThat(dados.tipo()).isEqualTo("Cancelamento");
    assertThat(dados.autor()).isEqualTo("64351855000140");
    assertThat(LeitorDoDocumentoDaReceita.motivoDoCancelamento("1", false, null)).isEqualTo("Erro na emissão");
    assertThat(LeitorDoDocumentoDaReceita.motivoDoCancelamento("01", true, "Cancelamento de NFS-e por Substituicao"))
        .isEqualTo("Cancelamento de NFS-e por Substituicao");
  }

  private static String nfse(String prestador, String tomador) {
    String tagToma = tomador.length() == 11 ? "CPF" : "CNPJ";
    return """
        <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.01"><infNFSe Id="NFS%s">
          <nNFSe>5</nNFSe><dhProc>2026-11-06T10:00:00-03:00</dhProc>
          <emit><CNPJ>%s</CNPJ><xNome>SALAO TESTE LTDA</xNome></emit>
          <valores><vBC>80.00</vBC><vISSQN>4.00</vISSQN><vLiq>80.00</vLiq></valores>
          <DPS versao="1.01"><infDPS Id="DPS1">
            <tpAmb>1</tpAmb><dCompet>2026-11-06</dCompet>
            <prest><CNPJ>%s</CNPJ></prest>
            <toma><%s>%s</%s><xNome>CLIENTE DO PORTAL</xNome></toma>
            <serv><cServ><cTribNac>060101</cTribNac><xDescServ>Escova</xDescServ></cServ></serv>
            <valores><vServPrest><vServ>80.00</vServ></vServPrest>
              <trib><tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>1</tpRetISSQN></tribMun></trib></valores>
          </infDPS></DPS></infNFSe></NFSe>
        """.formatted(CHAVE, prestador, prestador, tagToma, tomador, tagToma);
  }

  private static String evento() {
    return """
        <evento xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.01"><infEvento Id="EVT1">
          <pedRegEvento><infPedReg><chNFSe>%s</chNFSe>
            <e101101><xDesc>Cancelamento de NFS-e</xDesc><cMotivo>1</cMotivo></e101101>
          </infPedReg></pedRegEvento></infEvento></evento>
        """.formatted(CHAVE);
  }

  private static String gzipBase64(String texto) throws Exception {
    ByteArrayOutputStream saida = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(saida)) {
      gzip.write(texto.getBytes(StandardCharsets.UTF_8));
    }
    return Base64.getEncoder().encodeToString(saida.toByteArray());
  }
}
