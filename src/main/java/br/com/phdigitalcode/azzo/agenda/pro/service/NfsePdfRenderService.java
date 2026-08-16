package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;

/**
 * Porte verbatim de {@code modules/nfse/application/NfsePdfRenderService.java} (Fronteira 7).
 *
 * <p>Gera o documento auxiliar em PDF via OpenPDF — mesma lib ja usada por {@code
 * FiscalDanfeRenderService} (Fronteira 5 de {@code fiscal}) para o DANFE. Documento puramente
 * informativo: o XML autorizado permanece a fonte de verdade fiscal, como o texto do cabecalho do
 * original deixa explicito.
 */
@Service
public class NfsePdfRenderService {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Sao_Paulo"));

  public byte[] render(NfseInvoiceEntity invoice, List<NfseInvoiceItemEntity> items) {
    Document document = new Document(PageSize.A4, 24f, 24f, 24f, 24f);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(document, output);
      document.open();
      addHeader(document);
      addIdentification(document, invoice);
      addVerificacao(document, invoice);
      addTomador(document, invoice);
      addItems(document, items);
      addTotal(document, invoice);
      addObservacoes(document, invoice);
      document.close();
      return output.toByteArray();
    } catch (DocumentException ex) {
      throw new IllegalStateException("Falha ao gerar PDF da NFS-e.", ex);
    }
  }

  private void addHeader(Document document) throws DocumentException {
    Font title = new Font(Font.HELVETICA, 13, Font.BOLD);
    Font subtitle = new Font(Font.HELVETICA, 9, Font.NORMAL);
    document.add(new Paragraph("NFS-e - Documento Auxiliar", title));
    Paragraph text =
        new Paragraph(
            "Documento gerado para consulta operacional. O XML autorizado permanece como verdade fiscal.",
            subtitle);
    text.setSpacingAfter(10f);
    document.add(text);
  }

  private void addIdentification(Document document, NfseInvoiceEntity invoice) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {1.2f, 1.2f, 1f, 1f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);
    table.addCell(headerCell("Numero NFS-e"));
    table.addCell(headerCell("RPS"));
    table.addCell(headerCell("Serie"));
    table.addCell(headerCell("Status"));
    table.addCell(valueCell(valueOrDash(invoice.getNumeroNfse())));
    table.addCell(valueCell(invoice.getNumeroRps() != null ? String.valueOf(invoice.getNumeroRps()) : "-"));
    table.addCell(valueCell(valueOrDash(invoice.getSerieRps())));
    table.addCell(valueCell(invoice.getFiscalStatus() != null ? invoice.getFiscalStatus().name() : "-"));
    table.addCell(headerCell("Codigo Verificacao", 2));
    table.addCell(headerCell("Protocolo", 2));
    table.addCell(valueCell(valueOrDash(invoice.getCodigoVerificacao()), 2));
    table.addCell(valueCell(valueOrDash(invoice.getProtocolo()), 2));
    table.addCell(headerCell("Data Emissao", 2));
    table.addCell(headerCell("Data Competencia", 2));
    table.addCell(
        valueCell(
            invoice.getDataEmissao() != null ? DATE_TIME_FORMATTER.format(invoice.getDataEmissao()) : "-", 2));
    table.addCell(
        valueCell(invoice.getDataCompetencia() != null ? invoice.getDataCompetencia().toString() : "-", 2));
    document.add(table);
  }

  private void addVerificacao(Document document, NfseInvoiceEntity invoice) throws DocumentException {
    if (invoice.getCodigoVerificacao() == null || invoice.getCodigoVerificacao().isBlank()) return;
    Font boldFont = new Font(Font.HELVETICA, 9, Font.BOLD);
    Font normalFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
    Font smallFont = new Font(Font.HELVETICA, 7, Font.NORMAL);
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);
    PdfPCell headerCell = new PdfPCell(new Phrase("Autenticidade da NFS-e", boldFont));
    headerCell.setBackgroundColor(new Color(220, 235, 255));
    headerCell.setBorder(Rectangle.BOX);
    headerCell.setPadding(5f);
    table.addCell(headerCell);
    String codVerif = invoice.getCodigoVerificacao().trim();
    PdfPCell codeCell = new PdfPCell(new Phrase("Codigo de Verificacao: " + codVerif, normalFont));
    codeCell.setBorder(Rectangle.BOX);
    codeCell.setPadding(5f);
    table.addCell(codeCell);
    String instrucao =
        "Para confirmar a autenticidade desta NFS-e, acesse o portal da Prefeitura do municipio "
            + valueOrDash(invoice.getMunicipioCodigoIbge())
            + " e consulte pelo Codigo de Verificacao acima ou pelo numero da NFS-e "
            + valueOrDash(invoice.getNumeroNfse())
            + ".";
    PdfPCell instrCell = new PdfPCell(new Phrase(instrucao, smallFont));
    instrCell.setBorder(Rectangle.BOX);
    instrCell.setPadding(5f);
    table.addCell(instrCell);
    document.add(table);
  }

  private void addTomador(Document document, NfseInvoiceEntity invoice) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {2f, 1f, 1.2f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);
    table.addCell(headerCell("Tomador"));
    table.addCell(headerCell("Tipo"));
    table.addCell(headerCell("Documento"));
    table.addCell(valueCell(valueOrDash(invoice.getCustomerName())));
    table.addCell(valueCell(invoice.getCustomerType() != null ? invoice.getCustomerType().name() : "-"));
    table.addCell(valueCell(valueOrDash(invoice.getCustomerDocument())));
    document.add(table);
  }

  private void addItems(Document document, List<NfseInvoiceItemEntity> items) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {0.5f, 2.3f, 0.8f, 0.8f, 0.8f, 0.9f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);
    table.addCell(headerCell("#"));
    table.addCell(headerCell("Descricao"));
    table.addCell(headerCell("Item LC116"));
    table.addCell(headerCell("Qtd"));
    table.addCell(headerCell("Vlr Unit"));
    table.addCell(headerCell("Vlr Total"));
    if (items == null || items.isEmpty()) {
      table.addCell(valueCell("-", 6));
    } else {
      for (NfseInvoiceItemEntity item : items) {
        table.addCell(valueCell(item.getLineNumber() != null ? String.valueOf(item.getLineNumber()) : "-"));
        table.addCell(valueCell(valueOrDash(item.getDescricaoServico())));
        table.addCell(valueCell(valueOrDash(item.getItemListaServico())));
        table.addCell(valueCell(formatDecimal(item.getQuantidade())));
        table.addCell(valueCell(formatCurrency(item.getValorUnitario())));
        table.addCell(valueCell(formatCurrency(item.getValorTotal())));
      }
    }
    document.add(table);
  }

  private void addTotal(Document document, NfseInvoiceEntity invoice) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {2.5f, 1f, 1f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);
    table.addCell(headerCell("Natureza da Operacao"));
    table.addCell(headerCell("Aliquota ISS"));
    table.addCell(headerCell("Valor ISS"));
    table.addCell(valueCell(valueOrDash(invoice.getNaturezaOperacao())));
    table.addCell(valueCell(formatDecimal(invoice.getAliquotaIss())));
    table.addCell(valueCell(formatCurrency(invoice.getValorIss())));
    table.addCell(headerCell("Valor Servicos", 2));
    table.addCell(headerCell("Valor Deducoes"));
    table.addCell(valueCell(formatCurrency(invoice.getValorServicos()), 2));
    table.addCell(valueCell(formatCurrency(invoice.getValorDeducoes())));
    document.add(table);
  }

  private void addObservacoes(Document document, NfseInvoiceEntity invoice) throws DocumentException {
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(100);
    table.addCell(headerCell("Observacoes"));
    table.addCell(valueCell(valueOrDash(invoice.getNotes())));
    document.add(table);
  }

  private PdfPCell headerCell(String value) {
    return headerCell(value, 1);
  }

  private PdfPCell headerCell(String value, int colspan) {
    Font font = new Font(Font.HELVETICA, 8, Font.BOLD);
    PdfPCell cell = new PdfPCell(new Phrase(value, font));
    cell.setBackgroundColor(new Color(240, 240, 240));
    cell.setBorder(Rectangle.BOX);
    cell.setPadding(5f);
    cell.setColspan(colspan);
    return cell;
  }

  private PdfPCell valueCell(String value) {
    return valueCell(value, 1);
  }

  private PdfPCell valueCell(String value, int colspan) {
    Font font = new Font(Font.HELVETICA, 8, Font.NORMAL);
    PdfPCell cell = new PdfPCell(new Phrase(value, font));
    cell.setBorder(Rectangle.BOX);
    cell.setPadding(5f);
    cell.setColspan(colspan);
    return cell;
  }

  private String valueOrDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private String formatCurrency(Number value) {
    if (value == null) return "R$ 0,00";
    return String.format("R$ %.2f", value.doubleValue());
  }

  private String formatDecimal(Number value) {
    if (value == null) return "0";
    return String.format("%.4f", value.doubleValue());
  }
}
