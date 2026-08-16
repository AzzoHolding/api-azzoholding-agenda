package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalProvider;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;

/**
 * Porte verbatim de {@code modules/fiscal/infrastructure/fiscal/MockFiscalProvider.java}.
 *
 * <p>Provider fiscal em memoria (sem persistencia, sem SEFAZ real) usado para desenvolvimento e
 * ambientes sem certificado digital configurado. Ativo quando {@code app.fiscal.provider} esta
 * ausente ou vale {@code "mock"} — mesma condicao do {@code @IfBuildProperty} original.
 *
 * <p>Estado guardado em {@link ConcurrentHashMap} por processo: reinicia a cada deploy, nao e
 * compartilhado entre instancias. Fiel ao original, que tem a mesma limitacao.
 */
@Service
@ConditionalOnProperty(name = "app.fiscal.provider", havingValue = "mock", matchIfMissing = true)
public class MockFiscalProvider implements FiscalProvider {

  private final ConcurrentHashMap<UUID, FiscalDtos.TaxConfig> taxConfigByTenant = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Map<String, FiscalDtos.Invoice>> invoicesByTenant =
      new ConcurrentHashMap<>();

  @Override
  public FiscalDtos.TaxConfig obterTaxConfig(UUID tenantId) {
    return taxConfigByTenant.computeIfAbsent(tenantId, key -> defaultTaxConfig());
  }

  @Override
  public FiscalDtos.TaxConfig atualizarTaxConfig(UUID tenantId, FiscalDtos.TaxConfig request) {
    taxConfigByTenant.put(tenantId, request == null ? defaultTaxConfig() : request);
    return obterTaxConfig(tenantId);
  }

