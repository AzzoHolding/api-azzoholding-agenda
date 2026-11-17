package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CupomDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cupom;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LoteDeCupom;
import br.com.phdigitalcode.azzo.agenda.pro.entity.UsoDeCupom;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CupomRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LoteDeCupomRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsoDeCupomRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.CodigoAleatorio;

/**
 * Cupons de desconto e o gerador de lotes (item 6; PLANO-CUPONS-VALES.md).
 *
 * <p>Decisoes do dono (17/11/2026): cupom no PDV e no agendamento online; % ou R$; inicio e fim; limite total;
 * limite por cliente; valor minimo. O gerador cria N codigos de uso unico ({@code limite_total = 1}) para
 * acompanhar quais foram usados.
 *
 * <p>O uso e RESERVADO quando o cupom e aplicado (na comanda ou no agendamento): o limite e conferido numa
 * unica instrucao ({@link CupomRepository#reservarUso}) e o limite por cliente sob trava da linha do cupom —
 * duas comandas ao mesmo tempo nao passam do limite. Cancelar, estornar ou remover o cupom libera o uso.
 */
@Service
public class ServicoDeCupons {

  static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  static final int TAMANHO_DO_CODIGO_DO_LOTE = 6;

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final CupomRepository cupomRepository;
  private final LoteDeCupomRepository loteRepository;
  private final UsoDeCupomRepository usoRepository;
  private final ClienteRepository clienteRepository;
  private final AuditService auditService;

  /** Nomes dos servicos da lista do cupom (V182). Por setter: o construtor nao muda. */
  private ServicoRepository servicoRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setServicoRepository(ServicoRepository servicoRepository) {
    this.servicoRepository = servicoRepository;
  }

  /** A opcao "mais de um cupom por comanda" (V183). Por setter: o construtor nao muda. */
  private br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository configuracoes;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setConfiguracoes(
      br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository configuracoes) {
    this.configuracoes = configuracoes;
  }

