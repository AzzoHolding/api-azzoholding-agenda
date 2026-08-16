package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalApuracaoMensalEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalTaxConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalApuracaoMensalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalTaxConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;

/**
 * Espelha {@code modules/fiscal/infrastructure/fiscal/FiscalPersistenceService.java}.
 *
 * <p>Camada de persistencia local do modulo fiscal: espelha em {@code fiscal_invoices} /
 * {@code fiscal_tax_configs} / {@code fiscal_apuracao_monthly} o que o {@link FiscalProvider}
 * (mock ou real) devolve, alem de administrar a reserva de numeracao sequencial e a guarda do XML
 * assinado (cifrado via {@link EncryptionService}).
 *
 * <p><b>Adaptacao a armadilha 3 (Panache grava na hora, Spring Data adia ate o commit):</b>
 * {@link #reservarNumeroFiscal} le a mesma entidade que {@link #upsertInvoice} acabou de gravar
 * dentro da mesma transacao (via {@code ServicoFiscal.autorizarInvoice}) — no Panache original o
 * {@code persist()} de {@code upsertInvoice} ja tinha efeito visivel na hora; aqui o insert so
 * some com o commit. Por isso {@link #upsertInvoice} e {@link #upsertApuracao} usam
 * {@code saveAndFlush} quando estao inserindo uma linha nova (o {@code entity.getId() == null}
 * do original vira {@code entity.getId() == null} antes do save, e o flush garante que a leitura
 * seguinte por {@code findByTenantAndExternalInvoiceIdForUpdate}/{@code findByTenantAnoMes} na
 * mesma transacao enxergue a linha). Atualizacao de linha ja existente nao precisa de flush porque
 * o JPA rastreia a entidade gerenciada e as leituras subsequentes por outro id continuam batendo
 * nela via first-level cache.
 */
@Service
public class FiscalPersistenceService {

  private final FiscalTaxConfigRepository fiscalTaxConfigRepository;
  private final FiscalInvoiceRepository fiscalInvoiceRepository;
  private final FiscalApuracaoMensalRepository fiscalApuracaoMensalRepository;
  private final FiscalSequenceService fiscalSequenceService;
  private final EncryptionService encryptionService;
  private final ObjectMapper objectMapper;

