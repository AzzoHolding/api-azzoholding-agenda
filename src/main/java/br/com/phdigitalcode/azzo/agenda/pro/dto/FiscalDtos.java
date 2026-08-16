package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Espelha {@code modules/fiscal/api/dto/FiscalDtos.java}.
 *
 * <p>Classes de campo publico e sem getter, como no original — o contrato JSON depende do nome do
 * campo, entao renomear qualquer um quebra o frontend.
 *
 * <p>Os valores monetarios sao {@code long} em centavos ({@code unitPrice}, {@code totalPrice},
 * {@code totalAmount}, os totais de apuracao); as aliquotas de {@link TaxConfig} sao
 * {@link BigDecimal} e os campos de partilha de {@link InvoiceItem} sao {@link Double}. Mistura do
 * original, preservada.
 */
public final class FiscalDtos {

  private FiscalDtos() {}

  public static class TaxConfig {
    public String regime;
    public BigDecimal icmsRate;
    public BigDecimal pisRate;
    public BigDecimal cofinsRate;
    public String issuerRazaoSocial;
    public String issuerNomeFantasia;
    public String issuerCnpj;
    public String issuerIe;
    public String issuerIm;
    public String issuerPhone;
    public String issuerEmail;
    public String issuerStreet;
    public String issuerNumber;
    public String issuerComplement;
    public String issuerNeighborhood;
    public String issuerCity;
    public String issuerState;
    public String issuerZipCode;
    public String issuerUfCode;
    public String nfceCscHomologation;
    public String nfceCscIdTokenHomologation;
    public String nfceCscProduction;
    public String nfceCscIdTokenProduction;
  }

  public static class InvoiceCustomer {
    public String type;
    public String document;
    public String name;
    public String email;
    public String phone;
  }

  public static class InvoiceItem {
    public String id;
    public String description;
    public int quantity;
    public long unitPrice;
    public long totalPrice;
    public String ncm;
    public String cfop;
    public String cst;
    public Double baseIcmsSt;
    public Double mvaPercent;
    public Double aliquotaInternaDestino;
    public Double aliquotaInterestadual;
    public Double baseFcp;
    public Double aliquotaFcp;
    public Double percentualPartilhaOrigem;
    public Double percentualPartilhaDestino;
  }

  public static class Invoice {
    public String id;
    public String type;
    public InvoiceCustomer customer;
    public List<InvoiceItem> items = new ArrayList<>();
    public String operationNature;
    public String notes;

    /**
     * {@code WRITE_ONLY}: o XML assinado entra pelo corpo da requisicao mas <b>nunca sai</b> na
     * resposta. Remover esta anotacao vaza o documento assinado do tenant.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String signedXml;

    public String appointmentId;
    public String status;
    public String numeroNf;
    public String serieNf;
    public String accessKey;
    public String authorizationProtocol;
    public String cancelReason;
    public String createdAt;
    public long totalAmount;
  }

  public static class InvoiceListResponse {
    public List<Invoice> items = new ArrayList<>();
    public long total;
    public int page;
    public int pageSize;
  }

  public static class CancelInvoiceRequest {
    public String reason;
  }

  public static class AuthorizeInvoiceRequest {
    public String certificatePassword;
  }

  public static class DanfeJobResponse {
    public String jobId;
    public String invoiceId;
    public String status;
    public String downloadUrl;
    public Boolean downloadAvailable;
    public Boolean downloadConsumed;
    public String downloadExpiresAt;
    public String errorCode;
    public String errorMessage;
    public String requestedAt;
    public String finishedAt;
  }

  public static class CertificateUpsertRequest {
    public String pfxBase64;
    public String password;
  }

  /** Resposta do certificado — <b>sem</b> o PFX e sem a senha, como no original. */
  public static class CertificateResponse {
    public String id;
    public String thumbprint;
    public String subjectName;
    public String validTo;
    public String status;
    public String createdAt;
  }

  public static class ApuracaoMensal {
    public int ano;
    public int mes;
    public long totalServicos;
    public long totalImpostos;
    public long totalDocumentos;
    public List<Invoice> documentos = new ArrayList<>();
  }

  public static class ApuracaoResumo {
    public int ano;
    public int mes;
    public long totalServicos;
    public long totalImpostos;
    public long totalDocumentos;
  }

  public static class ResumoAnual {
    public long totalServicos;
    public long totalImpostos;
    public long totalDocumentos;
    public List<ApuracaoResumo> meses = new ArrayList<>();
  }
}