  /** O documento do salao, para o prefixo dos codigos gerados. Por setter: o construtor nao muda. */
  private br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository tenantRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setTenantRepository(br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  /** Os 6 digitos do salao (hash do CPF/CNPJ) no inicio dos codigos do gerador. */
  public String prefixoDoSalao(UUID tenantId) {
    String documento = tenantRepository == null ? null
        : tenantRepository.findById(tenantId).map(t -> t.getDocument()).orElse(null);
    return CodigoAleatorio.prefixoDoSalao(documento, tenantId);
  }

  /** A comanda aceita mais de um cupom? Padrao: nao. */
  public boolean permiteVariosPorComanda(UUID tenantId) {
    return configuracoes != null && configuracoes.findByTenantIdOrCreate(tenantId).isPosVariosCuponsPorComanda();
  }

  @Transactional
  public CupomDtos.ConfiguracaoDeCupons configuracao() {
    CupomDtos.ConfiguracaoDeCupons r = new CupomDtos.ConfiguracaoDeCupons();
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    r.variosPorComanda = permiteVariosPorComanda(tenantId);
    r.prefixoDoSalao = prefixoDoSalao(tenantId);
    return r;
  }

  @Transactional
  public CupomDtos.ConfiguracaoDeCupons salvarConfiguracao(CupomDtos.ConfiguracaoDeCupons req) {
    if (req == null || req.variosPorComanda == null) {
      throw new IllegalArgumentException("Informe se a comanda aceita mais de um cupom (variosPorComanda).");
    }
    if (configuracoes == null) throw new IllegalStateException("Configuração indisponível.");
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    var entidade = configuracoes.findByTenantIdOrCreate(tenantId);
    boolean antes = entidade.isPosVariosCuponsPorComanda();
    if (antes != req.variosPorComanda) {
      entidade.setPosVariosCuponsPorComanda(req.variosPorComanda);
      configuracoes.save(entidade);
      auditar(tenantId, "COUPON_SETTINGS_UPDATE", "COUPON_SETTINGS", tenantId,
          Map.of("variosPorComanda", antes), Map.of("variosPorComanda", req.variosPorComanda));
    }
    return configuracao();
  }

  public ServicoDeCupons(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      CupomRepository cupomRepository,
      LoteDeCupomRepository loteRepository,
      UsoDeCupomRepository usoRepository,
      ClienteRepository clienteRepository,
      AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.cupomRepository = cupomRepository;
    this.loteRepository = loteRepository;
    this.usoRepository = usoRepository;
    this.clienteRepository = clienteRepository;
    this.auditService = auditService;
  }

  // ─── Regras puras ──────────────────────────────────────────────────────────

  /** O desconto do cupom sobre uma base: % arredondado em centavos, ou R$ ate a base. Nunca passa da base. */
  public static BigDecimal desconto(String tipo, BigDecimal valor, BigDecimal base) {
    if (base == null || base.signum() <= 0 || valor == null) return BigDecimal.ZERO.setScale(2);
    BigDecimal d = Cupom.TIPO_PERCENTUAL.equals(tipo)
        ? base.multiply(valor).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
        : valor.setScale(2, RoundingMode.HALF_UP);
    return d.min(base).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal desconto(Cupom cupom, BigDecimal base) {
    return desconto(cupom.getTipo(), cupom.getValor(), base);
  }

  // ─── So alguns servicos (V182) ─────────────────────────────────────────────

  /** Os servicos que ganham o desconto. Vazio = a conta toda. O codigo de lote segue a lista do lote. */
  public java.util.Set<UUID> servicosDoCupom(Cupom c) {
    List<UUID> ids = c.getLoteId() != null
        ? cupomRepository.servicosDoLote(c.getLoteId())
        : cupomRepository.servicosDoCupom(c.getId());
    return new java.util.LinkedHashSet<>(ids);
  }

  /**
   * Sobre quanto o cupom desconta: a conta toda, ou so o valor dos servicos da lista.
   *
   * @param valorPorServico quanto cada servico soma na conta (servico -> valor)
   */
  public static BigDecimal baseElegivel(
      java.util.Set<UUID> servicos, BigDecimal baseTotal, Map<UUID, BigDecimal> valorPorServico) {
    if (servicos == null || servicos.isEmpty()) return baseTotal == null ? BigDecimal.ZERO : baseTotal;
    BigDecimal soma = BigDecimal.ZERO;
    for (UUID id : servicos) {
      BigDecimal v = valorPorServico == null ? null : valorPorServico.get(id);
      if (v != null) soma = soma.add(v);
    }
    return soma.setScale(2, RoundingMode.HALF_UP);
  }

  /** Recusa o cupom de lista quando a conta nao tem nenhum servico dela: diz quais servicos sao. */
  void exigirServicoDaLista(UUID tenantId, Cupom c, java.util.Set<UUID> servicos, BigDecimal elegivel) {
    if (servicos.isEmpty() || elegivel.signum() > 0) return;
    throw new IllegalArgumentException("Este cupom vale só para: " + String.join(", ", nomes(tenantId, servicos)) + ".");
  }

  private List<String> nomes(UUID tenantId, java.util.Collection<UUID> ids) {
    if (servicoRepository == null || ids.isEmpty()) return List.of();
    return servicoRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
        .map(br.com.phdigitalcode.azzo.agenda.pro.entity.Servico::getName).sorted().toList();
  }

  private List<CupomDtos.ServicoDoCupom> servicosParaTela(UUID tenantId, java.util.Collection<UUID> ids) {
    if (servicoRepository == null || ids.isEmpty()) return new ArrayList<>();
    List<CupomDtos.ServicoDoCupom> lista = new ArrayList<>();
    for (var s : servicoRepository.findByTenantIdAndIdIn(tenantId, ids)) {
      CupomDtos.ServicoDoCupom r = new CupomDtos.ServicoDoCupom();
      r.id = s.getId().toString();
      r.nome = s.getName();
      lista.add(r);
    }
    lista.sort(java.util.Comparator.comparing(r -> r.nome == null ? "" : r.nome));
    return lista;
  }

  /** Os servicos pedidos, conferidos: do salao, e com id valido. */
  private java.util.Set<UUID> servicosPedidos(UUID tenantId, List<String> ids) {
    java.util.Set<UUID> pedidos = new java.util.LinkedHashSet<>();
    if (ids == null) return pedidos;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      try {
        pedidos.add(UUID.fromString(id.trim()));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Serviço inválido na lista do cupom.");
      }
    }
    if (pedidos.isEmpty()) return pedidos;
    if (servicoRepository != null && servicoRepository.findByTenantIdAndIdIn(tenantId, pedidos).size() != pedidos.size()) {
      throw new IllegalArgumentException("Um dos serviços da lista não é deste salão.");
    }
    return pedidos;
  }

  /** Situacao do cupom hoje: VALIDO, AGENDADO, VENCIDO, ESGOTADO ou INATIVO. */
  public static String situacao(Cupom c, LocalDate hoje) {
    if (!c.isAtivo()) return "INATIVO";
    if (c.getLimiteTotal() != null && c.getUsos() >= c.getLimiteTotal()) return "ESGOTADO";
    if (c.getFim() != null && hoje.isAfter(c.getFim())) return "VENCIDO";
    if (c.getInicio() != null && hoje.isBefore(c.getInicio())) return "AGENDADO";
    return "VALIDO";
  }

  /**
   * Confere as regras que nao dependem de concorrencia. Mensagem pronta para a tela.
   *
   * @param exigeCliente true quando o cupom tem limite por cliente: sem cliente nao da para contar
   */
  static void conferirRegras(Cupom c, UUID clienteId, BigDecimal base, LocalDate hoje) {
    String s = situacao(c, hoje);
    switch (s) {
      case "INATIVO" -> throw new IllegalArgumentException("Este cupom não está ativo.");
      case "ESGOTADO" -> throw new IllegalArgumentException("Este cupom já foi usado o número máximo de vezes.");
      case "VENCIDO" -> throw new IllegalArgumentException("Este cupom venceu em " + dataBr(c.getFim()) + ".");
      case "AGENDADO" -> throw new IllegalArgumentException("Este cupom só vale a partir de " + dataBr(c.getInicio()) + ".");
      default -> { }
    }
    if (c.getValorMinimo() != null && (base == null || base.compareTo(c.getValorMinimo()) < 0)) {
      throw new IllegalArgumentException("Este cupom vale para compras a partir de " + reais(c.getValorMinimo()) + ".");
    }
    if (c.getLimitePorCliente() != null && clienteId == null) {
      throw new IllegalArgumentException("Este cupom tem limite por cliente: identifique a cliente na comanda.");
    }
  }

  static String dataBr(LocalDate d) {
    return d == null ? "" : String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
  }

  static String reais(BigDecimal v) {
    return String.format(Locale.of("pt", "BR"), "R$ %,.2f", v);
  }

  static LocalDate hoje() {
    return LocalDate.now(ZONA_BR);
  }

  // ─── Uso (comanda e agendamento) ───────────────────────────────────────────

  /** O cupom pelo codigo digitado, do salao. Mensagem unica para "nao existe": nada de varrer codigos. */
  public Cupom buscarPorCodigo(UUID tenantId, String codigo) {
    String c = CodigoAleatorio.normalizar(codigo);
    if (c.isEmpty()) throw new IllegalArgumentException("Informe o código do cupom.");
    return cupomRepository.buscarPorCodigo(tenantId, c)
        .orElseThrow(() -> new IllegalArgumentException(
            CodigoAleatorio.pareceDeOutroSalao(c, prefixoDoSalao(tenantId))
                ? "Este código é de outro salão: confira com a cliente."
                : "Cupom não encontrado. Confira o código."));
  }

  /** No link publico, codigo inexistente tem a mesma resposta de codigo que nao vale: nada de varrer codigos. */
  public Cupom buscarPorCodigoPublico(UUID tenantId, String codigo) {
    String c = CodigoAleatorio.normalizar(codigo);
    return cupomRepository.buscarPorCodigo(tenantId, c)
        .orElseThrow(() -> new IllegalArgumentException("Este cupom não vale para este agendamento."));
  }

  /**
   * Validacao do link publico, sem reservar. A cliente pode ainda nao ter cadastro: o limite por cliente so e
   * contado quando o telefone ja e de alguem; o pedido do horario confere de novo, com a cliente gravada.
   */
  @Transactional(readOnly = true)
  public BigDecimal simularPublico(UUID tenantId, String codigo, UUID clienteIdOuNulo, BigDecimal base,
      Map<UUID, BigDecimal> valorPorServico) {
    Cupom c = buscarPorCodigoPublico(tenantId, codigo);
    conferirRegras(c, clienteIdOuNulo == null ? CLIENTE_A_CADASTRAR : clienteIdOuNulo, base, hoje());
    if (c.getLimitePorCliente() != null && clienteIdOuNulo != null
        && usoRepository.contarAtivosDoCliente(c.getId(), clienteIdOuNulo) >= c.getLimitePorCliente()) {
      throw new IllegalArgumentException("Você já usou este cupom.");
    }
    java.util.Set<UUID> servicos = servicosDoCupom(c);
    BigDecimal elegivel = baseElegivel(servicos, base, valorPorServico);
    exigirServicoDaLista(tenantId, c, servicos, elegivel);
    return desconto(c, elegivel);
  }

  /** Marcador para a regra "limite por cliente exige cliente": no link, a cliente ainda vai ser cadastrada. */
  private static final UUID CLIENTE_A_CADASTRAR = new UUID(0L, 0L);

  /** Validacao sem reservar (pagina publica): devolve o desconto ou lanca a recusa em frase. */
  @Transactional(readOnly = true)
  public BigDecimal simular(UUID tenantId, String codigo, UUID clienteId, BigDecimal base) {
    Cupom c = buscarPorCodigo(tenantId, codigo);
    conferirRegras(c, clienteId, base, hoje());
    if (c.getLimitePorCliente() != null && clienteId != null
        && usoRepository.contarAtivosDoCliente(c.getId(), clienteId) >= c.getLimitePorCliente()) {
      throw new IllegalArgumentException("Este cupom já foi usado por esta cliente.");
    }
    return desconto(c, base);
  }

  /**
   * Reserva um uso do cupom para uma comanda OU um agendamento. Trava o cupom, confere as regras de novo,
   * conta o limite por cliente e incrementa os usos so se o limite total deixar.
   */
  @Transactional
  public UsoDeCupom reservar(UUID tenantId, UUID cupomId, UUID comandaId, UUID appointmentId, UUID clienteId,
      BigDecimal base) {
    return reservar(tenantId, cupomId, comandaId, appointmentId, clienteId, base, null);
  }

  /**
   * Como {@link #reservar(UUID, UUID, UUID, UUID, UUID, BigDecimal)}, com o valor de cada servico da conta: o cupom
   * de lista desconta so sobre os servicos dela (V182), e e recusado quando a conta nao tem nenhum deles.
   */
  @Transactional
  public UsoDeCupom reservar(UUID tenantId, UUID cupomId, UUID comandaId, UUID appointmentId, UUID clienteId,
      BigDecimal base, Map<UUID, BigDecimal> valorPorServico) {
    Cupom c = cupomRepository.travar(cupomId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado. Confira o código."));
    conferirRegras(c, clienteId, base, hoje());
    java.util.Set<UUID> servicos = servicosDoCupom(c);
    BigDecimal elegivel = baseElegivel(servicos, base, valorPorServico);
    exigirServicoDaLista(tenantId, c, servicos, elegivel);
    if (c.getLimitePorCliente() != null
        && usoRepository.contarAtivosDoCliente(c.getId(), clienteId) >= c.getLimitePorCliente()) {
      throw new IllegalArgumentException("Este cupom já foi usado por esta cliente.");
    }
    if (cupomRepository.reservarUso(c.getId(), tenantId) == 0) {
      throw new IllegalArgumentException("Este cupom já foi usado o número máximo de vezes.");
    }
    UsoDeCupom uso = new UsoDeCupom();
    uso.setTenantId(tenantId);
    uso.setCupomId(c.getId());
    uso.setComandaId(comandaId);
    uso.setAppointmentId(appointmentId);
    uso.setClientId(clienteId);
    uso.setValorDesconto(desconto(c, elegivel));
    uso.setCreatedBy(authenticatedUser.idOuNulo());
    usoRepository.saveAndFlush(uso);
    return uso;
  }

  /**
   * Libera o uso ativo da comanda (remover cupom, cancelar, estornar). O uso que veio do agendamento online so se
   * desliga da comanda: ele continua do agendamento (o preco com desconto e dele), e uma nova comanda o liga de
   * novo. Quem libera esse uso e o cancelamento ou a falta do agendamento.
   */
  @Transactional
  public void liberarDaComanda(UUID tenantId, UUID comandaId) {
    usosAtivosDaComanda(tenantId, comandaId).forEach(this::liberarUso);
  }

  /** Os usos ativos da comanda, na ordem em que entraram (V183: pode haver mais de um). */
  public List<UsoDeCupom> usosAtivosDaComanda(UUID tenantId, UUID comandaId) {
    return usoRepository.ativosDaComanda(comandaId).stream().filter(u -> tenantId.equals(u.getTenantId())).toList();
  }

  /** Libera um uso da comanda. O do agendamento online so se desliga dela (ver {@link #liberarDaComanda}). */
  @Transactional
  public void liberarUso(UsoDeCupom u) {
    if (u.getAppointmentId() != null) {
      u.setComandaId(null);
      usoRepository.save(u);
    } else {
      desfazer(u);
    }
  }

  /** Libera o uso ativo do agendamento (cancelado ou falta). */
  @Transactional
  public void liberarDoAgendamento(UUID tenantId, UUID appointmentId) {
    usoRepository.ativoDoAgendamento(appointmentId).filter(u -> tenantId.equals(u.getTenantId())).ifPresent(this::desfazer);
  }

  private void desfazer(UsoDeCupom uso) {
    uso.setStatus(UsoDeCupom.STATUS_DESFEITO);
    uso.setDesfeitoEm(Instant.now());
    usoRepository.save(uso);
    cupomRepository.liberarUso(uso.getCupomId(), uso.getTenantId());
  }

  /** O valor do uso acompanha a comanda (o cupom de % muda com o subtotal). */
  @Transactional
  public void atualizarValorDoUso(UsoDeCupom u, BigDecimal valor) {
    if (u.getValorDesconto() != null && u.getValorDesconto().compareTo(valor) == 0) return;
    u.setValorDesconto(valor);
    usoRepository.save(u);
  }

  /** O uso que um agendamento online trouxe passa a apontar tambem para a comanda dele. */
  @Transactional
  public boolean ligarUsoDoAgendamentoAComanda(UUID tenantId, UUID appointmentId, UUID comandaId) {
    Optional<UsoDeCupom> uso = usoRepository.ativoDoAgendamento(appointmentId)
        .filter(u -> tenantId.equals(u.getTenantId()))
        .filter(u -> u.getComandaId() == null);
    uso.ifPresent(u -> {
      u.setComandaId(comandaId);
      usoRepository.save(u);
    });
    return uso.isPresent();
  }

  public Optional<Cupom> porId(UUID tenantId, UUID id) {
    return id == null ? Optional.empty() : cupomRepository.findByIdAndTenantId(id, tenantId);
  }

  // ─── Cadastro (cupom avulso) ───────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<CupomDtos.CupomResponse> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = hoje();
    return cupomRepository.findByTenantIdAndLoteIdIsNullOrderByCreatedAtDesc(tenantId).stream()
        .map(c -> toResponse(c, hoje)).toList();
  }

  @Transactional
  public CupomDtos.CupomResponse criar(CupomDtos.CupomRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    String codigo = CodigoAleatorio.normalizar(req.codigo);
    if (cupomRepository.contarCodigo(tenantId, codigo) > 0) {
      throw new ApiClientErrorException("Já existe um cupom com o código " + codigo + ".", 409);
    }
    Cupom c = new Cupom();
    c.setTenantId(tenantId);
    c.setCodigo(codigo);
    aplicar(req, c);
    c.setCreatedBy(authenticatedUser.idOuNulo());
    java.util.Set<UUID> servicos = servicosPedidos(tenantId, req.servicoIds);
    cupomRepository.saveAndFlush(c);
    for (UUID s : servicos) cupomRepository.incluirServicoNoCupom(c.getId(), s);
    Map<String, Object> depois = dados(c);
    depois.put("servicos", servicos.stream().map(UUID::toString).toList());
    auditar(tenantId, "COUPON_CREATE", "COUPON", c.getId(), null, depois);
    return toResponse(cupomRepository.findById(c.getId()).orElse(c), hoje());
  }

  @Transactional
  public CupomDtos.CupomResponse editar(UUID id, CupomDtos.CupomRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cupom c = cupomRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cupom não encontrado.", 404));
    if (c.getLoteId() != null) {
      throw new IllegalArgumentException("Este código é de um lote: as regras se mudam no lote.");
    }
    Map<String, Object> antes = dados(c);
    String codigo = CodigoAleatorio.normalizar(req.codigo);
    if (!codigo.equalsIgnoreCase(c.getCodigo())) {
      if (c.getUsos() > 0) throw new IllegalArgumentException("Cupom já usado não muda de código.");
      if (cupomRepository.contarCodigo(tenantId, codigo) > 0) {
        throw new ApiClientErrorException("Já existe um cupom com o código " + codigo + ".", 409);
      }
      c.setCodigo(codigo);
    }
    aplicar(req, c);
    if (c.getLimiteTotal() != null && c.getLimiteTotal() < c.getUsos()) {
      throw new IllegalArgumentException("O limite não pode ficar abaixo dos " + c.getUsos() + " usos que o cupom já teve.");
    }
    java.util.Set<UUID> servicos = servicosPedidos(tenantId, req.servicoIds);
    antes.put("servicos", cupomRepository.servicosDoCupom(c.getId()).stream().map(UUID::toString).toList());
    cupomRepository.saveAndFlush(c);
    cupomRepository.limparServicosDoCupom(c.getId());
    for (UUID s : servicos) cupomRepository.incluirServicoNoCupom(c.getId(), s);
    Map<String, Object> depois = dados(c);
    depois.put("servicos", servicos.stream().map(UUID::toString).toList());
    auditar(tenantId, "COUPON_UPDATE", "COUPON", c.getId(), antes, depois);
    return toResponse(c, hoje());
  }

  @Transactional(readOnly = true)
  public List<CupomDtos.UsoResponse> usos(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Cupom c = cupomRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Cupom não encontrado.", 404));
    List<UsoDeCupom> usos = usoRepository.findTop100ByTenantIdAndCupomIdOrderByCreatedAtDesc(tenantId, c.getId());
    Map<UUID, String> nomes = nomesDosClientes(tenantId, usos);
    return usos.stream().map(u -> {
      CupomDtos.UsoResponse r = new CupomDtos.UsoResponse();
      r.id = u.getId().toString();
      r.codigo = c.getCodigo();
      r.comandaId = str(u.getComandaId());
      r.appointmentId = str(u.getAppointmentId());
      r.clientId = str(u.getClientId());
      r.clienteNome = u.getClientId() == null ? null : nomes.get(u.getClientId());
      r.valorDesconto = u.getValorDesconto();
      r.status = u.getStatus();
      r.createdAt = u.getCreatedAt() == null ? null : u.getCreatedAt().toString();
      return r;
    }).toList();
  }

  private void aplicar(CupomDtos.CupomRequest req, Cupom c) {
    regras(req);
    c.setDescricao(vazioParaNulo(req.descricao));
    c.setTipo(req.tipo);
    c.setValor(req.valor.setScale(2, RoundingMode.HALF_UP));
    c.setInicio(data(req.inicio, "início"));
    c.setFim(data(req.fim, "fim"));
    c.setValorMinimo(req.valorMinimo);
    c.setLimiteTotal(req.limiteTotal);
    c.setLimitePorCliente(req.limitePorCliente);
    if (req.ativo != null) c.setAtivo(req.ativo);
  }

  /** As regras comuns ao cupom e ao lote. */
  static void regras(CupomDtos.RegrasDoDesconto req) {
    if (Cupom.TIPO_PERCENTUAL.equals(req.tipo) && req.valor.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("O desconto em % vai até 100%.");
    }
    if (Cupom.TIPO_VALOR.equals(req.tipo) && req.valor.compareTo(new BigDecimal("99999")) > 0) {
      throw new IllegalArgumentException("Desconto em R$ acima do limite. Confira o valor.");
    }
    LocalDate inicio = data(req.inicio, "início");
    LocalDate fim = data(req.fim, "fim");
    if (inicio != null && fim != null && fim.isBefore(inicio)) {
      throw new IllegalArgumentException("A data de fim vem antes da data de início.");
    }
  }

  static LocalDate data(String valor, String campo) {
    if (valor == null || valor.isBlank()) return null;
    try {
      return LocalDate.parse(valor.trim().substring(0, Math.min(10, valor.trim().length())));
    } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
      throw new IllegalArgumentException("Data de " + campo + " inválida.");
    }
  }

