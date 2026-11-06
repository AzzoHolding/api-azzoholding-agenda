package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;

/**
 * Apuracao mensal do salao a partir das NFS-e (06/11/2026, pedido do dono).
 *
 * <p>Antes, a apuracao vinha do simulador de nota de produto ({@code MockFiscalProvider}): somava NF-e
 * simuladas (nenhuma em producao) e aplicava ICMS + PIS + COFINS, que o salao nao recolhe. O salao so
 * presta servico; o documento dele e a NFS-e, e o imposto da nota e o ISS.
 *
 * <p>Regras:
 *
 * <ul>
 *   <li><b>Mes pela data de competencia</b> da nota, que e a que define o mes do ISS.
 *   <li><b>So notas de producao.</b> Homologacao e teste, sem validade fiscal.
 *   <li><b>So notas validas:</b> autorizadas, e as com cancelamento pedido ou recusado (seguem
 *       validas na prefeitura). Canceladas, rejeitadas e rascunhos ficam fora.
 *   <li><b>Impostos = ISS que o salao paga</b>: o das notas sem retencao. O ISS retido e recolhido
 *       pelo tomador. Para quem apura o ISS pelo Simples, o valor e uma estimativa: o ISS entra no
 *       DAS, pela aliquota do Simples.
 *   <li>Valores em centavos, como o resto deste DTO.
 * </ul>
 */
@Service
public class ApuracaoMensalDaNfse {

  private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
  static final List<NfseFiscalStatus> VALIDAS =
      List.of(NfseFiscalStatus.AUTHORIZED, NfseFiscalStatus.CANCEL_PENDING, NfseFiscalStatus.CANCEL_REJECTED);

  private final NfseInvoiceRepository nfseInvoiceRepository;

  public ApuracaoMensalDaNfse(NfseInvoiceRepository nfseInvoiceRepository) {
    this.nfseInvoiceRepository = nfseInvoiceRepository;
  }

  @Transactional(readOnly = true)
  public FiscalDtos.ApuracaoMensal doMes(UUID tenantId, int ano, int mes) {
    if (mes < 1 || mes > 12) throw new IllegalArgumentException("Mês inválido.");
    YearMonth periodo = YearMonth.of(ano, mes);
    List<NfseInvoiceEntity> notas =
        nfseInvoiceRepository.listForAccountingExport(tenantId, periodo.atDay(1), periodo.atEndOfMonth(), VALIDAS)
            .stream()
            .filter(nota -> nota.getAmbiente() == NfseAmbiente.PRODUCAO)
            .toList();

    FiscalDtos.ApuracaoMensal apuracao = new FiscalDtos.ApuracaoMensal();
    apuracao.ano = ano;
    apuracao.mes = mes;
    for (NfseInvoiceEntity nota : notas) {
      apuracao.totalServicos += centavos(nota.getValorServicos());
      if (!nota.isIssRetido()) apuracao.totalImpostos += centavos(nota.getValorIss());
      apuracao.documentos.add(documento(nota));
    }
    apuracao.totalDocumentos = notas.size();
    return apuracao;
  }

  public FiscalDtos.ApuracaoMensal doMesAtual(UUID tenantId) {
    YearMonth atual = YearMonth.now(ZONA);
    return doMes(tenantId, atual.getYear(), atual.getMonthValue());
  }

  public List<FiscalDtos.ApuracaoResumo> historico(UUID tenantId, int limite) {
    int quantos = limite < 1 ? 12 : Math.min(limite, 60);
    YearMonth base = YearMonth.now(ZONA);
    List<FiscalDtos.ApuracaoResumo> meses = new ArrayList<>();
    for (int i = 0; i < quantos; i++) {
      YearMonth periodo = base.minusMonths(i);
      meses.add(resumo(doMes(tenantId, periodo.getYear(), periodo.getMonthValue())));
    }
    return meses;
  }

  public FiscalDtos.ResumoAnual resumoAnual(UUID tenantId, int ano) {
    FiscalDtos.ResumoAnual anual = new FiscalDtos.ResumoAnual();
    for (int mes = 1; mes <= 12; mes++) {
      FiscalDtos.ApuracaoResumo item = resumo(doMes(tenantId, ano, mes));
      anual.meses.add(item);
      anual.totalServicos += item.totalServicos;
      anual.totalImpostos += item.totalImpostos;
      anual.totalDocumentos += item.totalDocumentos;
    }
    return anual;
  }

  private FiscalDtos.ApuracaoResumo resumo(FiscalDtos.ApuracaoMensal mensal) {
    FiscalDtos.ApuracaoResumo resumo = new FiscalDtos.ApuracaoResumo();
    resumo.ano = mensal.ano;
    resumo.mes = mensal.mes;
    resumo.totalServicos = mensal.totalServicos;
    resumo.totalImpostos = mensal.totalImpostos;
    resumo.totalDocumentos = mensal.totalDocumentos;
    return resumo;
  }

  private FiscalDtos.Invoice documento(NfseInvoiceEntity nota) {
    FiscalDtos.Invoice documento = new FiscalDtos.Invoice();
    documento.id = nota.getId() != null ? nota.getId().toString() : null;
    documento.type = "NFSE";
    documento.status = nota.getFiscalStatus() != null ? nota.getFiscalStatus().name() : null;
    documento.numeroNf = nota.getNumeroNfse();
    documento.serieNf = nota.getSerieRps();
    documento.accessKey = nota.getChaveAcessoNfse();
    documento.operationNature = nota.getNaturezaOperacao();
    LocalDate competencia = nota.getDataCompetencia();
    documento.createdAt = nota.getDataEmissao() != null
        ? nota.getDataEmissao().toString()
        : competencia != null ? competencia.toString() : null;
    documento.totalAmount = centavos(nota.getValorServicos());
    FiscalDtos.InvoiceCustomer tomador = new FiscalDtos.InvoiceCustomer();
    tomador.name = nota.getCustomerName();
    tomador.document = nota.getCustomerDocument();
    documento.customer = tomador;
    return documento;
  }

  static long centavos(BigDecimal valor) {
    if (valor == null) return 0;
    return valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
  }
}
