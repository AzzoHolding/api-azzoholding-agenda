package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Espelha {@code modules/nfse/api/dto/NfseDtos.java} — porte verbatim (campos publicos, sem
 * Lombok, mesmo padrao de {@code FiscalDtos}). Consumido pela API REST na Fronteira 6
 * ({@code NfseResource}, 18 endpoints); entra na Fronteira 1 porque os contratos ja sao
 * referenciados pelos testes das fronteiras seguintes.
 */
public final class NfseDtos {

  private NfseDtos() {}

  public static class Config {
    public String ambiente;
    public String municipioCodigoIbge;
    public String provedor;
    public String serieRps;
    public BigDecimal aliquotaIssPadrao;
    public String itemListaServicoPadrao;
    public String codigoTributacaoMunicipio;
    public String applicationVersion;
    public String nationalTaxCodeDefault;
    public String nbsCodeDefault;
    public String simplesNacionalSituacao;
    public String simplesNacionalRegimeTributacao;
    public String especialRegimeTributacao;
    public String emissionMode;
    public String emitForCpfMode;
    public boolean autoIssueOnAppointmentClose;
    public String wsUrl;
    public String wsUrlHomologacao;
  }

  public static class Customer {
    public String type;
    public String document;
    public String countryCode;
    public String documentType;
    public String name;
    public String email;
    public String phone;
  }

  public static class Item {
    public Integer lineNumber;
    public String descricaoServico;
    public BigDecimal quantidade;
    public BigDecimal valorUnitario;
    public BigDecimal valorTotal;
    public String itemListaServico;
    public String codigoTributacaoMunicipio;
    public String nationalTaxCode;
    public String nbsCode;
    public BigDecimal aliquotaIss;
    public BigDecimal valorIss;
  }

  public static class Invoice {
    public String id;
    public String appointmentId;
    public String ambiente;
    public String municipioCodigoIbge;
    public String provedor;
    public String fiscalStatus;
    public String operationalStatus;
    public Long numeroRps;
    public String serieRps;
    public String numeroNfse;
    public String codigoVerificacao;
    public String chaveAcessoNfse;
    public String protocolo;
    public String dataCompetencia;
    public String dataEmissao;
    public String naturezaOperacao;
    public String itemListaServico;
    public String codigoTributacaoMunicipio;
    public String nationalTaxCode;
    public String nbsCode;
    public String localPrestacaoCodigoIbge;
    public BigDecimal valorServicos;
    public BigDecimal valorDeducoes;
    public BigDecimal valorIss;
    public BigDecimal aliquotaIss;
    public boolean issRetido;
    public String notes;
    public Customer customer;
    public List<Item> items = new ArrayList<>();
    public String createdAt;
    public String updatedAt;
  }

  public static class InvoiceListResponse {
    public List<Invoice> items = new ArrayList<>();
    public long total;
    public int page;
    public int pageSize;
  }

  public static class AuthorizeRequest {
    public String certificatePassword;
    public String unlockTokenId;
    /** Provedor escolhido pelo usuario na autorizacao. Opcional: vazio mantem o do rascunho. */
    public String provedor;
  }

  public static class CancelRequest {
    public String reason;
    public String certificatePassword;
  }

  public static class PdfJobResponse {
    public String jobId;
    public String invoiceId;
    public String status;
    public String errorCode;
    public String errorMessage;
    public String requestedAt;
    public String finishedAt;
    public Boolean downloadAvailable;
    public Boolean downloadConsumed;
    public String downloadExpiresAt;
  }

  public static class CertificateUnlockRequest {
    public String certificatePassword;
  }

  public static class CertificateUnlockStatusResponse {
    public Boolean active;
    public String unlockTokenId;
    public String issuedAt;
    public String expiresAt;
    public String status;
  }

  public static class ProviderCapabilities {
    public String municipioCodigoIbge;
    public String provedor;
    public String layoutVersion;
    public boolean cancelSupported;
    public Integer cancelWindowHours;
    public String cancelMode;
    public String acceptedCancelReasonCodes;
    public String createdAt;
    public String updatedAt;
  }

  public static class FiscalState {
    public String codigoIbge;
    public String uf;
    public String nome;
    public String regiaoSigla;
    public String regiaoNome;
  }

  public static class FiscalMunicipality {
    public String codigoIbge;
    public String nome;
    public String stateCodigoIbge;
    public String stateUf;
    public String stateNome;
    public String codigoTom;
    public String codigoTomDv;
    public String codigoTomComDv;
  }

  public static class TomadorLookupAddress {
    public String street;
    public String number;
    public String complement;
    public String neighborhood;
    public String city;
    public String state;
    public String zipCode;
  }

  public static class TomadorLookupResponse {
    public String document;
    public String name;
    public String tradeName;
    public String email;
    public String phone;
    public String source;
    public String status;
    public Boolean active;
    public TomadorLookupAddress address;
  }
}
