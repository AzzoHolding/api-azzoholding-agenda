package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PagamentosDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cobranca;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TaxaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CobrancaRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ContaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TaxaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Taxas da operadora por conta e recebiveis (Fase 5 dos pagamentos).
 *
 * <p>O dono informa as faixas; quando a cobranca e paga e a operadora nao mandou a taxa real, a
 * estimativa sai daqui ({@link CalculoDaTaxaDePagamento}). Recebiveis = cobrancas pagas com data
 * prevista, agrupadas por dia e por operadora.
 */
@Service
public class ServicoDeTaxasDePagamento {

  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private static final Set<String> MEIOS = Set.of("PIX", "CREDITO", "DEBITO", "LINK");

  private final TaxaDePagamentoRepository taxas;
  private final ContaDePagamentoRepository contas;
  private final CobrancaRepository cobrancas;
  private final ContextoTenant contextoTenant;
  private final AuditService auditService;

  public ServicoDeTaxasDePagamento(
      TaxaDePagamentoRepository taxas,
      ContaDePagamentoRepository contas,
      CobrancaRepository cobrancas,
      ContextoTenant contextoTenant,
      AuditService auditService) {
    this.taxas = taxas;
    this.contas = contas;
    this.cobrancas = cobrancas;
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<PagamentosDtos.Taxa> listar(UUID contaId) {
    ContaDePagamento conta = doSalao(contaId);
    return taxas.findByContaIdOrderByMeioAscParcelasDeAsc(conta.getId()).stream().map(ServicoDeTaxasDePagamento::paraDto).toList();
  }

  /** Troca todas as faixas da conta de uma vez (a tela edita a tabela inteira). */
  @Transactional
  public List<PagamentosDtos.Taxa> salvar(UUID contaId, List<PagamentosDtos.Taxa> faixas) {
    ContaDePagamento conta = doSalao(contaId);
    List<TaxaDePagamento> novas = new ArrayList<>();
    for (PagamentosDtos.Taxa f : faixas == null ? List.<PagamentosDtos.Taxa>of() : faixas) {
      if (f == null || f.meio == null || !MEIOS.contains(f.meio)) throw new ApiClientErrorException("Meio de pagamento inválido.", 400);
      int de = f.parcelasDe == null ? 1 : f.parcelasDe;
      int ate = f.parcelasAte == null ? de : f.parcelasAte;
      if (de < 1 || ate < de || ate > 24) throw new ApiClientErrorException("Faixa de parcelas inválida: de 1 a 24, e \"até\" não menor que \"de\".", 400);
      if (!"CREDITO".equals(f.meio) && (de != 1 || ate != 1)) {
        throw new ApiClientErrorException("Só o crédito tem parcelas; Pix, débito e link são à vista.", 400);
      }
      BigDecimal percentual = f.percentual == null ? BigDecimal.ZERO : f.percentual;
      BigDecimal fixo = f.valorFixo == null ? BigDecimal.ZERO : f.valorFixo;
      if (percentual.signum() < 0 || percentual.compareTo(BigDecimal.valueOf(100)) >= 0) {
        throw new ApiClientErrorException("O percentual vai de 0 a 99,999%.", 400);
      }
      if (fixo.signum() < 0) throw new ApiClientErrorException("O valor fixo não pode ser negativo.", 400);
      int dias = f.diasParaReceber == null ? 0 : f.diasParaReceber;
      if (dias < 0 || dias > 400) throw new ApiClientErrorException("Dias para receber: de 0 a 400.", 400);
      TaxaDePagamento t = new TaxaDePagamento();
      t.setTenantId(conta.getTenantId());
      t.setContaId(conta.getId());
      t.setMeio(f.meio);
      t.setParcelasDe(de);
      t.setParcelasAte(ate);
      t.setPercentual(percentual.setScale(3, java.math.RoundingMode.HALF_UP));
      t.setValorFixo(fixo.setScale(2, java.math.RoundingMode.HALF_UP));
      t.setDiasParaReceber(dias);
      novas.add(t);
    }
    Optional<String> conflito = CalculoDaTaxaDePagamento.conflito(novas);
    if (conflito.isPresent()) throw new ApiClientErrorException(conflito.get(), 400);
    List<PagamentosDtos.Taxa> antes = taxas.findByContaIdOrderByMeioAscParcelasDeAsc(conta.getId()).stream()
        .map(ServicoDeTaxasDePagamento::paraDto).toList();
    taxas.deleteByContaId(conta.getId());
    taxas.flush();
    taxas.saveAll(novas);
    List<PagamentosDtos.Taxa> depois = novas.stream().map(ServicoDeTaxasDePagamento::paraDto).toList();
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = conta.getTenantId();
    command.module = AuditConstants.Module.SETTINGS;
    command.action = "PAGAMENTO_TAXAS_ALTERADAS";
    command.entityType = "PAYMENT_ACCOUNT";
    command.entityId = conta.getId().toString();
    command.sourceChannel = AuditConstants.SourceChannel.API;
    command.before = Map.of("conta", conta.getApelido(), "faixas", resumo(antes));
    command.after = Map.of("conta", conta.getApelido(), "faixas", resumo(depois));
    auditService.recordSuccess(command);
    return depois;
  }

  /**
   * Preenche taxa, liquido e previsao da cobranca paga quando a operadora nao mandou. Chamado
   * dentro da transacao que aplica o status: nao salva sozinho.
   */
  public void estimarSeFaltar(Cobranca c) {
    if (c.getTaxa() != null && c.getPrevisaoRecebimento() != null) return;
    LocalDate dia = c.getPagaEm() == null ? LocalDate.now(BRASILIA) : c.getPagaEm().atZone(BRASILIA).toLocalDate();
    CalculoDaTaxaDePagamento.estimar(taxas.findByContaIdOrderByMeioAscParcelasDeAsc(c.getContaId()), c.getMeio(),
        c.getParcelas(), c.getValor(), dia).ifPresent(e -> {
          if (c.getTaxa() == null) {
            c.setTaxa(e.taxa());
            c.setValorLiquido(e.liquido());
          } else if (c.getValorLiquido() == null) {
            c.setValorLiquido(c.getValor().subtract(c.getTaxa()));
          }
          if (c.getPrevisaoRecebimento() == null) c.setPrevisaoRecebimento(e.previsao());
        });
  }

  /** O que foi pago e cai entre {@code de} e {@code ate}, por dia, com o total por operadora. */
  @Transactional(readOnly = true)
  public PagamentosDtos.Recebiveis recebiveis(LocalDate de, LocalDate ate) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (de == null) de = LocalDate.now(BRASILIA);
    if (ate == null) ate = de.plusDays(30);
    if (ate.isBefore(de)) throw new ApiClientErrorException("A data final é anterior à inicial.", 400);
    if (de.plusDays(400).isBefore(ate)) throw new ApiClientErrorException("Período máximo: 400 dias.", 400);
    Map<UUID, String> nomes = new LinkedHashMap<>();
    contas.findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(tenantId).forEach(c -> nomes.put(c.getId(), c.getApelido()));
    PagamentosDtos.Recebiveis r = new PagamentosDtos.Recebiveis();
    r.de = de.toString();
    r.ate = ate.toString();
    Map<String, PagamentosDtos.TotalDaOperadora> porConta = new LinkedHashMap<>();
    for (Cobranca c : cobrancas.recebiveis(tenantId, de, ate)) {
      PagamentosDtos.Recebivel item = new PagamentosDtos.Recebivel();
      item.cobrancaId = c.getId().toString();
      item.data = c.getPrevisaoRecebimento().toString();
      item.conta = nomes.getOrDefault(c.getContaId(), c.getProvedor());
      item.provedor = c.getProvedor();
      item.meio = c.getMeio();
      item.parcelas = c.getParcelas();
      item.bruto = c.getValor().subtract(c.getValorEstornado() == null ? BigDecimal.ZERO : c.getValorEstornado());
      item.taxa = c.getTaxa() == null ? BigDecimal.ZERO : c.getTaxa();
      item.liquido = c.getValorLiquido() == null ? item.bruto.subtract(item.taxa) : c.getValorLiquido()
          .subtract(c.getValorEstornado() == null ? BigDecimal.ZERO : c.getValorEstornado()).max(BigDecimal.ZERO);
      item.origem = c.getOrigemTipo();
      r.itens.add(item);
      r.bruto = r.bruto.add(item.bruto);
      r.taxa = r.taxa.add(item.taxa);
      r.liquido = r.liquido.add(item.liquido);
      PagamentosDtos.TotalDaOperadora t = porConta.computeIfAbsent(item.conta, k -> {
        PagamentosDtos.TotalDaOperadora novo = new PagamentosDtos.TotalDaOperadora();
        novo.conta = k;
        return novo;
      });
      t.bruto = t.bruto.add(item.bruto);
      t.taxa = t.taxa.add(item.taxa);
      t.liquido = t.liquido.add(item.liquido);
      t.quantidade++;
    }
    r.porOperadora.addAll(porConta.values());
    r.semPrevisao = cobrancas.pagasSemPrevisao(tenantId);
    return r;
  }

  private ContaDePagamento doSalao(UUID contaId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return contas.findById(contaId).filter(c -> c.getTenantId().equals(tenantId))
        .orElseThrow(() -> new ApiClientErrorException("Conta de pagamento não encontrada.", 404));
  }

  private static List<String> resumo(List<PagamentosDtos.Taxa> faixas) {
    return faixas.stream().map(f -> f.meio + " " + f.parcelasDe + "-" + f.parcelasAte + "x: " + f.percentual.stripTrailingZeros().toPlainString()
        + "% + R$ " + f.valorFixo + ", " + f.diasParaReceber + " dias").toList();
  }

  private static PagamentosDtos.Taxa paraDto(TaxaDePagamento t) {
    PagamentosDtos.Taxa d = new PagamentosDtos.Taxa();
    d.meio = t.getMeio();
    d.parcelasDe = t.getParcelasDe();
    d.parcelasAte = t.getParcelasAte();
    d.percentual = t.getPercentual();
    d.valorFixo = t.getValorFixo();
    d.diasParaReceber = t.getDiasParaReceber();
    return d;
  }
}
