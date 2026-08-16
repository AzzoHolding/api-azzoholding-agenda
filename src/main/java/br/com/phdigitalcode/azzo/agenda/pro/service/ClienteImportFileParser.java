package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;

/**
 * Espelha {@code modules/customers/application/importacao/ClienteImportFileParser.java}: le CSV ou
 * XLSX com o mesmo cabecalho fixo (12 colunas) e devolve as linhas nao vazias.
 */
@Component
public class ClienteImportFileParser {

  public record ClienteImportRow(
      int lineNumber,
      String nome,
      String telefone,
      String email,
      String dataNascimento,
      String observacoes,
      String cep,
      String logradouro,
      String numero,
      String complemento,
      String bairro,
      String cidade,
      String uf) {}

  private static final List<String> HEADER =
      List.of(
          "nome",
          "telefone",
          "email",
          "data_nascimento",
          "observacoes",
          "cep",
          "logradouro",
          "numero",
          "complemento",
          "bairro",
          "cidade",
          "uf");

  public List<ClienteImportRow> parse(byte[] arquivo, String nomeArquivo) {
    String nome = nomeArquivo == null ? "" : nomeArquivo.toLowerCase(Locale.ROOT);
    try {
      if (nome.endsWith(".csv")) {
        return parseCsv(arquivo);
      }
      if (nome.endsWith(".xlsx")) {
        return parseXlsx(arquivo);
      }
      throw new ApiClientErrorException(
          "Formato de importacao nao suportado. Use csv ou xlsx.", HttpStatus.BAD_REQUEST.value());
    } catch (ApiClientErrorException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao ler arquivo de importacao de clientes.", exception);
    }
  }

  private List<ClienteImportRow> parseCsv(byte[] arquivo) throws IOException {
    List<ClienteImportRow> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(arquivo), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      validarCabecalho(parseCsvColumns(headerLine));
      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        List<String> columns = parseCsvColumns(line);
        ClienteImportRow row = toRow(lineNumber, columns);
        if (!isBlankRow(row)) {
          rows.add(row);
        }
      }
    }
    return rows;
  }

  private List<ClienteImportRow> parseXlsx(byte[] arquivo) throws IOException {
    List<ClienteImportRow> rows = new ArrayList<>();
    DataFormatter formatter = new DataFormatter();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
      Sheet sheet = workbook.getSheetAt(0);
      if (sheet == null) {
        throw new ApiClientErrorException(
            "Planilha de importacao vazia.", HttpStatus.BAD_REQUEST.value());
      }
      Row headerRow = sheet.getRow(0);
      validarCabecalho(readCells(headerRow, formatter));
      for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        Row row = sheet.getRow(rowIndex);
        ClienteImportRow parsed = toRow(rowIndex + 1, readCells(row, formatter));
        if (!isBlankRow(parsed)) {
          rows.add(parsed);
        }
      }
    }
    return rows;
  }

  private List<String> parseCsvColumns(String line) {
    if (line == null) return List.of();
    String[] parts = line.split(",", -1);
    List<String> columns = new ArrayList<>(parts.length);
    for (String part : parts) {
      columns.add(unquote(part));
    }
    return columns;
  }

  private String unquote(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
      return normalized.substring(1, normalized.length() - 1).replace("\"\"", "\"").trim();
    }
    return normalized;
  }

  private List<String> readCells(Row row, DataFormatter formatter) {
    if (row == null) return List.of();
    List<String> values = new ArrayList<>(HEADER.size());
    for (int i = 0; i < HEADER.size(); i++) {
      values.add(formatter.formatCellValue(row.getCell(i)).trim());
    }
    return values;
  }

  private void validarCabecalho(List<String> header) {
    List<String> normalized = new ArrayList<>(HEADER.size());
    for (int i = 0; i < HEADER.size(); i++) {
      normalized.add(i < header.size() ? safe(header.get(i)).toLowerCase(Locale.ROOT) : "");
    }
    if (!HEADER.equals(normalized)) {
      throw new ApiClientErrorException(
          "Cabecalho invalido para importacao de clientes. Baixe o modelo e tente novamente.",
          HttpStatus.BAD_REQUEST.value());
    }
  }

  private ClienteImportRow toRow(int lineNumber, List<String> columns) {
    return new ClienteImportRow(
        lineNumber,
        column(columns, 0),
        column(columns, 1),
        column(columns, 2),
        column(columns, 3),
        column(columns, 4),
        column(columns, 5),
        column(columns, 6),
        column(columns, 7),
        column(columns, 8),
        column(columns, 9),
        column(columns, 10),
        column(columns, 11));
  }

  private String column(List<String> columns, int index) {
    return index < columns.size() ? safe(columns.get(index)) : "";
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean isBlankRow(ClienteImportRow row) {
    return safe(row.nome()).isBlank()
        && safe(row.telefone()).isBlank()
        && safe(row.email()).isBlank()
        && safe(row.dataNascimento()).isBlank()
        && safe(row.observacoes()).isBlank()
        && safe(row.cep()).isBlank()
        && safe(row.logradouro()).isBlank()
        && safe(row.numero()).isBlank()
        && safe(row.complemento()).isBlank()
        && safe(row.bairro()).isBlank()
        && safe(row.cidade()).isBlank()
        && safe(row.uf()).isBlank();
  }
}
