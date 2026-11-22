package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ValeDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ComandaItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.MovimentoDeVale;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ValePresente;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MovimentoDeValeRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ValePresenteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.CodigoAleatorio;

/**
 * Vales-presente (item 6; PLANO-CUPONS-VALES.md).
 *
 * <p>Decisoes do dono (17/11/2026): receita no dia da VENDA (como pacote); pagar com vale nao gera receita de novo
 * nem entra no esperado do caixa; validade padrao de 12 meses; usa quem tem o codigo; uso em partes.
 *
 * <p>O saldo so muda por instrucao condicional ({@link ValePresenteRepository#debitarSeHouver}): dois pagamentos
 * ao mesmo tempo nao gastam o mesmo saldo. Todo movimento vai para o extrato ({@code movimentos_de_vale}).
 */
@Service
public class ServicoDeVales {

  static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  public static final int TAMANHO_DO_CODIGO = 10;
  public static final int VALIDADE_PADRAO_MESES = 12;
  public static final BigDecimal VALOR_MINIMO = new BigDecimal("10.00");
  public static final BigDecimal VALOR_MAXIMO = new BigDecimal("5000.00");
  /** Vale vencido que recebe devolucao (cancelamento ou estorno da comanda) passa a valer ate hoje + 30 dias. */
  static final int DIAS_APOS_DEVOLUCAO = 30;

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final ValePresenteRepository valeRepository;
  private final MovimentoDeValeRepository movimentoRepository;
  private final ClienteRepository clienteRepository;
  private final AuditService auditService;

  public ServicoDeVales(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      ValePresenteRepository valeRepository,
      MovimentoDeValeRepository movimentoRepository,
      ClienteRepository clienteRepository,
      AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.valeRepository = valeRepository;
    this.movimentoRepository = movimentoRepository;
    this.clienteRepository = clienteRepository;
    this.auditService = auditService;
  }

  static LocalDate hoje() {
    return LocalDate.now(ZONA_BR);
  }

  /** O valor do vale vendido: de R$ 10 a R$ 5.000, em centavos. */
  public static void exigirValorDeVenda(BigDecimal valor) {
    if (valor == null || valor.compareTo(VALOR_MINIMO) < 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
      throw new IllegalArgumentException("O vale-presente vai de R$ 10,00 a R$ 5.000,00.");
    }
    if (valor.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("Valor do vale inválido: use no máximo 2 casas decimais.");
    }
  }

  /** Validade em meses do vale vendido: 1 a 60 (padrao 12). */
  public static int validadeEmMeses(Integer pedido) {
    if (pedido == null) return VALIDADE_PADRAO_MESES;
    if (pedido < 1 || pedido > 60) throw new IllegalArgumentException("A validade do vale vai de 1 a 60 meses.");
    return pedido;
  }

  // ─── Venda (no fechamento da comanda) ──────────────────────────────────────

  /** Cria o vale de um item VALE da comanda que esta fechando. O codigo e sorteado e unico no salao. */
  @Transactional
  public ValePresente criarDaVenda(UUID tenantId, UUID comandaId, UUID compradorId, ComandaItem item, BigDecimal valor) {
    ValePresente vale = new ValePresente();
    vale.setTenantId(tenantId);
    vale.setCodigo(codigoNovo(tenantId));
    vale.setValorInicial(valor);
    vale.setSaldo(valor);
    vale.setExpiraEm(hoje().plusMonths(validadeEmMeses(item.getValeValidadeMeses())));
    vale.setCompradorClientId(compradorId);
    vale.setDestinatarioNome(item.getValeDestinatario());
    vale.setMensagem(item.getValeMensagem());
    vale.setComandaVendaId(comandaId);
    vale.setComandaItemId(item.getId());
    vale.setCreatedBy(authenticatedUser.idOuNulo());
    valeRepository.saveAndFlush(vale);
    registrar(vale, MovimentoDeVale.VENDA, valor, comandaId, "Venda do vale-presente");
    return vale;
  }

