package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalDanfeRenderService.java}.
 *
 * <p>Renderiza o PDF do DANFE/DANFCE via OpenPDF (mesma lib do original). {@code tenantId} e
 * {@code externalInvoiceId} sao recebidos mas nao usados no corpo do PDF — parametros preservados
 * do original (provavelmente reservados para watermark/auditoria futura).
 */
@Service
public class FiscalDanfeRenderService {

  public byte[] render(UUID tenantId, String externalInvoiceId, FiscalDanfeMapper.DanfeViewModel viewModel) {
    Document document = new Document(PageSize.A4, 24f, 24f, 24f, 24f);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(document, output);
      document.open();

      adicionarCabecalho(document, viewModel);
      adicionarBlocoIdentificacao(document, viewModel);
      adicionarBlocoEmitente(document, viewModel);
      adicionarBlocoDestinatario(document, viewModel);
      adicionarBlocoItens(document, viewModel);
      adicionarBlocoTotal(document, viewModel);
      adicionarObservacoes(document, viewModel);

      document.close();
      return output.toByteArray();
    } catch (DocumentException exception) {
      throw new IllegalStateException("Falha ao gerar PDF DANFE.", exception);
    } finally {
      output.reset();
    }
  }

  private void adicionarBlocoEmitente(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {2f, 1f, 1f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);

    String razao = vm.taxConfig != null ? vm.taxConfig.issuerRazaoSocial : null;
    String cnpj = vm.taxConfig != null ? vm.taxConfig.issuerCnpj : null;
    String ie = vm.taxConfig != null ? vm.taxConfig.issuerIe : null;
    String im = vm.taxConfig != null ? vm.taxConfig.issuerIm : null;
    String street = vm.taxConfig != null ? vm.taxConfig.issuerStreet : null;
    String number = vm.taxConfig != null ? vm.taxConfig.issuerNumber : null;
    String neighborhood = vm.taxConfig != null ? vm.taxConfig.issuerNeighborhood : null;
    String city = vm.taxConfig != null ? vm.taxConfig.issuerCity : null;
    String state = vm.taxConfig != null ? vm.taxConfig.issuerState : null;
    String zip = vm.taxConfig != null ? vm.taxConfig.issuerZipCode : null;

    table.addCell(headerCell("Emitente"));
    table.addCell(headerCell("CNPJ"));
    table.addCell(headerCell("IE / IM"));
    table.addCell(valueCell(valorOuTraco(razao)));
    table.addCell(valueCell(valorOuTraco(cnpj)));
    table.addCell(valueCell(valorOuTraco(ie) + " / " + valorOuTraco(im)));
    table.addCell(headerCell("Endereco", 3));
    table.addCell(valueCell(
        valorOuTraco(street) + ", " + valorOuTraco(number) + " - "
            + valorOuTraco(neighborhood) + " - " + valorOuTraco(city) + "/"
            + valorOuTraco(state) + " CEP " + valorOuTraco(zip),
        3));

    document.add(table);
  }

  private void adicionarCabecalho(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    Font titleFont = new Font(Font.HELVETICA, 13, Font.BOLD);
    Font subtitleFont = new Font(Font.HELVETICA, 9, Font.NORMAL);

    String titulo = "NFE".equalsIgnoreCase(vm.model)
        ? "DANFE - Documento Auxiliar da Nota Fiscal Eletronica"
        : "DANFCE - Documento Auxiliar da NFC-e";
    Paragraph title = new Paragraph(titulo, titleFont);
    title.setSpacingAfter(3f);
    document.add(title);

    Paragraph subtitle = new Paragraph(
        "Modelo fiscal conforme padrao nacional da NF-e/NFC-e (versao operacional Azzo).",
        subtitleFont);
    subtitle.setSpacingAfter(10f);
    document.add(subtitle);
  }

  private void adicionarBlocoIdentificacao(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {1.2f, 1f, 1.4f, 1.4f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);

    table.addCell(headerCell("Numero"));
    table.addCell(headerCell("Serie"));
    table.addCell(headerCell("Data Emissao"));
    table.addCell(headerCell("Status"));

    table.addCell(valueCell(valorOuTraco(vm.number)));
    table.addCell(valueCell(valorOuTraco(vm.series)));
    table.addCell(valueCell(formatDate(vm.issueDate)));
    table.addCell(valueCell(valorOuTraco(vm.status)));

    table.addCell(headerCell("Natureza da Operacao", 4));
    table.addCell(valueCell(valorOuTraco(vm.operationNature), 4));

    table.addCell(headerCell("Chave de Acesso", 4));
    table.addCell(valueCell(valorOuTraco(vm.accessKey), 4));

    table.addCell(headerCell("Protocolo de Autorizacao", 4));
    table.addCell(valueCell(valorOuTraco(vm.authorizationProtocol), 4));

    document.add(table);
  }

  private void adicionarBlocoDestinatario(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {2f, 1f, 1.2f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);

    table.addCell(headerCell("Tomador"));
    table.addCell(headerCell("Tipo"));
    table.addCell(headerCell("Documento"));

    String nome = vm.customer != null ? vm.customer.name : null;
    String tipo = vm.customer != null ? vm.customer.type : null;
    String documento = vm.customer != null ? vm.customer.document : null;

    table.addCell(valueCell(valorOuTraco(nome)));
    table.addCell(valueCell(valorOuTraco(tipo)));
    table.addCell(valueCell(valorOuTraco(documento)));

    document.add(table);
  }

  private void adicionarBlocoItens(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {0.5f, 2.5f, 0.8f, 0.8f, 0.9f, 0.9f, 1f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);

    table.addCell(headerCell("#"));
    table.addCell(headerCell("Descricao"));
    table.addCell(headerCell("CFOP"));
    table.addCell(headerCell("CST/CSOSN"));
    table.addCell(headerCell("Qtd"));
    table.addCell(headerCell("Vlr Unit"));
    table.addCell(headerCell("Total"));

    if (vm.items == null || vm.items.isEmpty()) {
      table.addCell(valueCell("-", 7));
    } else {
      int idx = 1;
      for (var item : vm.items) {
        table.addCell(valueCell(String.valueOf(idx++)));
        table.addCell(valueCell(valorOuTraco(item.description)));
        table.addCell(valueCell(valorOuTraco(item.cfop)));
        table.addCell(valueCell(valorOuTraco(item.cst)));
        table.addCell(valueCell(String.valueOf(item.quantity)));
        table.addCell(valueCell(formatCurrency(item.unitPrice)));
        table.addCell(valueCell(formatCurrency(item.totalPrice)));
      }
    }

    document.add(table);
  }

  private void adicionarBlocoTotal(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    PdfPTable table = new PdfPTable(new float[] {3f, 1f});
    table.setWidthPercentage(100);
    table.setSpacingAfter(8f);
    table.addCell(headerCell("Valor Total da Nota"));
    table.addCell(valueCell(formatCurrency(vm.totalAmount)));
    document.add(table);
  }

  private void adicionarObservacoes(Document document, FiscalDanfeMapper.DanfeViewModel vm) throws DocumentException {
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(100);
    table.addCell(headerCell("Observacoes"));
    table.addCell(valueCell(valorOuTraco(vm.notes)));
    document.add(table);
  }

  private PdfPCell headerCell(String value) {
    return headerCell(value, 1);
  }

  private PdfPCell headerCell(String value, int colspan) {
    Font font = new Font(Font.HELVETICA, 8, Font.BOLD);
    PdfPCell cell = new PdfPCell(new Phrase(value, font));
    cell.setBackgroundColor(new java.awt.Color(240, 240, 240));
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

  private String valorOuTraco(String value) {
    if (value == null || value.isBlank()) return "-";
    return value;
  }

  private String formatDate(String value) {
    if (value == null || value.isBlank()) return "-";
    try {
      return OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    } catch (Exception ignored) {
      return value;
    }
  }

  private String formatCurrency(long value) {
    double normalized = Math.abs(value) >= 1000 ? value / 100.0 : value;
    return String.format("R$ %.2f", normalized);
  }
}