  // ─── Gerador de lotes ──────────────────────────────────────────────────────

  @Transactional
  public CupomDtos.LoteResponse gerarLote(CupomDtos.LoteRequest req) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    regras(req);
    LoteDeCupom lote = new LoteDeCupom();
    lote.setTenantId(tenantId);
    lote.setNome(req.nome.trim());
    lote.setPrefixo(CodigoAleatorio.normalizar(req.prefixo));
    lote.setQuantidade(0);
    lote.setTipo(req.tipo);
    lote.setValor(req.valor.setScale(2, RoundingMode.HALF_UP));
    lote.setInicio(data(req.inicio, "início"));
    lote.setFim(data(req.fim, "fim"));
    lote.setValorMinimo(req.valorMinimo);
    lote.setCreatedBy(authenticatedUser.idOuNulo());
    lote.setQuantidade(req.quantidade);
    java.util.Set<UUID> servicos = servicosPedidos(tenantId, req.servicoIds);
    loteRepository.saveAndFlush(lote);
    for (UUID s : servicos) cupomRepository.incluirServicoNoLote(lote.getId(), s);
    gerarCodigos(tenantId, lote, req.quantidade);
    auditar(tenantId, "COUPON_BATCH_CREATE", "COUPON_BATCH", lote.getId(), null, dados(lote));
    return acompanhar(lote.getId(), null);
  }

  @Transactional
  public CupomDtos.LoteResponse gerarMais(UUID loteId, int quantidade) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LoteDeCupom lote = loteOuFalhar(tenantId, loteId);
    if (!lote.isAtivo()) throw new IllegalArgumentException("Este lote está desativado.");
    if (quantidade < 1 || quantidade > 1000) throw new IllegalArgumentException("Gere de 1 a 1.000 códigos por vez.");
    gerarCodigos(tenantId, lote, quantidade);
    Map<String, Object> antes = dados(lote);
    lote.setQuantidade(lote.getQuantidade() + quantidade);
    loteRepository.save(lote);
    auditar(tenantId, "COUPON_BATCH_MORE", "COUPON_BATCH", lote.getId(), antes, dados(lote));
    return acompanhar(lote.getId(), null);
  }

  @Transactional
  public CupomDtos.LoteResponse desativarLote(UUID loteId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LoteDeCupom lote = loteOuFalhar(tenantId, loteId);
    Map<String, Object> antes = dados(lote);
    lote.setAtivo(false);
    loteRepository.saveAndFlush(lote);
    int desativados = cupomRepository.desativarNaoUsadosDoLote(lote.getId(), tenantId);
    Map<String, Object> depois = dados(lote);
    depois.put("codigosDesativados", desativados);
    auditar(tenantId, "COUPON_BATCH_DISABLE", "COUPON_BATCH", lote.getId(), antes, depois);
    return acompanhar(lote.getId(), null);
  }

  /**
   * Cria os codigos do lote: PREFIXO-XXXXXX, uso unico. Sorteia de novo o que ja existir no salao (ou no proprio
   * lote). Tudo na mesma transacao: ou nascem todos, ou nenhum.
   */
  private void gerarCodigos(UUID tenantId, LoteDeCupom lote, int quantidade) {
    // 482913-NATAL-7K3QPZ: os 6 digitos do salao na frente (pedido do dono, 17/11).
    String doSalao = prefixoDoSalao(tenantId);
    Set<String> sorteados = new HashSet<>();
    List<Cupom> novos = new ArrayList<>(quantidade);
    int tentativas = 0;
    while (novos.size() < quantidade) {
      if (++tentativas > quantidade * 20 + 100) {
        throw new IllegalStateException("Não foi possível gerar códigos únicos. Tente um prefixo diferente.");
      }
      String codigo = doSalao + "-" + lote.getPrefixo() + "-" + CodigoAleatorio.gerar(TAMANHO_DO_CODIGO_DO_LOTE);
      if (!sorteados.add(codigo) || cupomRepository.contarCodigo(tenantId, codigo) > 0) continue;
      Cupom c = new Cupom();
      c.setTenantId(tenantId);
      c.setCodigo(codigo);
      c.setDescricao(lote.getNome());
      c.setTipo(lote.getTipo());
      c.setValor(lote.getValor());
      c.setInicio(lote.getInicio());
      c.setFim(lote.getFim());
      c.setValorMinimo(lote.getValorMinimo());
      c.setLimiteTotal(1);
      c.setLoteId(lote.getId());
      c.setCreatedBy(lote.getCreatedBy());
      novos.add(c);
    }
    cupomRepository.saveAll(novos);
    cupomRepository.flush();
  }

  @Transactional(readOnly = true)
  public List<CupomDtos.LoteResumoResponse> listarLotes() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = hoje();
    return loteRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(lote -> {
      CupomDtos.LoteResumoResponse r = new CupomDtos.LoteResumoResponse();
      preencherResumo(r, lote, cupomRepository.findByTenantIdAndLoteIdOrderByCodigoAsc(tenantId, lote.getId()), hoje);
      return r;
    }).toList();
  }

  /**
   * O lote e cada codigo com a situacao: USADO (com data, cliente, comanda ou agendamento e valor), NAO_USADO,
   * VENCIDO ou DESATIVADO. {@code filtro} vazio traz todos.
   */
  @Transactional(readOnly = true)
  public CupomDtos.LoteResponse acompanhar(UUID loteId, String filtro) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LoteDeCupom lote = loteOuFalhar(tenantId, loteId);
    LocalDate hoje = hoje();
    List<Cupom> codigos = cupomRepository.findByTenantIdAndLoteIdOrderByCodigoAsc(tenantId, lote.getId());
    CupomDtos.LoteResponse r = new CupomDtos.LoteResponse();
    preencherResumo(r, lote, codigos, hoje);

    Map<UUID, UsoDeCupom> usoPorCupom = new HashMap<>();
    for (Cupom c : codigos) {
      if (c.getUsos() == 0) continue;
      usoRepository.findTop100ByTenantIdAndCupomIdOrderByCreatedAtDesc(tenantId, c.getId()).stream()
          .filter(u -> UsoDeCupom.STATUS_ATIVO.equals(u.getStatus())).findFirst()
          .ifPresent(u -> usoPorCupom.put(c.getId(), u));
    }
    Map<UUID, String> nomes = nomesDosClientes(tenantId, new ArrayList<>(usoPorCupom.values()));
    String f = filtro == null ? "" : filtro.trim().toUpperCase(Locale.ROOT);
    for (Cupom c : codigos) {
      String s = situacaoNoLote(c, hoje);
      if (!f.isEmpty() && !f.equals(s)) continue;
      CupomDtos.CodigoDoLoteResponse item = new CupomDtos.CodigoDoLoteResponse();
      item.id = c.getId().toString();
      item.codigo = c.getCodigo();
      item.situacao = s;
      UsoDeCupom uso = usoPorCupom.get(c.getId());
      if (uso != null) {
        item.usadoEm = uso.getCreatedAt() == null ? null : uso.getCreatedAt().toString();
        item.clientId = str(uso.getClientId());
        item.clienteNome = uso.getClientId() == null ? null : nomes.get(uso.getClientId());
        item.comandaId = str(uso.getComandaId());
        item.appointmentId = str(uso.getAppointmentId());
        item.valorDesconto = uso.getValorDesconto();
      }
      r.codigos.add(item);
    }
    return r;
  }

  /** CSV no formato dos relatorios da zona: ponto e virgula, virgula decimal. */
  @Transactional(readOnly = true)
  public String exportarLote(UUID loteId) {
    CupomDtos.LoteResponse lote = acompanhar(loteId, null);
    StringBuilder csv = new StringBuilder("Código;Situação;Usado em;Cliente;Desconto (R$)\r\n");
    for (CupomDtos.CodigoDoLoteResponse c : lote.codigos) {
      csv.append(c.codigo).append(';')
          .append(rotuloDaSituacao(c.situacao)).append(';')
          .append(c.usadoEm == null ? "" : dataDoInstante(c.usadoEm)).append(';')
          .append(c.clienteNome == null ? "" : celula(c.clienteNome)).append(';')
          .append(c.valorDesconto == null ? "" : c.valorDesconto.toPlainString().replace('.', ','))
          .append("\r\n");
    }
    return csv.toString();
  }

  public String nomeDoArquivoDoLote(UUID loteId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LoteDeCupom lote = loteOuFalhar(tenantId, loteId);
    return "lote-de-cupons-" + lote.getPrefixo().toLowerCase(Locale.ROOT) + ".csv";
  }

  static String situacaoNoLote(Cupom c, LocalDate hoje) {
    if (c.getUsos() > 0) return "USADO";
    if (!c.isAtivo()) return "DESATIVADO";
    if (c.getFim() != null && hoje.isAfter(c.getFim())) return "VENCIDO";
    return "NAO_USADO";
  }

  static String rotuloDaSituacao(String s) {
    return switch (s) {
      case "USADO" -> "Usado";
      case "NAO_USADO" -> "Não usado";
      case "VENCIDO" -> "Vencido";
      case "DESATIVADO" -> "Desativado";
      default -> s;
    };
  }

  private static String dataDoInstante(String instante) {
    try {
      return Instant.parse(instante).atZone(ZONA_BR).toLocalDate().toString();
    } catch (DateTimeParseException e) {
      return instante;
    }
  }

  private static String celula(String texto) {
    String t = texto;
    if (!t.isEmpty() && "=+-@".indexOf(t.charAt(0)) >= 0) t = "'" + t;
    return t.contains(";") || t.contains("\"") ? "\"" + t.replace("\"", "\"\"") + "\"" : t;
  }

  private void preencherResumo(CupomDtos.LoteResumoResponse r, LoteDeCupom lote, List<Cupom> codigos, LocalDate hoje) {
    r.id = lote.getId().toString();
    r.nome = lote.getNome();
    r.prefixo = lote.getPrefixo();
    r.tipo = lote.getTipo();
    r.valor = lote.getValor();
    r.inicio = lote.getInicio() == null ? null : lote.getInicio().toString();
    r.fim = lote.getFim() == null ? null : lote.getFim().toString();
    r.valorMinimo = lote.getValorMinimo();
    r.ativo = lote.isAtivo();
    r.createdAt = lote.getCreatedAt() == null ? null : lote.getCreatedAt().toString();
    r.gerados = codigos.size();
    for (Cupom c : codigos) {
      switch (situacaoNoLote(c, hoje)) {
        case "USADO" -> r.usados++;
        case "VENCIDO" -> r.vencidos++;
        case "DESATIVADO" -> r.desativados++;
        default -> r.naoUsados++;
      }
    }
    r.servicos = servicosParaTela(lote.getTenantId(), cupomRepository.servicosDoLote(lote.getId()));
  }

  private LoteDeCupom loteOuFalhar(UUID tenantId, UUID loteId) {
    return loteRepository.findByIdAndTenantId(loteId, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Lote não encontrado.", 404));
  }

  // ─── Apoio ─────────────────────────────────────────────────────────────────

  private Map<UUID, String> nomesDosClientes(UUID tenantId, List<UsoDeCupom> usos) {
    Map<UUID, String> nomes = new HashMap<>();
    for (UsoDeCupom u : usos) {
      if (u.getClientId() == null || nomes.containsKey(u.getClientId())) continue;
      nomes.put(u.getClientId(),
          clienteRepository.findByIdAndTenantId(u.getClientId(), tenantId).map(Cliente::getName).orElse(null));
    }
    return nomes;
  }

  CupomDtos.CupomResponse toResponse(Cupom c, LocalDate hoje) {
    CupomDtos.CupomResponse r = new CupomDtos.CupomResponse();
    r.id = c.getId().toString();
    r.codigo = c.getCodigo();
    r.descricao = c.getDescricao();
    r.tipo = c.getTipo();
    r.valor = c.getValor();
    r.inicio = c.getInicio() == null ? null : c.getInicio().toString();
    r.fim = c.getFim() == null ? null : c.getFim().toString();
    r.limiteTotal = c.getLimiteTotal();
    r.limitePorCliente = c.getLimitePorCliente();
    r.valorMinimo = c.getValorMinimo();
    r.usos = c.getUsos();
    r.ativo = c.isAtivo();
    r.situacao = situacao(c, hoje);
    r.createdAt = c.getCreatedAt() == null ? null : c.getCreatedAt().toString();
    r.servicos = servicosParaTela(c.getTenantId(), servicosDoCupom(c));
    return r;
  }

  private static String str(UUID id) {
    return id == null ? null : id.toString();
  }

  private static String vazioParaNulo(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private Map<String, Object> dados(Cupom c) {
    Map<String, Object> d = new HashMap<>();
    d.put("codigo", c.getCodigo());
    d.put("tipo", c.getTipo());
    d.put("valor", c.getValor());
    d.put("inicio", c.getInicio() == null ? null : c.getInicio().toString());
    d.put("fim", c.getFim() == null ? null : c.getFim().toString());
    d.put("limiteTotal", c.getLimiteTotal());
    d.put("limitePorCliente", c.getLimitePorCliente());
    d.put("valorMinimo", c.getValorMinimo());
    d.put("ativo", c.isAtivo());
    return d;
  }

  private Map<String, Object> dados(LoteDeCupom l) {
    Map<String, Object> d = new HashMap<>();
    d.put("nome", l.getNome());
    d.put("prefixo", l.getPrefixo());
    d.put("quantidade", l.getQuantidade());
    d.put("tipo", l.getTipo());
    d.put("valor", l.getValor());
    d.put("fim", l.getFim() == null ? null : l.getFim().toString());
    d.put("ativo", l.isAtivo());
    return d;
  }

  private void auditar(UUID tenantId, String acao, String tipo, UUID id, Object antes, Object depois) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.FINANCE;
      command.action = acao;
      command.entityType = tipo;
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