  /** O documento do salao, para o prefixo do codigo do vale. Por setter: o construtor nao muda. */
  private br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository tenantRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setTenantRepository(br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  private String documentoDoSalao(UUID tenantId) {
    return tenantRepository == null ? null
        : tenantRepository.findById(tenantId).map(t -> t.getDocument()).orElse(null);
  }

  /**
   * 10 caracteres (pedido do dono, 17/11): o hash de um UUID novo (a semente) com o documento do salao, cortado em
   * 10. O documento ja entra no hash, entao o vale nao leva os 6 digitos do salao na frente (o cupom de lote leva).
   */
  private String codigoNovo(UUID tenantId) {
    String documento = documentoDoSalao(tenantId);
    for (int i = 0; i < 50; i++) {
      String codigo = CodigoAleatorio.deHash(UUID.randomUUID(), documento, TAMANHO_DO_CODIGO);
      if (!valeRepository.existsByTenantIdAndCodigo(tenantId, codigo)) return codigo;
    }
    throw new IllegalStateException("Não foi possível gerar um código de vale único.");
  }

  /** Estorno da venda: so se o vale nao foi usado. O vale e cancelado. */
  @Transactional
  public void cancelarDaVenda(UUID tenantId, UUID comandaId, String motivo) {
    for (ValePresente vale : valeRepository.findByTenantIdAndComandaVendaId(tenantId, comandaId)) {
      if (movimentoRepository.existsByValeIdAndTipo(vale.getId(), MovimentoDeVale.USO)) {
        throw new IllegalArgumentException("O vale-presente " + vale.getCodigo()
            + " desta venda já foi usado: a venda não pode ser estornada.");
      }
      BigDecimal saldo = valeRepository.saldoAtual(vale.getId());
      valeRepository.mudarStatus(vale.getId(), tenantId, ValePresente.STATUS_CANCELADO);
      registrarComSaldo(vale.getTenantId(), vale.getId(), MovimentoDeVale.ESTORNO, saldo.negate(), BigDecimal.ZERO,
          comandaId, "Estorno da venda: " + motivo);
    }
  }

  /** O codigo do vale (para mostrar no pagamento da comanda). */
  @Transactional(readOnly = true)
  public String codigoDoVale(UUID valeId) {
    return valeId == null ? null : valeRepository.findById(valeId).map(ValePresente::getCodigo).orElse(null);
  }

  /** Os vales criados por uma comanda (para mostrar o codigo depois de fechar). */
  @Transactional(readOnly = true)
  public List<ValePresente> daVenda(UUID tenantId, UUID comandaId) {
    return valeRepository.findByTenantIdAndComandaVendaId(tenantId, comandaId);
  }

  // ─── Uso (pagamento da comanda) ────────────────────────────────────────────

  /** O vale pelo codigo, para o caixa conferir antes de usar. */
  @Transactional(readOnly = true)
  public ValePresente buscarPorCodigo(UUID tenantId, String codigo) {
    String c = CodigoAleatorio.normalizar(codigo).replace("-", "").replace(" ", "");
    if (c.isEmpty()) throw new IllegalArgumentException("Informe o código do vale-presente.");
    return valeRepository.findByTenantIdAndCodigo(tenantId, c)
        .orElseThrow(() -> new IllegalArgumentException("Vale-presente não encontrado. Confira o código."));
  }

  /** Debita o vale (pagamento da comanda). Recusa em frase quando nao ha saldo, venceu ou nao esta ativo. */
  @Transactional
  public ValePresente debitar(UUID tenantId, String codigo, BigDecimal valor, UUID comandaId) {
    ValePresente vale = buscarPorCodigo(tenantId, codigo);
    if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("Informe o valor a usar do vale.");
    if (valeRepository.debitarSeHouver(vale.getId(), tenantId, valor, hoje()) == 0) {
      BigDecimal saldo = valeRepository.saldoAtual(vale.getId());
      String status = valeRepository.statusAtual(vale.getId());
      if (ValePresente.STATUS_CANCELADO.equals(status)) throw new IllegalArgumentException("Este vale-presente foi cancelado.");
      if (ValePresente.STATUS_VENCIDO.equals(status) || vale.getExpiraEm().isBefore(hoje())) {
        throw new IllegalArgumentException("Este vale-presente venceu em " + ServicoDeCupons.dataBr(vale.getExpiraEm()) + ".");
      }
      if (saldo == null || saldo.signum() <= 0) throw new IllegalArgumentException("Este vale-presente não tem mais saldo.");
      throw new IllegalArgumentException("O vale-presente tem só " + ServicoDeCupons.reais(saldo) + " de saldo.");
    }
    registrar(vale, MovimentoDeVale.USO, valor.negate(), comandaId, "Pagamento de comanda");
    return vale;
  }

  /** Devolve ao vale (cancelamento ou estorno da comanda). Vale vencido volta a valer por 30 dias. */
  @Transactional
  public void devolver(UUID tenantId, UUID valeId, BigDecimal valor, UUID comandaId, String motivo) {
    if (valeId == null || valor == null || valor.signum() <= 0) return;
    int atualizados = valeRepository.devolver(valeId, tenantId, valor, hoje().plusDays(DIAS_APOS_DEVOLUCAO));
    if (atualizados == 0) return; // vale cancelado: nada a devolver
    registrarComSaldo(tenantId, valeId, MovimentoDeVale.DEVOLUCAO, valor, valeRepository.saldoAtual(valeId), comandaId, motivo);
  }

  // ─── Gestao (dono ou perfil com pos:coupons) ───────────────────────────────

  @Transactional(readOnly = true)
  public ValeDtos.ListaDeValesResponse listar(String busca) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String b = busca == null ? "" : CodigoAleatorio.normalizar(busca);
    ValeDtos.ListaDeValesResponse r = new ValeDtos.ListaDeValesResponse();
    Map<UUID, String> nomes = new HashMap<>();
    for (ValePresente v : valeRepository.findTop500ByTenantIdOrderByCreatedAtDesc(tenantId)) {
      if (!b.isEmpty() && !v.getCodigo().contains(b)
          && (v.getDestinatarioNome() == null || !CodigoAleatorio.normalizar(v.getDestinatarioNome()).contains(b))) {
        continue;
      }
      r.vales.add(toResponse(v, nomes, false));
    }
    r.saldoEmAberto = valeRepository.saldoEmAberto(tenantId);
    return r;
  }