  public FiscalPersistenceService(
      FiscalTaxConfigRepository fiscalTaxConfigRepository,
      FiscalInvoiceRepository fiscalInvoiceRepository,
      FiscalApuracaoMensalRepository fiscalApuracaoMensalRepository,
      FiscalSequenceService fiscalSequenceService,
      EncryptionService encryptionService,
      ObjectMapper objectMapper) {
    this.fiscalTaxConfigRepository = fiscalTaxConfigRepository;
    this.fiscalInvoiceRepository = fiscalInvoiceRepository;
    this.fiscalApuracaoMensalRepository = fiscalApuracaoMensalRepository;
    this.fiscalSequenceService = fiscalSequenceService;
    this.encryptionService = encryptionService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void upsertTaxConfig(UUID tenantId, FiscalDtos.TaxConfig dto) {
    if (tenantId == null || dto == null) return;
    boolean nova = false;
    FiscalTaxConfigEntity entity = fiscalTaxConfigRepository.findByTenantId(tenantId).orElse(null);
    if (entity == null) {
      entity = new FiscalTaxConfigEntity();
      nova = true;
    }
    entity.setTenantId(tenantId);
    entity.setRegime(dto.regime);
    entity.setIcmsRate(dto.icmsRate);
    entity.setPisRate(dto.pisRate);
    entity.setCofinsRate(dto.cofinsRate);
    entity.setIssuerRazaoSocial(dto.issuerRazaoSocial);
    entity.setIssuerNomeFantasia(dto.issuerNomeFantasia);
    entity.setIssuerCnpj(dto.issuerCnpj);
    entity.setIssuerIe(dto.issuerIe);
    entity.setIssuerIm(dto.issuerIm);
    entity.setIssuerPhone(dto.issuerPhone);
    entity.setIssuerEmail(dto.issuerEmail);
    entity.setIssuerStreet(dto.issuerStreet);
    entity.setIssuerNumber(dto.issuerNumber);
    entity.setIssuerComplement(dto.issuerComplement);
    entity.setIssuerNeighborhood(dto.issuerNeighborhood);
    entity.setIssuerCity(dto.issuerCity);
    entity.setIssuerState(dto.issuerState);
    entity.setIssuerZipCode(dto.issuerZipCode);
    entity.setIssuerUfCode(dto.issuerUfCode);
    entity.setNfceCscHomologation(dto.nfceCscHomologation);
    entity.setNfceCscIdTokenHomologation(dto.nfceCscIdTokenHomologation);
    entity.setNfceCscProduction(dto.nfceCscProduction);
    entity.setNfceCscIdTokenProduction(dto.nfceCscIdTokenProduction);
    if (nova) fiscalTaxConfigRepository.save(entity);
  }

  @Transactional
  public void upsertInvoice(UUID tenantId, FiscalDtos.Invoice dto) {
    if (tenantId == null || dto == null || dto.id == null || dto.id.isBlank()) return;
    boolean nova = false;
    FiscalInvoiceEntity entity =
        fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, dto.id).orElse(null);
    if (entity == null) {
      entity = new FiscalInvoiceEntity();
      nova = true;
    }
    entity.setTenantId(tenantId);
    entity.setExternalInvoiceId(dto.id);
    entity.setAppointmentId(parseUuid(dto.appointmentId));
    entity.setInvoiceType(dto.type);
    entity.setModelo(resolveModelo(dto.type));
    entity.setAmbiente(
        entity.getAmbiente() == null || entity.getAmbiente().isBlank()
            ? "HOMOLOGACAO"
            : entity.getAmbiente());
    entity.setStatus(normalizeStatus(dto.status, dto.authorizationProtocol));
    entity.setChaveAcesso(dto.accessKey);
    entity.setProtocoloAutorizacao(dto.authorizationProtocol);
    entity.setDataEmissao(DataUtil.parseDataISO(dto.createdAt));
    entity.setTotalAmount(dto.totalAmount);
    entity.setPayloadJson(toJson(dto));
    if (dto.signedXml != null && !dto.signedXml.isBlank()) {
      entity.setXmlSignedEnc(encryptionService.encrypt(dto.signedXml));
    }
    if (dto.serieNf != null && dto.serieNf.matches("^[0-9]+$")) {
      entity.setSerie(Integer.parseInt(dto.serieNf));
    }
    if (dto.numeroNf != null && dto.numeroNf.matches("^[0-9]+$")) {
      entity.setNumero(Integer.parseInt(dto.numeroNf));
    }
    if (nova) {
      fiscalInvoiceRepository.saveAndFlush(entity);
    }
  }

  @Transactional
  public String reservarNumeroFiscal(
      UUID tenantId, String externalInvoiceId, String modelo, int serie, String ambiente) {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio");
    if (externalInvoiceId == null || externalInvoiceId.isBlank()) {
      throw new IllegalArgumentException("externalInvoiceId obrigatorio");
    }
    if (modelo == null || modelo.isBlank()) throw new IllegalArgumentException("modelo obrigatorio");
    if (ambiente == null || ambiente.isBlank()) {
      throw new IllegalArgumentException("ambiente obrigatorio");
    }
    if (serie <= 0) throw new IllegalArgumentException("serie deve ser maior que zero");

    FiscalInvoiceEntity entity =
        fiscalInvoiceRepository
            .findByTenantAndExternalInvoiceIdForUpdate(tenantId, externalInvoiceId)
            .orElseThrow(
                () -> new IllegalArgumentException("Invoice nao encontrada para reserva de numeracao fiscal"));

    if (entity.getNumeroNf() != null && !entity.getNumeroNf().isBlank()) {
      return entity.getNumeroNf();
    }

    int numero = fiscalSequenceService.nextNumber(tenantId, modelo, serie, ambiente);
    while (fiscalInvoiceRepository.existsByFiscalNumber(
        tenantId, modelo, serie, numero, ambiente, externalInvoiceId)) {
      numero = fiscalSequenceService.nextNumber(tenantId, modelo, serie, ambiente);
    }
    entity.setNumeroNf(String.valueOf(numero));
    entity.setSerieNf(String.valueOf(serie));
    entity.setModelo(modelo);
    entity.setSerie(serie);
    entity.setNumero(numero);
    entity.setAmbiente(ambiente);
    return entity.getNumeroNf();
  }

