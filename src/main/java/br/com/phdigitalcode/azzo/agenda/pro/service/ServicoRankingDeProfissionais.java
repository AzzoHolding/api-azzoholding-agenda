package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.RankingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RankingProfissionais;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RankingProfissionaisRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Criterio;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Linha;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Metricas;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Regra;
import br.com.phdigitalcode.azzo.agenda.pro.util.CelulaCsv;
import br.com.phdigitalcode.azzo.agenda.pro.util.ReceitaDeServicosSql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Ranking dos profissionais com os criterios do dono (V166). O dono monta os rankings; o calculo
 * fica em {@link CalculoDoRanking}; as metricas saem de uma consulta so, por periodo.
 *
 * <p>O profissional ve so a PROPRIA posicao ({@link #minhaPosicao}), e so nos rankings que o dono
 * marcou como visiveis. A lista dos colegas nunca sai por ali.
 */
@Service
public class ServicoRankingDeProfissionais {

  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
  /** Um ano e o bastante para comparar; mais que isso pesa no banco sem ajudar ninguem. */
  static final int MAXIMO_DE_DIAS = 366;

  private final RankingProfissionaisRepository rankings;
  private final ProfissionalRepository profissionais;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser usuarioLogado;
  private final AuditService auditService;
  private final ObjectMapper json;
  private FotosDaEquipe fotos;

  @PersistenceContext
  private EntityManager entityManager;

  public ServicoRankingDeProfissionais(
      RankingProfissionaisRepository rankings,
      ProfissionalRepository profissionais,
      ContextoTenant contextoTenant,
      AuthenticatedUser usuarioLogado,
      AuditService auditService,
      ObjectMapper json) {
    this.rankings = rankings;
    this.profissionais = profissionais;
    this.contextoTenant = contextoTenant;
    this.usuarioLogado = usuarioLogado;
    this.auditService = auditService;
    this.json = json;
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  void setFotosDaEquipe(FotosDaEquipe fotos) {
    this.fotos = fotos;
  }

  // ---- catalogo e CRUD ----------------------------------------------------------------------

  public List<RankingDtos.Criterio> criterios() {
    List<RankingDtos.Criterio> lista = new ArrayList<>();
    for (Criterio c : Criterio.values()) {
      RankingDtos.Criterio dto = new RankingDtos.Criterio();
      dto.chave = c.name();
      dto.rotulo = c.rotulo;
      dto.unidade = c.unidade;
      dto.maiorEMelhor = c.maiorEMelhor;
      lista.add(dto);
    }
    return lista;
  }

  @Transactional(readOnly = true)
  public List<RankingDtos.Ranking> listar() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return rankings.ativosDoSalao(tenantId).stream().map(this::paraDto).toList();
  }

  @Transactional
  public RankingDtos.Ranking criar(RankingDtos.RankingRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    validar(tenantId, pedido, null);
    RankingProfissionais ranking = new RankingProfissionais();
    ranking.setTenantId(tenantId);
    ranking.setCreatedBy(usuarioLogado.idOuNulo());
    aplicar(ranking, pedido);
    rankings.save(ranking);
    auditar(tenantId, "RANKING_CRIADO", ranking.getId(), null, paraDto(ranking));
    return paraDto(ranking);
  }

  @Transactional
  public RankingDtos.Ranking atualizar(UUID id, RankingDtos.RankingRequest pedido) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    RankingProfissionais ranking = doSalao(id, tenantId);
    validar(tenantId, pedido, id);
    RankingDtos.Ranking antes = paraDto(ranking);
    aplicar(ranking, pedido);
    rankings.save(ranking);
    RankingDtos.Ranking depois = paraDto(ranking);
    auditar(tenantId, "RANKING_ALTERADO", id, antes, depois);
    return depois;
  }

  @Transactional
  public void excluir(UUID id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    RankingProfissionais ranking = doSalao(id, tenantId);
    RankingDtos.Ranking antes = paraDto(ranking);
    ranking.setAtivo(false);
    rankings.save(ranking);
    auditar(tenantId, "RANKING_EXCLUIDO", id, antes, null);
  }

  // ---- resultado ----------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public RankingDtos.Resultado resultado(UUID id, String de, String ate) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    RankingProfissionais ranking = doSalao(id, tenantId);
    Periodo periodo = periodo(de, ate);
    List<Linha> linhas = calcular(ranking, metricas(tenantId, periodo));
    Map<UUID, String> fotosDaEquipe = fotosDoSalao(tenantId);

    RankingDtos.Resultado resultado = new RankingDtos.Resultado();
    resultado.ranking = paraDto(ranking);
    resultado.de = periodo.inicio().toString();
    resultado.ate = periodo.fim().toString();
    for (Linha linha : linhas) {
      RankingDtos.LinhaDoResultado dto = new RankingDtos.LinhaDoResultado();
      dto.posicao = linha.posicao();
      dto.profissionalId = linha.profissionalId().toString();
      dto.nome = linha.nome();
      dto.avatarUrl = fotos == null ? null : fotos.url(tenantId, fotosDaEquipe.get(linha.profissionalId()));
      dto.total = linha.total();
      dto.criterios = itens(linha);
      resultado.linhas.add(dto);
    }
    return resultado;
  }

  /** CSV do resultado: posicao, profissional, total e, por criterio, o valor e os pontos. */
  @Transactional(readOnly = true)
  public byte[] exportarCsv(UUID id, String de, String ate) {
    RankingDtos.Resultado resultado = resultado(id, de, ate);
    StringBuilder csv = new StringBuilder();
    List<String> cabecalho = new ArrayList<>(List.of("posicao", "profissional", "total"));
    List<RankingDtos.Regra> regras = resultado.ranking.criterios;
    for (RankingDtos.Regra regra : regras) {
      Criterio c = Criterio.valueOf(regra.chave);
      cabecalho.add(c.rotulo + " (valor)");
      cabecalho.add(c.rotulo + " (pontos)");
    }
    csv.append(linhaCsv(cabecalho));
    for (RankingDtos.LinhaDoResultado linha : resultado.linhas) {
      List<String> celulas = new ArrayList<>(List.of(
          String.valueOf(linha.posicao), linha.nome == null ? "" : linha.nome, linha.total.toPlainString()));
      for (RankingDtos.ItemDoResultado item : linha.criterios) {
        celulas.add(item.valor.toPlainString());
        celulas.add(item.pontos.toPlainString());
      }
      csv.append(linhaCsv(celulas));
    }
    // BOM: o Excel brasileiro abre com acento certo.
    return ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * A posicao do profissional logado em cada ranking que o dono deixou visivel. So a dele: o
   * calculo usa a equipe toda (a posicao depende dos outros), mas a resposta leva uma linha.
   */
  @Transactional(readOnly = true)
  public RankingDtos.MinhasPosicoes minhaPosicao(String de, String ate) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID userId = usuarioLogado.idOuFalhar();
    Profissional eu = profissionais.findByTenantIdAndUserId(tenantId, userId)
        .orElseThrow(() -> new ApiClientErrorException("Profissional não encontrado para o usuário autenticado", 403));
    Periodo periodo = periodo(de, ate);
    RankingDtos.MinhasPosicoes resposta = new RankingDtos.MinhasPosicoes();
    resposta.de = periodo.inicio().toString();
    resposta.ate = periodo.fim().toString();

    List<RankingProfissionais> visiveis =
        rankings.ativosDoSalao(tenantId).stream().filter(RankingProfissionais::isVisivelParaProfissional).toList();
    if (visiveis.isEmpty()) return resposta;
    List<Metricas> metricas = metricas(tenantId, periodo);
    for (RankingProfissionais ranking : visiveis) {
      List<Linha> linhas = calcular(ranking, metricas);
      for (Linha linha : linhas) {
        if (!linha.profissionalId().equals(eu.getId())) continue;
        RankingDtos.MinhaPosicao minha = new RankingDtos.MinhaPosicao();
        minha.rankingId = ranking.getId().toString();
        minha.nome = ranking.getNome();
        minha.modo = ranking.getModo();
        minha.posicao = linha.posicao();
        minha.participantes = linhas.size();
        minha.total = linha.total();
        minha.criterios = itens(linha);
        resposta.rankings.add(minha);
      }
    }
    return resposta;
  }

  // ---- metricas -----------------------------------------------------------------------------

  /**
   * Todas as metricas, por profissional, numa consulta so. Entram os profissionais ativos e quem,
   * mesmo inativo, teve movimento no periodo (saiu da equipe no meio do mes e ainda conta).
   */
  static final String SQL_DAS_METRICAS =
      "WITH " + ReceitaDeServicosSql.CTE + """
      , receita AS (
        SELECT professional_id, SUM(valor) AS valor
        FROM receita_servicos
        WHERE dia BETWEEN :inicio AND :fim
        GROUP BY professional_id
      ), atendimentos AS (
        SELECT professional_id,
               COUNT(*) FILTER (WHERE status = 'Concluido') AS concluidos,
               COUNT(DISTINCT client_id) FILTER (WHERE status = 'Concluido') AS clientes,
               COUNT(*) FILTER (WHERE status = 'Nao compareceu') AS faltas,
               COUNT(*) FILTER (WHERE status = 'Cancelado') AS cancelamentos
        FROM appointments
        WHERE tenant_id = :tenantId AND date BETWEEN :inicio AND :fim
        GROUP BY professional_id
      ), primeiro_atendimento AS (
        SELECT DISTINCT ON (client_id) client_id, professional_id, date
        FROM appointments
        WHERE tenant_id = :tenantId AND status = 'Concluido'
        ORDER BY client_id, date, start_time
      ), novos AS (
        SELECT professional_id, COUNT(*) AS quantidade
        FROM primeiro_atendimento
        WHERE date BETWEEN :inicio AND :fim
        GROUP BY professional_id
      ), voltaram AS (
        SELECT a.professional_id, COUNT(DISTINCT a.client_id) AS quantidade
        FROM appointments a
        WHERE a.tenant_id = :tenantId AND a.status = 'Concluido' AND a.date BETWEEN :inicio AND :fim
          AND EXISTS (
            SELECT 1 FROM appointments b
            WHERE b.tenant_id = a.tenant_id AND b.client_id = a.client_id
              AND b.professional_id = a.professional_id AND b.status = 'Concluido'
              AND (b.date < a.date OR (b.date = a.date AND b.start_time < a.start_time)))
        GROUP BY a.professional_id
      ), produtos AS (
        SELECT ci.professional_id,
               SUM(CASE WHEN c.subtotal > 0
                        THEN ROUND(ci.total * GREATEST(c.subtotal - c.desconto, 0) / c.subtotal, 2)
                        ELSE 0 END) AS valor
        FROM comandas c
        JOIN comanda_itens ci ON ci.comanda_id = c.id AND ci.tenant_id = c.tenant_id
        WHERE c.tenant_id = :tenantId AND c.status = 'FECHADA' AND ci.tipo = 'PRODUTO'
          AND ci.professional_id IS NOT NULL
          AND (c.closed_at AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN :inicio AND :fim
        GROUP BY ci.professional_id
      ), dias AS (
        SELECT d::date AS dia, EXTRACT(ISODOW FROM d)::int AS dow_iso
        FROM generate_series(CAST(:inicio AS date), CAST(:fim AS date), interval '1 day') d
      ), disponivel AS (
        SELECT wh.professional_id,
               SUM(EXTRACT(EPOCH FROM (wh.end_time - wh.start_time)) / 60) AS minutos
        FROM professional_working_hours wh
        JOIN dias ON wh.day_of_week = dias.dow_iso OR (dias.dow_iso = 7 AND wh.day_of_week = 0)
        WHERE wh.tenant_id = :tenantId AND wh.is_working = true AND wh.start_time < wh.end_time
        GROUP BY wh.professional_id
      ), ocupado AS (
        SELECT professional_id,
               SUM(EXTRACT(EPOCH FROM (end_time::time - start_time::time)) / 60) AS minutos
        FROM appointments
        WHERE tenant_id = :tenantId AND date BETWEEN :inicio AND :fim AND status <> 'Cancelado'
        GROUP BY professional_id
      )
      SELECT p.id, p.name,
             COALESCE(r.valor, 0),
             COALESCE(at.concluidos, 0),
             COALESCE(at.clientes, 0),
             COALESCE(n.quantidade, 0),
             COALESCE(v.quantidade, 0),
             COALESCE(pr.valor, 0),
             CASE WHEN COALESCE(d.minutos, 0) > 0
                  THEN ROUND(CAST(COALESCE(o.minutos, 0) * 100.0 / d.minutos AS numeric), 2) ELSE 0 END,
             COALESCE(at.faltas, 0),
             COALESCE(at.cancelamentos, 0)
      FROM professionals p
      LEFT JOIN receita r ON r.professional_id = p.id
      LEFT JOIN atendimentos at ON at.professional_id = p.id
      LEFT JOIN novos n ON n.professional_id = p.id
      LEFT JOIN voltaram v ON v.professional_id = p.id
      LEFT JOIN produtos pr ON pr.professional_id = p.id
      LEFT JOIN disponivel d ON d.professional_id = p.id
      LEFT JOIN ocupado o ON o.professional_id = p.id
      WHERE p.tenant_id = :tenantId
        AND (p.is_active
             OR COALESCE(r.valor, 0) > 0 OR COALESCE(at.concluidos, 0) > 0 OR COALESCE(pr.valor, 0) > 0)
      """;

  List<Metricas> metricas(UUID tenantId, Periodo periodo) {
    @SuppressWarnings("unchecked")
    List<Object[]> linhas = entityManager.createNativeQuery(SQL_DAS_METRICAS)
        .setParameter("tenantId", tenantId)
        .setParameter("inicio", periodo.inicio())
        .setParameter("fim", periodo.fim())
        .getResultList();
    List<Metricas> metricas = new ArrayList<>();
    for (Object[] l : linhas) {
      Map<Criterio, BigDecimal> valores = new EnumMap<>(Criterio.class);
      BigDecimal faturamento = numero(l[2]);
      BigDecimal concluidos = numero(l[3]);
      valores.put(Criterio.FATURAMENTO_SERVICOS, faturamento);
      valores.put(Criterio.ATENDIMENTOS_CONCLUIDOS, concluidos);
      valores.put(Criterio.CLIENTES_ATENDIDOS, numero(l[4]));
      valores.put(Criterio.CLIENTES_NOVOS, numero(l[5]));
      valores.put(Criterio.CLIENTES_QUE_VOLTARAM, numero(l[6]));
      valores.put(Criterio.VENDA_DE_PRODUTOS, numero(l[7]));
      valores.put(Criterio.OCUPACAO, numero(l[8]));
      valores.put(Criterio.FALTAS, numero(l[9]));
      valores.put(Criterio.CANCELAMENTOS, numero(l[10]));
      valores.put(Criterio.TICKET_MEDIO, concluidos.signum() > 0
          ? faturamento.divide(concluidos, 2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO);
      metricas.add(new Metricas(uuid(l[0]), (String) l[1], valores));
    }
    return metricas;
  }

  // ---- apoio --------------------------------------------------------------------------------

  record Periodo(LocalDate inicio, LocalDate fim) {}

  /** Sem datas: do dia 1 do mes ate hoje (a mesma escolha das outras telas de relatorio). */
  static Periodo periodo(String de, String ate) {
    LocalDate hoje = LocalDate.now(FUSO);
    LocalDate inicio;
    LocalDate fim;
    try {
      inicio = de == null || de.isBlank() ? hoje.withDayOfMonth(1) : LocalDate.parse(de.trim().substring(0, 10));
      fim = ate == null || ate.isBlank() ? hoje : LocalDate.parse(ate.trim().substring(0, 10));
    } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
      throw new ApiClientErrorException("Período inválido. Use datas no formato aaaa-mm-dd.", 400);
    }
    if (fim.isBefore(inicio)) throw new ApiClientErrorException("A data final vem antes da inicial.", 400);
    if (ChronoUnit.DAYS.between(inicio, fim) + 1 > MAXIMO_DE_DIAS) {
      throw new ApiClientErrorException("Escolha um período de até 1 ano.", 400);
    }
    return new Periodo(inicio, fim);
  }

  private List<Linha> calcular(RankingProfissionais ranking, List<Metricas> metricas) {
    List<Regra> regras = regras(ranking).stream()
        .map(r -> new Regra(r.chave, r.pontos, r.aCada, r.peso))
        .toList();
    return CalculoDoRanking.calcular(ranking.getModo(), regras, metricas);
  }

  private void validar(UUID tenantId, RankingDtos.RankingRequest pedido, UUID ignorar) {
    if (pedido == null) throw new ApiClientErrorException("Informe o ranking.", 400);
    List<Regra> regras = pedido.criterios == null ? List.of()
        : pedido.criterios.stream().map(r -> new Regra(r.chave, r.pontos, r.aCada, r.peso)).toList();
    String modo = pedido.modo == null ? null : pedido.modo.trim().toUpperCase();
    List<String> problemas = CalculoDoRanking.validar(pedido.nome, modo, regras);
    if (problemas.isEmpty() && rankings.nomeEmUso(tenantId, pedido.nome.trim(), ignorar)) {
      problemas = List.of("Já existe um ranking com o nome \"" + pedido.nome.trim() + "\".");
    }
    if (!problemas.isEmpty()) throw new ApiClientErrorException(String.join(" ", problemas), 400);
  }

  private void aplicar(RankingProfissionais ranking, RankingDtos.RankingRequest pedido) {
    String modo = pedido.modo.trim().toUpperCase();
    List<RankingDtos.Regra> limpas = new ArrayList<>();
    for (RankingDtos.Regra r : pedido.criterios) {
      RankingDtos.Regra limpa = new RankingDtos.Regra();
      limpa.chave = r.chave.trim().toUpperCase();
      if (CalculoDoRanking.PONTOS.equals(modo)) {
        limpa.pontos = r.pontos;
        limpa.aCada = r.aCada;
      } else {
        limpa.peso = r.peso;
      }
      limpas.add(limpa);
    }
    ranking.setNome(pedido.nome.trim());
    ranking.setModo(modo);
    ranking.setVisivelParaProfissional(pedido.visivelParaProfissional);
    try {
      ranking.setCriteriosJson(json.writeValueAsString(limpas));
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gravar os critérios do ranking", e);
    }
  }

  private List<RankingDtos.Regra> regras(RankingProfissionais ranking) {
    try {
      return json.readValue(ranking.getCriteriosJson(), new TypeReference<List<RankingDtos.Regra>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Critérios do ranking ilegíveis (id=" + ranking.getId() + ")", e);
    }
  }

  private RankingProfissionais doSalao(UUID id, UUID tenantId) {
    return rankings.findByIdAndTenantIdAndAtivoTrue(id, tenantId)
        .orElseThrow(() -> new ApiClientErrorException("Ranking não encontrado.", 404));
  }

  private RankingDtos.Ranking paraDto(RankingProfissionais ranking) {
    RankingDtos.Ranking dto = new RankingDtos.Ranking();
    dto.id = ranking.getId() == null ? null : ranking.getId().toString();
    dto.nome = ranking.getNome();
    dto.modo = ranking.getModo();
    dto.criterios = regras(ranking);
    dto.visivelParaProfissional = ranking.isVisivelParaProfissional();
    dto.createdAt = ranking.getCreatedAt() == null ? null : ranking.getCreatedAt().toString();
    dto.updatedAt = ranking.getUpdatedAt() == null ? null : ranking.getUpdatedAt().toString();
    return dto;
  }

  private static List<RankingDtos.ItemDoResultado> itens(Linha linha) {
    List<RankingDtos.ItemDoResultado> itens = new ArrayList<>();
    for (CalculoDoRanking.Item item : linha.criterios()) {
      Criterio c = Criterio.valueOf(item.chave());
      RankingDtos.ItemDoResultado dto = new RankingDtos.ItemDoResultado();
      dto.chave = item.chave();
      dto.rotulo = c.rotulo;
      dto.unidade = c.unidade;
      dto.valor = item.valor();
      dto.pontos = item.pontos();
      itens.add(dto);
    }
    return itens;
  }

  /** A foto guardada de cada profissional DESTE salao (chave do storage ou URL). */
  private Map<UUID, String> fotosDoSalao(UUID tenantId) {
    @SuppressWarnings("unchecked")
    List<Object[]> linhas = entityManager
        .createNativeQuery("SELECT id, avatar FROM professionals WHERE tenant_id = :tenantId AND avatar IS NOT NULL")
        .setParameter("tenantId", tenantId)
        .getResultList();
    Map<UUID, String> mapa = new LinkedHashMap<>();
    for (Object[] l : linhas) mapa.put(uuid(l[0]), (String) l[1]);
    return mapa;
  }

  private void auditar(UUID tenantId, String acao, UUID id, Object antes, Object depois) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "PROFESSIONAL_RANKING";
    command.entityId = id == null ? null : id.toString();
    command.sourceChannel = AuditConstants.SourceChannel.API;
    command.before = antes;
    command.after = depois;
    auditService.recordSuccess(command);
  }

  private static String linhaCsv(List<String> celulas) {
    StringBuilder linha = new StringBuilder();
    for (int i = 0; i < celulas.size(); i++) {
      if (i > 0) linha.append(';');
      String valor = CelulaCsv.semFormula(celulas.get(i) == null ? "" : celulas.get(i));
      if (valor.contains(";") || valor.contains("\"") || valor.contains("\n")) {
        valor = "\"" + valor.replace("\"", "\"\"") + "\"";
      }
      linha.append(valor);
    }
    return linha.append("\r\n").toString();
  }

  private static BigDecimal numero(Object valor) {
    if (valor == null) return BigDecimal.ZERO;
    if (valor instanceof BigDecimal b) return b;
    if (valor instanceof Number n) return new BigDecimal(n.toString());
    return new BigDecimal(valor.toString());
  }

  private static UUID uuid(Object valor) {
    return valor instanceof UUID u ? u : UUID.fromString(valor.toString());
  }
}