  @Transactional(readOnly = true)
  public ValeDtos.ValeResponse consultar(String codigo) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(buscarPorCodigo(tenantId, codigo), new HashMap<>(), true);
  }

  @Transactional(readOnly = true)
  public ValeDtos.ValeResponse obter(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(valeOuFalhar(tenantId, id), new HashMap<>(), true);
  }

  @Transactional(readOnly = true)
  public List<ValeDtos.ValeResponse> doCliente(UUID clienteId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Map<UUID, String> nomes = new HashMap<>();
    return valeRepository.findByTenantIdAndCompradorClientIdOrderByCreatedAtDesc(tenantId, clienteId).stream()
        .map(v -> toResponse(v, nomes, false)).toList();
  }

  /** Ajuste do saldo: so o dono. */
  @Transactional
  public ValeDtos.ValeResponse ajustar(UUID id, ValeDtos.AjustarRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    exigirDono();
    ValePresente vale = valeOuFalhar(tenantId, id);
    BigDecimal antes = valeRepository.saldoAtual(vale.getId());
    BigDecimal novo = req.saldo.setScale(2, RoundingMode.HALF_UP);
    if (novo.compareTo(vale.getValorInicial()) > 0) {
      throw new IllegalArgumentException("O saldo não pode passar do valor do vale (" + ServicoDeCupons.reais(vale.getValorInicial()) + ").");
    }
    if (valeRepository.ajustarSaldo(vale.getId(), tenantId, novo) == 0) {
      throw new IllegalArgumentException("Só vale ativo ou esgotado pode ter o saldo ajustado.");
    }
    registrarComSaldo(tenantId, vale.getId(), MovimentoDeVale.AJUSTE, novo.subtract(antes), novo, null, req.motivo.trim());
    auditar(tenantId, "GIFT_CARD_ADJUST", vale.getId(), Map.of("saldo", antes), Map.of("saldo", novo, "motivo", req.motivo.trim()));
    return obter(id);
  }

  /** Cancelamento: so o dono. O saldo deixa de valer. */
  @Transactional
  public ValeDtos.ValeResponse cancelar(UUID id, ValeDtos.CancelarRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    exigirDono();
    ValePresente vale = valeOuFalhar(tenantId, id);
    String status = valeRepository.statusAtual(vale.getId());
    if (ValePresente.STATUS_CANCELADO.equals(status)) throw new IllegalArgumentException("Este vale já está cancelado.");
    BigDecimal saldo = valeRepository.saldoAtual(vale.getId());
    valeRepository.mudarStatus(vale.getId(), tenantId, ValePresente.STATUS_CANCELADO);
    registrarComSaldo(tenantId, vale.getId(), MovimentoDeVale.AJUSTE, BigDecimal.ZERO, saldo, null,
        "Cancelado: " + req.motivo.trim());
    auditar(tenantId, "GIFT_CARD_CANCEL", vale.getId(), Map.of("status", status), Map.of("status", "CANCELADO", "motivo", req.motivo.trim()));
    return obter(id);
  }

  /** Nova validade: so o dono. Vale vencido com saldo volta a valer. */
  @Transactional
  public ValeDtos.ValeResponse prorrogar(UUID id, ValeDtos.ProrrogarRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    exigirDono();
    ValePresente vale = valeOuFalhar(tenantId, id);
    LocalDate nova = ServicoDeCupons.data(req.expiraEm, "validade");
    if (nova == null || nova.isBefore(hoje())) throw new IllegalArgumentException("A nova validade precisa ser de hoje em diante.");
    if (valeRepository.prorrogar(vale.getId(), tenantId, nova) == 0) {
      throw new IllegalArgumentException("Só vale ativo ou vencido pode ter a validade mudada.");
    }
    registrarComSaldo(tenantId, vale.getId(), MovimentoDeVale.PRORROGACAO, BigDecimal.ZERO,
        valeRepository.saldoAtual(vale.getId()), null, "Validade até " + ServicoDeCupons.dataBr(nova) + ": " + req.motivo.trim());
    auditar(tenantId, "GIFT_CARD_EXTEND", vale.getId(), Map.of("expiraEm", vale.getExpiraEm().toString()),
        Map.of("expiraEm", nova.toString(), "motivo", req.motivo.trim()));
    return obter(id);
  }

  // ─── Vencimento diario ─────────────────────────────────────────────────────

  /** Marca VENCIDO o vale ativo que passou da validade. O saldo fica registrado no extrato. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int expirarVencidos() {
    int n = 0;
    for (ValePresente vale : valeRepository.vencidos(hoje())) {
      BigDecimal saldo = valeRepository.saldoAtual(vale.getId());
      if (valeRepository.mudarStatus(vale.getId(), vale.getTenantId(), ValePresente.STATUS_VENCIDO) == 0) continue;
      registrarComSaldo(vale.getTenantId(), vale.getId(), MovimentoDeVale.EXPIRACAO, BigDecimal.ZERO, saldo, null,
          "Vale vencido em " + ServicoDeCupons.dataBr(vale.getExpiraEm()) + " com saldo de " + ServicoDeCupons.reais(saldo));
      n++;
    }
    return n;
  }

  // ─── Apoio ─────────────────────────────────────────────────────────────────

  private void exigirDono() {
    if (!authenticatedUser.temRole("OWNER")) {
      throw new ApiClientErrorException("Só o dono do salão ajusta, cancela ou prorroga vale-presente.", 403);
    }
  }

  private ValePresente valeOuFalhar(UUID tenantId, UUID id) {
    return valeRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Vale-presente não encontrado.", 404));
  }

  private void registrar(ValePresente vale, String tipo, BigDecimal valor, UUID comandaId, String motivo) {
    registrarComSaldo(vale.getTenantId(), vale.getId(), tipo, valor, valeRepository.saldoAtual(vale.getId()), comandaId, motivo);
  }

  private void registrarComSaldo(UUID tenantId, UUID valeId, String tipo, BigDecimal valor, BigDecimal saldoApos,
      UUID comandaId, String motivo) {
    MovimentoDeVale m = new MovimentoDeVale();
    m.setTenantId(tenantId);
    m.setValeId(valeId);
    m.setTipo(tipo);
    m.setValor(valor);
    m.setSaldoApos(saldoApos == null ? BigDecimal.ZERO : saldoApos);
    m.setComandaId(comandaId);
    m.setMotivo(motivo == null ? null : motivo.length() > 200 ? motivo.substring(0, 200) : motivo);
    m.setCreatedBy(authenticatedUser.idOuNulo());
    movimentoRepository.save(m);
  }

  private ValeDtos.ValeResponse toResponse(ValePresente v, Map<UUID, String> nomes, boolean comExtrato) {
    ValeDtos.ValeResponse r = new ValeDtos.ValeResponse();
    r.id = v.getId().toString();
    r.codigo = v.getCodigo();
    r.valorInicial = v.getValorInicial();
    // Saldo e status do banco: mudam por instrucao condicional, fora da entidade carregada.
    r.saldo = valeRepository.saldoAtual(v.getId());
    r.status = valeRepository.statusAtual(v.getId());
    r.expiraEm = v.getExpiraEm() == null ? null : v.getExpiraEm().toString();
    r.compradorClientId = v.getCompradorClientId() == null ? null : v.getCompradorClientId().toString();
    if (v.getCompradorClientId() != null) {
      r.compradorNome = nomes.computeIfAbsent(v.getCompradorClientId(),
          id -> clienteRepository.findByIdAndTenantId(id, v.getTenantId()).map(Cliente::getName).orElse(null));
    }
    r.destinatarioNome = v.getDestinatarioNome();
    r.mensagem = v.getMensagem();
    r.comandaVendaId = v.getComandaVendaId() == null ? null : v.getComandaVendaId().toString();
    r.createdAt = v.getCreatedAt() == null ? null : v.getCreatedAt().toString();
    if (comExtrato) {
      for (MovimentoDeVale m : movimentoRepository.findByValeIdOrderByCreatedAtAsc(v.getId())) {
        ValeDtos.MovimentoResponse mr = new ValeDtos.MovimentoResponse();
        mr.tipo = m.getTipo();
        mr.valor = m.getValor();
        mr.saldoApos = m.getSaldoApos();
        mr.comandaId = m.getComandaId() == null ? null : m.getComandaId().toString();
        mr.motivo = m.getMotivo();
        mr.createdAt = m.getCreatedAt() == null ? null : m.getCreatedAt().toString();
        r.extrato.add(mr);
      }
    }
    return r;
  }

  private void auditar(UUID tenantId, String acao, UUID id, Object antes, Object depois) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = acao;
      command.entityType = "GIFT_CARD";
      command.entityId = id == null ? null : id.toString();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = antes;
      command.after = depois;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nunca derruba a operacao.
    }
  }
}
