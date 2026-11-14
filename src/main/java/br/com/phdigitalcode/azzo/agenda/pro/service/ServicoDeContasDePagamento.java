package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PagamentosDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ContaDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.CampoDeCredencial;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.ResultadoValidacao;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ContaDePagamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantPaymentSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * As contas do salao nas operadoras de pagamento (Pagamentos — Fase 0, V167).
 *
 * <p>Credenciais entram, sao conferidas NA OPERADORA (nada se salva com chave errada), cifradas e
 * nunca mais saem: a tela ve so a mascara. Cada conta tem a propria URL de aviso (webhook), com um
 * token que identifica a conta — e a operadora certa: o token de uma conta Asaas nao abre aviso
 * "do Mercado Pago".
 *
 * <p>{@code padraoPara} diz qual conta o PDV e o agendamento usam por padrao em cada caso (Pix,
 * cartao presencial, online). Um caso tem uma conta padrao so: marcar outra tira da anterior.
 */
@Service
public class ServicoDeContasDePagamento implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoDeContasDePagamento.class);
  public static final Set<String> CASOS_DE_USO = Set.of("PIX", "CARTAO_PRESENCIAL", "ONLINE");

  /** As operadoras do plano. As que ainda nao tem implementacao aparecem como "em breve". */
  static final Map<String, String> OPERADORAS_DO_PLANO = new LinkedHashMap<>();

  static {
    OPERADORAS_DO_PLANO.put("MERCADO_PAGO", "Mercado Pago");
    // A maquininha Stone (Stone Connect) passa pela API da Pagar.me: e uma conta so.
    OPERADORAS_DO_PLANO.put("PAGARME", "Stone / Pagar.me");
    OPERADORAS_DO_PLANO.put("CIELO", "Cielo");
    OPERADORAS_DO_PLANO.put("PAGBANK", "PagBank");
    OPERADORAS_DO_PLANO.put("REDE", "Rede");
    OPERADORAS_DO_PLANO.put("GETNET", "Getnet");
    OPERADORAS_DO_PLANO.put("ASAAS", "Asaas");
  }

  private final ContaDePagamentoRepository contas;
  private br.com.phdigitalcode.azzo.agenda.pro.repository.TerminalDePagamentoRepository terminais;

  @org.springframework.beans.factory.annotation.Autowired
  void setTerminais(br.com.phdigitalcode.azzo.agenda.pro.repository.TerminalDePagamentoRepository terminais) {
    this.terminais = terminais;
  }
  private final TenantPaymentSettingsRepository configuracoesAntigas;
  private final Map<String, ProvedorDePagamento> provedores;
  private final EncryptionService cifra;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser usuarioLogado;
  private final AuditService auditService;
  private final ObjectMapper json;
  private final String urlPublica;
  private final SecureRandom aleatorio = new SecureRandom();

  public ServicoDeContasDePagamento(
      ContaDePagamentoRepository contas,
      TenantPaymentSettingsRepository configuracoesAntigas,
      List<ProvedorDePagamento> provedores,
      EncryptionService cifra,
      ContextoTenant contextoTenant,
      AuthenticatedUser usuarioLogado,
      AuditService auditService,
      ObjectMapper json,
      @Value("${app.public-base-url:https://localhost:8443}") String urlPublica) {
    this.contas = contas;
    this.configuracoesAntigas = configuracoesAntigas;
    this.provedores = provedores.stream().collect(Collectors.toMap(ProvedorDePagamento::codigo, Function.identity()));
    this.cifra = cifra;
    this.contextoTenant = contextoTenant;
    this.usuarioLogado = usuarioLogado;
    this.auditService = auditService;
    this.json = json;
    this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
  }

  // ---- catalogo ------------------------------------------------------------------------------

  public List<PagamentosDtos.Operadora> operadoras() {
    List<PagamentosDtos.Operadora> lista = new ArrayList<>();
    OPERADORAS_DO_PLANO.forEach((codigo, nome) -> {
      PagamentosDtos.Operadora o = new PagamentosDtos.Operadora();
      o.codigo = codigo;
      o.nome = nome;
      ProvedorDePagamento p = provedores.get(codigo);
      o.disponivel = p != null;
      if (p != null) {
        o.capacidades = p.capacidades().stream().map(Enum::name).sorted().toList();
        o.campos = p.camposDeCredencial().stream().map(c -> {
          PagamentosDtos.Campo campo = new PagamentosDtos.Campo();
          campo.chave = c.chave();
          campo.rotulo = c.rotulo();
          campo.secreto = c.secreto();
          campo.obrigatorio = c.obrigatorio();
          return campo;
        }).toList();
      }
      lista.add(o);
    });
    return lista;
  }

  // ---- contas --------------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<PagamentosDtos.Conta> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return contas.findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(tenantId).stream().map(this::paraDto).toList();
  }

  @Transactional
  public PagamentosDtos.Conta conectar(PagamentosDtos.ContaRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ProvedorDePagamento provedor = provedor(pedido == null ? null : pedido.provedor);
    Map<String, String> credenciais = limpar(pedido.credenciais);
    exigirCampos(provedor, credenciais, null);

    ContaDePagamento conta = new ContaDePagamento();
    conta.setTenantId(tenantId);
    conta.setProvedor(provedor.codigo());
    conta.setApelido(apelido(pedido.apelido, provedor));
    conta.setAmbiente(ambiente(pedido.ambiente));
    conta.setWebhookToken(novoToken());
    conta.setCreatedBy(usuarioLogado.idOuNulo());
    gravarCredenciais(conta, provedor, credenciais);
    conferirNaOperadora(conta, provedor, true);
    conta.setPadraoPara(String.join(",", casos(pedido.padraoPara)));
    contas.save(conta);
    tirarPadraoDasOutras(conta);
    auditar(tenantId, "PAGAMENTO_CONTA_CONECTADA", conta, null);
    return paraDto(conta);
  }

  @Transactional
  public PagamentosDtos.Conta atualizar(UUID id, PagamentosDtos.ContaRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ContaDePagamento conta = doSalao(id, tenantId);
    ProvedorDePagamento provedor = provedor(conta.getProvedor());
    Map<String, Object> antes = resumo(conta);
    boolean mudouAcesso = false;
    if (pedido.apelido != null && !pedido.apelido.isBlank()) conta.setApelido(apelido(pedido.apelido, provedor));
    if (pedido.ambiente != null && !ambiente(pedido.ambiente).equals(conta.getAmbiente())) {
      conta.setAmbiente(ambiente(pedido.ambiente));
      mudouAcesso = true;
    }
    Map<String, String> novas = limpar(pedido.credenciais);
    if (!novas.isEmpty()) {
      // So os campos que vieram mudam: o resto (ex.: a chave, quando so o ambiente mudou) fica.
      Map<String, String> atuais = new LinkedHashMap<>(credenciais(conta));
      atuais.putAll(novas);
      exigirCampos(provedor, atuais, null);
      gravarCredenciais(conta, provedor, atuais);
      mudouAcesso = true;
    }
    if (mudouAcesso) conferirNaOperadora(conta, provedor, true);
    if (pedido.padraoPara != null) conta.setPadraoPara(String.join(",", casos(pedido.padraoPara)));
    contas.save(conta);
    tirarPadraoDasOutras(conta);
    auditar(tenantId, "PAGAMENTO_CONTA_ALTERADA", conta, antes);
    return paraDto(conta);
  }

  @Transactional
  public void desconectar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ContaDePagamento conta = doSalao(id, tenantId);
    conta.setAtivo(false);
    conta.setPadraoPara("");
    contas.save(conta);
    auditar(tenantId, "PAGAMENTO_CONTA_DESCONECTADA", conta, null);
  }

  /** Confere de novo na operadora, sem mudar nada alem do resultado. */
  @Transactional
  public PagamentosDtos.Conta testar(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ContaDePagamento conta = doSalao(id, tenantId);
    conferirNaOperadora(conta, provedor(conta.getProvedor()), false);
    contas.save(conta);
    return paraDto(conta);
  }

  /** O que o PDV oferece: Pix e link (se houver conta padrao que faca) e as maquininhas ativas. */
  @Transactional(readOnly = true)
  public PagamentosDtos.Disponiveis disponiveis() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    PagamentosDtos.Disponiveis d = new PagamentosDtos.Disponiveis();
    padrao(tenantId, "PIX").ifPresent(c -> {
      ProvedorDePagamento p = provedores.get(c.getProvedor());
      d.pix = p != null && p.capacidades().contains(ProvedorDePagamento.Capacidade.PIX);
      d.operadoraDoPix = OPERADORAS_DO_PLANO.getOrDefault(c.getProvedor(), c.getProvedor());
      // So o Mercado Pago emite Pix sem CPF/CNPJ de quem paga; as outras pedem o cliente cadastrado.
      d.pixExigeCliente = !"MERCADO_PAGO".equals(c.getProvedor());
    });
    padrao(tenantId, "ONLINE").ifPresent(c -> {
      ProvedorDePagamento p = provedores.get(c.getProvedor());
      d.link = p != null && p.capacidades().contains(ProvedorDePagamento.Capacidade.LINK);
    });
    if (terminais != null) {
      for (ContaDePagamento conta : contas.findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(tenantId)) {
        for (var t : terminais.findByTenantIdAndContaIdAndAtivoTrue(tenantId, conta.getId())) {
          PagamentosDtos.Terminal td = new PagamentosDtos.Terminal();
          td.id = t.getId().toString();
          td.apelido = t.getApelido();
          td.modelo = t.getModelo();
          d.maquininhas.add(td);
        }
      }
    }
    return d;
  }

  // ---- maquininhas --------------------------------------------------------------------------

  /** As maquininhas que a conta tem NA OPERADORA, para o dono escolher qual adicionar. */
  @Transactional(readOnly = true)
  public List<PagamentosDtos.Terminal> terminaisNaOperadora(UUID contaId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ContaDePagamento conta = doSalao(contaId, tenantId);
    try {
      return provedor(conta.getProvedor()).terminais(abrir(conta)).stream().map(t -> {
        PagamentosDtos.Terminal dto = new PagamentosDtos.Terminal();
        dto.serial = t.serial();
        dto.modelo = t.modelo();
        dto.apelido = t.apelido();
        return dto;
      }).toList();
    } catch (ProvedorDePagamento.FalhaNaOperadora e) {
      throw new ApiClientErrorException(e.getMessage(), 502);
    }
  }

  /** Adiciona a maquininha ao salao e a deixa pronta para receber cobranca pela API. */
  @Transactional
  public PagamentosDtos.Conta adicionarTerminal(UUID contaId, PagamentosDtos.TerminalRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ContaDePagamento conta = doSalao(contaId, tenantId);
    if (pedido == null || pedido.serial == null || pedido.serial.isBlank()) {
      throw new ApiClientErrorException("Escolha a maquininha.", 400);
    }
    ProvedorDePagamento provedor = provedor(conta.getProvedor());
    if (!provedor.capacidades().contains(ProvedorDePagamento.Capacidade.MAQUININHA_NUVEM)) {
      throw new ApiClientErrorException(provedor.nome() + " ainda não tem maquininha integrada pelo Azzo.", 400);
    }
    String serial = pedido.serial.trim();
    try {
      provedor.prepararTerminal(abrir(conta), serial);
    } catch (ProvedorDePagamento.FalhaNaOperadora e) {
      throw new ApiClientErrorException(e.getMessage(), 502);
    }
    br.com.phdigitalcode.azzo.agenda.pro.entity.TerminalDePagamento terminal =
        terminais.findByContaIdAndSerial(conta.getId(), serial).orElseGet(() -> {
          var novo = new br.com.phdigitalcode.azzo.agenda.pro.entity.TerminalDePagamento();
          novo.setTenantId(tenantId);
          novo.setContaId(conta.getId());
          novo.setSerial(serial);
          return novo;
        });
    terminal.setModelo(limitar(pedido.modelo, 60));
    terminal.setApelido(limitar(pedido.apelido == null || pedido.apelido.isBlank() ? "Maquininha" : pedido.apelido.trim(), 60));
    terminal.setAtivo(true);
    terminais.save(terminal);
    auditar(tenantId, "PAGAMENTO_MAQUININHA_ADICIONADA", conta, null);
    return paraDto(conta);
  }

  @Transactional
  public PagamentosDtos.Conta removerTerminal(UUID contaId, UUID terminalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    ContaDePagamento conta = doSalao(contaId, tenantId);
    terminais.findByIdAndTenantIdAndAtivoTrue(terminalId, tenantId)
        .filter(t -> t.getContaId().equals(conta.getId()))
        .ifPresent(t -> {
          t.setAtivo(false);
          terminais.save(t);
        });
    auditar(tenantId, "PAGAMENTO_MAQUININHA_REMOVIDA", conta, null);
    return paraDto(conta);
  }

  // ---- para o servico de pagamentos ------------------------------------------------------------

  /** A conta aberta (credenciais em memoria, so durante a chamada). */
  public ProvedorDePagamento.Conta abrir(ContaDePagamento conta) {
    Map<String, String> credenciais = new LinkedHashMap<>(credenciais(conta));
    credenciais.put("webhookToken", conta.getWebhookToken());
    return new ProvedorDePagamento.Conta(
        conta.getId().toString(), conta.getTenantId().toString(), conta.getAmbiente(), credenciais, urlDeAviso(conta));
  }

  public ProvedorDePagamento provedor(String codigo) {
    ProvedorDePagamento p = codigo == null ? null : provedores.get(codigo.trim().toUpperCase());
    if (p == null) {
      String nome = codigo == null ? "" : OPERADORAS_DO_PLANO.getOrDefault(codigo.trim().toUpperCase(), codigo);
      throw new ApiClientErrorException(
          nome.isBlank() ? "Escolha a operadora." : "A integração com " + nome + " ainda não está disponível.", 400);
    }
    return p;
  }

  /** A conta padrao do salao para um caso de uso (PIX, CARTAO_PRESENCIAL, ONLINE). */
  public Optional<ContaDePagamento> padrao(UUID tenantId, String caso) {
    return contas.findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(tenantId).stream()
        .filter(c -> casosDaConta(c).contains(caso))
        .findFirst();
  }

  public String urlDeAviso(ContaDePagamento conta) {
    return urlPublica + "/webhook/pagamentos/" + conta.getProvedor().toLowerCase() + "/" + conta.getWebhookToken();
  }

  // ---- migracao da configuracao antiga (V114) ---------------------------------------------------

  /**
   * Na subida: a conta Asaas configurada na tela antiga (tenant_payment_settings) vira uma conta
   * aqui, uma vez so. A chave e recifrada no formato novo (JSON) — por isso nao da para fazer na
   * migration SQL. Os fluxos antigos continuam lendo a tabela antiga nesta fase.
   */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    try {
      for (TenantPaymentSettings antiga : configuracoesAntigas.findAll()) {
        if (antiga.getApiKeyEnc() == null || antiga.getApiKeyEnc().isBlank()) continue;
        if (contas.existsByTenantIdAndProvedor(antiga.getTenantId(), "ASAAS")) continue;
        String chave = cifra.decrypt(antiga.getApiKeyEnc());
        if (chave == null || chave.isBlank()) continue;
        ContaDePagamento conta = new ContaDePagamento();
        conta.setTenantId(antiga.getTenantId());
        conta.setProvedor("ASAAS");
        conta.setApelido("Asaas");
        conta.setAmbiente("PROD".equals(antiga.getAmbiente()) ? "PROD" : "SANDBOX");
        conta.setWebhookToken(antiga.getWebhookToken() != null ? antiga.getWebhookToken() + "-v2" : novoToken());
        conta.setAtivo(antiga.isAtivo());
        conta.setPadraoPara(antiga.isAtivo() ? "PIX" : "");
        gravarCredenciais(conta, provedores.get("ASAAS"), Map.of("apiKey", chave));
        contas.save(conta);
        LOG.info("pagamentos_conta_migrada tenant_id={} provedor=ASAAS ativo={}", antiga.getTenantId(), antiga.isAtivo());
      }
    } catch (Exception e) {
      // Nao derruba a subida: a tela antiga continua funcionando e a migracao tenta na proxima.
      LOG.error("pagamentos_migracao_da_conta_antiga_falhou", e);
    }
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private void conferirNaOperadora(ContaDePagamento conta, ProvedorDePagamento provedor, boolean recusarSeInvalida) {
    ResultadoValidacao resultado = provedor.validarCredenciais(abrir(conta));
    if (resultado.valida()) {
      conta.setValidadaEm(Instant.now());
      conta.setUltimoErro(null);
      return;
    }
    conta.setUltimoErro(limitar(resultado.mensagem(), 300));
    if (recusarSeInvalida) throw new ApiClientErrorException(resultado.mensagem(), 400);
  }

  private void gravarCredenciais(ContaDePagamento conta, ProvedorDePagamento provedor, Map<String, String> credenciais) {
    try {
      conta.setCredenciaisEnc(cifra.encrypt(json.writeValueAsString(credenciais)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao cifrar as credenciais da conta de pagamento", e);
    }
    // A mascara: os 4 ultimos do primeiro campo secreto (a chave), nunca mais que isso.
    String secreta = provedor.camposDeCredencial().stream().filter(CampoDeCredencial::secreto)
        .map(c -> credenciais.get(c.chave())).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    conta.setCredenciaisMascara(secreta == null ? null : "••••" + secreta.substring(Math.max(0, secreta.length() - 4)));
  }

  Map<String, String> credenciais(ContaDePagamento conta) {
    try {
      return json.readValue(cifra.decrypt(conta.getCredenciaisEnc()), new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Credenciais da conta de pagamento ilegíveis (id=" + conta.getId() + ")", e);
    }
  }

  private void exigirCampos(ProvedorDePagamento provedor, Map<String, String> credenciais, String ignorar) {
    List<String> faltando = provedor.camposDeCredencial().stream()
        .filter(c -> !c.chave().equals(ignorar))
        .filter(CampoDeCredencial::obrigatorio)
        .filter(c -> credenciais.get(c.chave()) == null || credenciais.get(c.chave()).isBlank())
        .map(CampoDeCredencial::rotulo).toList();
    if (!faltando.isEmpty()) throw new ApiClientErrorException("Preencha: " + String.join(", ", faltando) + ".", 400);
  }

  private void tirarPadraoDasOutras(ContaDePagamento conta) {
    Set<String> meus = casosDaConta(conta);
    if (meus.isEmpty()) return;
    for (ContaDePagamento outra : contas.findByTenantIdAndAtivoTrueOrderByCreatedAtAsc(conta.getTenantId())) {
      if (outra.getId().equals(conta.getId())) continue;
      Set<String> deles = new LinkedHashSet<>(casosDaConta(outra));
      if (deles.removeAll(meus)) {
        outra.setPadraoPara(String.join(",", deles));
        contas.save(outra);
      }
    }
  }

  static Set<String> casosDaConta(ContaDePagamento conta) {
    if (conta.getPadraoPara() == null || conta.getPadraoPara().isBlank()) return Set.of();
    return Arrays.stream(conta.getPadraoPara().split(",")).map(String::trim).filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static List<String> casos(List<String> pedidos) {
    if (pedidos == null) return List.of();
    List<String> casos = new ArrayList<>();
    for (String p : pedidos) {
      String caso = p == null ? "" : p.trim().toUpperCase();
      if (!CASOS_DE_USO.contains(caso)) throw new ApiClientErrorException("Uso desconhecido: " + p + ".", 400);
      if (!casos.contains(caso)) casos.add(caso);
    }
    return casos;
  }

  private ContaDePagamento doSalao(UUID id, UUID tenantId) {
    return contas.findByIdAndTenantIdAndAtivoTrue(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Conta de pagamento não encontrada.", 404));
  }

  PagamentosDtos.Conta paraDto(ContaDePagamento conta) {
    PagamentosDtos.Conta dto = new PagamentosDtos.Conta();
    dto.id = conta.getId().toString();
    dto.provedor = conta.getProvedor();
    dto.nomeDoProvedor = OPERADORAS_DO_PLANO.getOrDefault(conta.getProvedor(), conta.getProvedor());
    dto.apelido = conta.getApelido();
    dto.ambiente = conta.getAmbiente();
    dto.credenciaisMascara = conta.getCredenciaisMascara();
    dto.padraoPara = new ArrayList<>(casosDaConta(conta));
    dto.validadaEm = conta.getValidadaEm() == null ? null : conta.getValidadaEm().toString();
    dto.ultimoErro = conta.getUltimoErro();
    dto.urlDeAviso = urlDeAviso(conta);
    ProvedorDePagamento p = provedores.get(conta.getProvedor());
    dto.capacidades = p == null ? List.of() : p.capacidades().stream().map(Enum::name).sorted().toList();
    if (terminais != null) {
      dto.terminais = terminais.findByTenantIdAndContaIdAndAtivoTrue(conta.getTenantId(), conta.getId()).stream().map(t -> {
        PagamentosDtos.Terminal td = new PagamentosDtos.Terminal();
        td.id = t.getId().toString();
        td.serial = t.getSerial();
        td.modelo = t.getModelo();
        td.apelido = t.getApelido();
        return td;
      }).toList();
    }
    return dto;
  }

  private Map<String, Object> resumo(ContaDePagamento conta) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("provedor", conta.getProvedor());
    r.put("apelido", conta.getApelido());
    r.put("ambiente", conta.getAmbiente());
    r.put("padraoPara", conta.getPadraoPara());
    r.put("credenciais", conta.getCredenciaisMascara());
    return r;
  }

  /** Nunca a chave: so a operadora, o apelido, o ambiente, o uso e a mascara. */
  private void auditar(UUID tenantId, String acao, ContaDePagamento conta, Map<String, Object> antes) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "PAYMENT_ACCOUNT";
    command.entityId = conta.getId().toString();
    command.sourceChannel = AuditConstants.SourceChannel.API;
    command.before = antes;
    command.after = resumo(conta);
    auditService.recordSuccess(command);
  }

  private static Map<String, String> limpar(Map<String, String> credenciais) {
    Map<String, String> limpas = new LinkedHashMap<>();
    if (credenciais == null) return limpas;
    credenciais.forEach((k, v) -> {
      if (k != null && v != null && !v.isBlank()) limpas.put(k.trim(), v.trim());
    });
    return limpas;
  }

  private static String apelido(String apelido, ProvedorDePagamento provedor) {
    String a = apelido == null || apelido.isBlank() ? provedor.nome() : apelido.trim();
    return limitar(a, 60);
  }

  private static String ambiente(String ambiente) {
    return "PROD".equalsIgnoreCase(ambiente == null ? "" : ambiente.trim()) ? "PROD" : "SANDBOX";
  }

  private String novoToken() {
    byte[] bytes = new byte[24];
    aleatorio.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String limitar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