  @Override
  public FiscalDtos.InvoiceListResponse listarInvoices(
      UUID tenantId, String status, String from, String to, Integer page, Integer pageSize) {
    int pagina = page == null || page < 1 ? 1 : page;
    int tamanhoPagina = pageSize == null || pageSize < 1 ? 20 : pageSize;
    LocalDate dataDe = from == null ? null : DataUtil.parseDataISO(from);
    LocalDate dataAte = to == null ? null : DataUtil.parseDataISO(to);

    List<FiscalDtos.Invoice> filtradas =
        invoicesByTenant.computeIfAbsent(tenantId, k -> new LinkedHashMap<>()).values().stream()
            .filter(i -> status == null || status.isBlank() || status.equalsIgnoreCase(i.status))
            .filter(
                i -> {
                  if (dataDe == null && dataAte == null) return true;
                  LocalDate data = DataUtil.parseDataISO(i.createdAt);
                  boolean okDe = dataDe == null || (data != null && !data.isBefore(dataDe));
                  boolean okAte = dataAte == null || (data != null && !data.isAfter(dataAte));
                  return okDe && okAte;
                })
            .sorted(Comparator.comparing((FiscalDtos.Invoice i) -> i.createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

    int inicio = (pagina - 1) * tamanhoPagina;
    int fim = Math.min(inicio + tamanhoPagina, filtradas.size());
    List<FiscalDtos.Invoice> paginaItems = inicio >= filtradas.size() ? List.of() : filtradas.subList(inicio, fim);

    FiscalDtos.InvoiceListResponse resp = new FiscalDtos.InvoiceListResponse();
    resp.items = new ArrayList<>(paginaItems);
    resp.total = filtradas.size();
    resp.page = pagina;
    resp.pageSize = tamanhoPagina;
    return resp;
  }

  private Map<String, FiscalDtos.Invoice> getInvoices(UUID tenantId) {
    return invoicesByTenant.computeIfAbsent(tenantId, k -> new LinkedHashMap<>());
  }

  @Override
  public FiscalDtos.Invoice obterInvoice(UUID tenantId, String id) {
    FiscalDtos.Invoice invoice = getInvoices(tenantId).get(id);
    if (invoice == null) throw new IllegalArgumentException("Invoice nao encontrada");
    return invoice;
  }

  @Override
  public FiscalDtos.Invoice criarInvoice(UUID tenantId, FiscalDtos.Invoice request) {
    FiscalDtos.Invoice invoice = new FiscalDtos.Invoice();
    invoice.id = UUID.randomUUID().toString();
    invoice.type = request.type;
    invoice.customer = request.customer;
    invoice.items = request.items == null ? new ArrayList<>() : request.items;
    invoice.operationNature = request.operationNature;
    invoice.notes = request.notes;
    invoice.appointmentId = request.appointmentId;
    invoice.status = request.status == null || request.status.isBlank() ? "DRAFT" : request.status;
    invoice.createdAt = LocalDate.now().toString();
    invoice.totalAmount = invoice.items.stream().mapToLong(i -> i.totalPrice).sum();

    getInvoices(tenantId).put(invoice.id, invoice);
    return invoice;
  }

  @Override
  public FiscalDtos.Invoice atualizarInvoice(UUID tenantId, String id, FiscalDtos.Invoice request) {
    FiscalDtos.Invoice invoice = obterInvoice(tenantId, id);
    if (!"DRAFT".equalsIgnoreCase(invoice.status)) {
      throw new IllegalArgumentException("Somente rascunhos podem ser alterados.");
    }
    invoice.type = request.type;
    invoice.customer = request.customer;
    invoice.items = request.items == null ? new ArrayList<>() : request.items;
    invoice.operationNature = request.operationNature;
    invoice.notes = request.notes;
    invoice.appointmentId = request.appointmentId;
    invoice.totalAmount = invoice.items.stream().mapToLong(i -> i.totalPrice).sum();
    return invoice;
  }

  @Override
  public FiscalDtos.Invoice cancelarInvoice(UUID tenantId, String id, FiscalDtos.CancelInvoiceRequest request) {
    FiscalDtos.Invoice invoice = obterInvoice(tenantId, id);
    invoice.status = "CANCELLED";
    invoice.cancelReason = request != null ? request.reason : null;
    return invoice;
  }

  @Override
  public FiscalDtos.Invoice autorizarInvoice(UUID tenantId, String id) {
    FiscalDtos.Invoice invoice = obterInvoice(tenantId, id);
    validarConfiguracaoFiscalObrigatoria(tenantId, invoice);
    invoice.status = "AUTHORIZED";
    invoice.accessKey = gerarChaveAcessoMock(invoice, obterTaxConfig(tenantId));
    invoice.authorizationProtocol = gerarProtocoloMock();
    return invoice;
  }

  @Override
  public byte[] gerarPdfInvoice(UUID tenantId, String id) {
    FiscalDtos.Invoice invoice = obterInvoice(tenantId, id);
    String texto = "Invoice " + invoice.id + " - " + invoice.status;
    return ("%PDF-1.4\n" + texto + "\n%%EOF").getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public FiscalDtos.ApuracaoMensal obterApuracaoAtual(UUID tenantId) {
    YearMonth atual = YearMonth.now();
    return calcularApuracao(tenantId, atual.getYear(), atual.getMonthValue());
  }

  @Override
  public FiscalDtos.ApuracaoMensal obterApuracao(UUID tenantId, int ano, int mes) {
    return calcularApuracao(tenantId, ano, mes);
  }

  @Override
  public FiscalDtos.ApuracaoMensal recalcularApuracao(UUID tenantId, int ano, int mes) {
    return calcularApuracao(tenantId, ano, mes);
  }

  @Override
  public List<FiscalDtos.ApuracaoResumo> historico(UUID tenantId, int limite) {
    int lim = limite < 1 ? 12 : limite;
    YearMonth base = YearMonth.now();
    ArrayList<FiscalDtos.ApuracaoResumo> itens = new ArrayList<>();
    for (int i = 0; i < lim; i++) {
      YearMonth ym = base.minusMonths(i);
      FiscalDtos.ApuracaoMensal m = calcularApuracao(tenantId, ym.getYear(), ym.getMonthValue());
      FiscalDtos.ApuracaoResumo r = new FiscalDtos.ApuracaoResumo();
      r.ano = m.ano;
      r.mes = m.mes;
      r.totalServicos = m.totalServicos;
      r.totalImpostos = m.totalImpostos;
      r.totalDocumentos = m.totalDocumentos;
      itens.add(r);
    }
    return itens;
  }

  @Override
  public FiscalDtos.ResumoAnual resumoAnual(UUID tenantId, int ano) {
    FiscalDtos.ResumoAnual resp = new FiscalDtos.ResumoAnual();
    for (int mes = 1; mes <= 12; mes++) {
      FiscalDtos.ApuracaoMensal ap = calcularApuracao(tenantId, ano, mes);
      FiscalDtos.ApuracaoResumo item = new FiscalDtos.ApuracaoResumo();
      item.ano = ano;
      item.mes = mes;
      item.totalServicos = ap.totalServicos;
      item.totalImpostos = ap.totalImpostos;
      item.totalDocumentos = ap.totalDocumentos;
      resp.meses.add(item);
      resp.totalServicos += item.totalServicos;
      resp.totalImpostos += item.totalImpostos;
      resp.totalDocumentos += item.totalDocumentos;
    }
    return resp;
  }

  private FiscalDtos.ApuracaoMensal calcularApuracao(UUID tenantId, int ano, int mes) {
    FiscalDtos.TaxConfig tax = obterTaxConfig(tenantId);
    List<FiscalDtos.Invoice> docs =
        getInvoices(tenantId).values().stream()
            .filter(
                i -> {
                  LocalDate data = DataUtil.parseDataISO(i.createdAt);
                  return data != null && data.getYear() == ano && data.getMonthValue() == mes;
                })
            .toList();

    long totalServicos = docs.stream().mapToLong(i -> i.totalAmount).sum();
    BigDecimal taxaTotal =
        (tax.icmsRate != null ? tax.icmsRate : BigDecimal.ZERO)
            .add(tax.pisRate != null ? tax.pisRate : BigDecimal.ZERO)
            .add(tax.cofinsRate != null ? tax.cofinsRate : BigDecimal.ZERO);

    FiscalDtos.ApuracaoMensal resp = new FiscalDtos.ApuracaoMensal();
    resp.ano = ano;
    resp.mes = mes;
    resp.documentos = new ArrayList<>(docs);
    resp.totalServicos = totalServicos;
    resp.totalImpostos =
        BigDecimal.valueOf(totalServicos)
            .multiply(taxaTotal)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.HALF_UP)
            .longValue();
    resp.totalDocumentos = docs.size();
    return resp;
  }

  private FiscalDtos.TaxConfig defaultTaxConfig() {
    FiscalDtos.TaxConfig c = new FiscalDtos.TaxConfig();
    c.regime = "SIMPLES_NACIONAL";
    c.icmsRate = new BigDecimal("2.75");
    c.pisRate = new BigDecimal("0.65");
    c.cofinsRate = new BigDecimal("3.00");
    c.issuerUfCode = "35";
    return c;
  }

  private void validarConfiguracaoFiscalObrigatoria(UUID tenantId, FiscalDtos.Invoice invoice) {
    FiscalDtos.TaxConfig cfg = obterTaxConfig(tenantId);
    if (cfg == null) throw new IllegalArgumentException("Configuracao fiscal do emitente nao encontrada.");
    if (isBlank(cfg.issuerRazaoSocial)) throw new IllegalArgumentException("Configure Razao Social do emitente.");
    if (digits(cfg.issuerCnpj).length() != 14) {
      throw new IllegalArgumentException("Configure CNPJ valido do emitente (14 digitos).");
    }
    if (isBlank(cfg.issuerIe)) throw new IllegalArgumentException("Configure IE do emitente.");
    if (isBlank(cfg.issuerCity) || isBlank(cfg.issuerState)) {
      throw new IllegalArgumentException("Configure cidade e UF do emitente.");
    }
    if (invoice != null && "NFCE".equalsIgnoreCase(invoice.type)) {
      boolean possuiCsc = !isBlank(cfg.nfceCscHomologation) || !isBlank(cfg.nfceCscProduction);
      boolean possuiToken = !isBlank(cfg.nfceCscIdTokenHomologation) || !isBlank(cfg.nfceCscIdTokenProduction);
      if (!possuiCsc || !possuiToken) {
        throw new IllegalArgumentException("Configure CSC e idToken da NFC-e para simulacao SEFAZ.");
      }
    }
  }

  private String gerarProtocoloMock() {
    long suffix = Math.abs(ThreadLocalRandom.current().nextLong()) % 1_000_000_000_000L;
    return String.format(Locale.ROOT, "13%013d", suffix);
  }

  private String gerarChaveAcessoMock(FiscalDtos.Invoice invoice, FiscalDtos.TaxConfig cfg) {
    String cUf = normalizeUfCode(cfg.issuerUfCode, cfg.issuerState);
    String aaMm = YearMonth.now().toString().replace("-", "").substring(2);
    String cnpj = padLeft(digits(cfg.issuerCnpj), 14);
    String modelo = "NFE".equalsIgnoreCase(invoice.type) ? "55" : "65";
    String serie = padLeft(digits(invoice.serieNf), 3);
    String numero = padLeft(digits(invoice.numeroNf), 9);
    String tpEmis = "1";
    String cNf = String.format(Locale.ROOT, "%08d", ThreadLocalRandom.current().nextInt(0, 100_000_000));
    String parcial = cUf + aaMm + cnpj + modelo + serie + numero + tpEmis + cNf;
    int dv = modulo11Dv(parcial);
    return parcial + dv;
  }

  private int modulo11Dv(String chave43) {
    int peso = 2;
    int soma = 0;
    for (int i = chave43.length() - 1; i >= 0; i--) {
      int n = Character.digit(chave43.charAt(i), 10);
      soma += n * peso;
      peso = peso == 9 ? 2 : peso + 1;
    }
    int mod = soma % 11;
    return (mod == 0 || mod == 1) ? 0 : 11 - mod;
  }

  private String normalizeUfCode(String ufCode, String ufState) {
    String normalized = digits(ufCode);
    if (normalized.length() == 2) return normalized;
    String uf = ufState == null ? "" : ufState.trim().toUpperCase(Locale.ROOT);
    return switch (uf) {
      case "AC" -> "12";
      case "AL" -> "27";
      case "AP" -> "16";
      case "AM" -> "13";
      case "BA" -> "29";
      case "CE" -> "23";
      case "DF" -> "53";
      case "ES" -> "32";
      case "GO" -> "52";
      case "MA" -> "21";
      case "MT" -> "51";
      case "MS" -> "50";
      case "MG" -> "31";
      case "PA" -> "15";
      case "PB" -> "25";
      case "PR" -> "41";
      case "PE" -> "26";
      case "PI" -> "22";
      case "RJ" -> "33";
      case "RN" -> "24";
      case "RS" -> "43";
      case "RO" -> "11";
      case "RR" -> "14";
      case "SC" -> "42";
      case "SP" -> "35";
      case "SE" -> "28";
      case "TO" -> "17";
      default -> "35";
    };
  }

  private String padLeft(String value, int length) {
    String digits = value == null ? "" : value;
    if (digits.length() >= length) return digits.substring(digits.length() - length);
    return "0".repeat(length - digits.length()) + digits;
  }

  private String digits(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