  @Transactional
  public void atualizarStatusInvoice(UUID tenantId, String externalInvoiceId, String status) {
    if (tenantId == null) return;
    if (externalInvoiceId == null || externalInvoiceId.isBlank()) return;
    if (status == null || status.isBlank()) return;
    fiscalInvoiceRepository
        .findByTenantAndExternalInvoiceIdForUpdate(tenantId, externalInvoiceId)
        .ifPresent(entity -> entity.setStatus(status));
  }

  @Transactional(readOnly = true)
  public String obterNumeroFiscalReservado(UUID tenantId, String externalInvoiceId) {
    if (tenantId == null || externalInvoiceId == null || externalInvoiceId.isBlank()) return null;
    return fiscalInvoiceRepository
        .findByTenantAndExternalInvoiceId(tenantId, externalInvoiceId)
        .map(FiscalInvoiceEntity::getNumeroNf)
        .filter(numero -> numero != null && !numero.isBlank())
        .orElse(null);
  }

  @Transactional
  public void salvarXmlAssinado(UUID tenantId, String externalInvoiceId, String signedXml) {
    if (tenantId == null || externalInvoiceId == null || externalInvoiceId.isBlank()) return;
    if (signedXml == null || signedXml.isBlank()) return;
    fiscalInvoiceRepository
        .findByTenantAndExternalInvoiceIdForUpdate(tenantId, externalInvoiceId)
        .ifPresent(entity -> entity.setXmlSignedEnc(encryptionService.encrypt(signedXml)));
  }

  @Transactional
  public void upsertInvoices(UUID tenantId, List<FiscalDtos.Invoice> invoices) {
    if (invoices == null || invoices.isEmpty()) return;
    for (FiscalDtos.Invoice invoice : invoices) {
      upsertInvoice(tenantId, invoice);
    }
  }

  @Transactional
  public void upsertApuracao(UUID tenantId, FiscalDtos.ApuracaoMensal dto) {
    if (tenantId == null || dto == null) return;
    boolean nova = false;
    FiscalApuracaoMensalEntity entity =
        fiscalApuracaoMensalRepository.findByTenantAnoMes(tenantId, dto.ano, dto.mes).orElse(null);
    if (entity == null) {
      entity = new FiscalApuracaoMensalEntity();
      nova = true;
    }
    entity.setTenantId(tenantId);
    entity.setAno(dto.ano);
    entity.setMes(dto.mes);
    entity.setTotalServicos(dto.totalServicos);
    entity.setTotalImpostos(dto.totalImpostos);
    entity.setTotalDocumentos(dto.totalDocumentos);
    entity.setDocumentosJson(toJson(dto.documentos));
    if (nova) fiscalApuracaoMensalRepository.saveAndFlush(entity);
  }

  private UUID parseUuid(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private String toJson(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return null;
    }
  }

  private String normalizeStatus(String status, String authorizationProtocol) {
    if (status == null || status.isBlank()) return status;
    if ("AUTHORIZED".equalsIgnoreCase(status)
        && (authorizationProtocol == null || authorizationProtocol.isBlank())) {
      return "SUBMITTED";
    }
    return status;
  }

  private String resolveModelo(String type) {
    if (type == null || type.isBlank()) return "55";
    String normalized = type.trim().toUpperCase(Locale.ROOT);
    if ("NFCE".equals(normalized) || "65".equals(normalized)) return "65";
    return "55";
  }
}
