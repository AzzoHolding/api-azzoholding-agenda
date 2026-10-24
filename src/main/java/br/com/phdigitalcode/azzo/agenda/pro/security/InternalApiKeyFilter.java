package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Porte de {@code modules/security/infrastructure/InternalApiKeyFilter.java}.
 *
 * <p>Autenticacao dos endpoints internos ({@code /api/v1/internal/}), usados na comunicacao
 * service-to-service (assistant API / api-gerenciamento -> backend principal). Valida o header
 * {@code X-Internal-Api-Key} contra {@code app.internal.api-key} em tempo constante.
 *
 * <p>Estes paths continuam em {@code permitAll} no {@code SecurityConfig} (nao ha JWT nessas
 * chamadas, exatamente como no original, onde {@code quarkus.http.auth.permission.public.paths}
 * inclui {@code /api/v1/internal/*}) — quem autentica e este filtro.
 *
 * <p><b>Uma chave por servico (SEG-018).</b> Antes havia uma chave so, global, que abria todo
 * {@code /api/v1/internal/**}: quem a tivesse (vazamento, ou um bug no assistant) chegava aos
 * endpoints de qualquer servico. Agora a chave do assistant so vale em
 * {@code /api/v1/internal/assistant/**} e a da Gestao so em {@code /api/v1/internal/plans/**}. A
 * chave unica antiga continua aceita enquanto {@code app.internal.aceitar-chave-legada} for
 * {@code true} (transicao: os servicos passam a usar as chaves novas, e so entao ela e desligada). O
 * servico que chamou fica no atributo {@link #ATRIBUTO_CHAMADOR}, para log e auditoria.
 *
 * <p>O path e comparado NORMALIZADO ({@link CaminhoDaRequisicao}): decodificado, sem context path e
 * sem parametros de caminho. A URI bruta deixava passar {@code /api/v1/%69nternal/...} sem a chave,
 * enquanto o roteador a entregava ao mesmo controller (achado SEG-001).
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(InternalApiKeyFilter.class);
  private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";
  private static final String HEADER_NAME = "X-Internal-Api-Key";

  /**
   * Marca a requisicao que passou pela chave interna. {@link ContextoTenant} so aceita o salao pelo
   * header {@code X-Tenant-Id} com esta marca — nunca de quem chama sem autenticacao.
   */
  public static final String ATRIBUTO_CHAMADA_INTERNA =
      InternalApiKeyFilter.class.getName() + ".autenticada";

  /** Nome do servico que chamou ({@code assistant}, {@code gerenciamento} ou {@code legado}). */
  public static final String ATRIBUTO_CHAMADOR = InternalApiKeyFilter.class.getName() + ".chamador";

  static final String CHAMADOR_LEGADO = "legado";

  /** Chave de um servico e o unico prefixo de caminho em que ela vale. */
  record ChaveDeServico(String servico, String chave, String prefixo) {}

  private final String configuredApiKey;
  private final boolean aceitarChaveLegada;
  private final java.util.List<ChaveDeServico> chavesDeServico;

  // @Autowired obrigatorio: com dois construtores, o Spring nao escolhe sozinho e a aplicacao nao sobe
  // (queda de 20/10/2026, os testes unitarios instanciam na mao e nao pegaram).
  @Autowired
  public InternalApiKeyFilter(
      @Value("${app.internal.api-key:changeme-dev}") String configuredApiKey,
      @Value("${app.internal.chaves.assistant:}") String chaveAssistant,
      @Value("${app.internal.chaves.gerenciamento:}") String chaveGerenciamento,
      @Value("${app.internal.aceitar-chave-legada:true}") boolean aceitarChaveLegada) {
    this.configuredApiKey = configuredApiKey;
    this.aceitarChaveLegada = aceitarChaveLegada;
    java.util.List<ChaveDeServico> chaves = new java.util.ArrayList<>();
    if (chaveAssistant != null && !chaveAssistant.isBlank()) {
      chaves.add(new ChaveDeServico("assistant", chaveAssistant.trim(), INTERNAL_PATH_PREFIX + "assistant/"));
    }
    if (chaveGerenciamento != null && !chaveGerenciamento.isBlank()) {
      chaves.add(new ChaveDeServico("gerenciamento", chaveGerenciamento.trim(), INTERNAL_PATH_PREFIX + "plans/"));
    }
    this.chavesDeServico = java.util.List.copyOf(chaves);
    if (chavesDeServico.isEmpty()) {
      LOG.warn("SEGURANCA: chaves internas por servico nao configuradas; so a chave unica (SEG-018).");
    } else if (aceitarChaveLegada) {
      LOG.warn("SEGURANCA: chave interna unica ainda aceita junto das chaves por servico (desligar com"
          + " INTERNAL_API_KEY_LEGADA_ATIVA=false quando todos os servicos usarem a propria).");
    }
  }

  /** So a chave unica, como antes (testes). */
  InternalApiKeyFilter(String configuredApiKey) {
    this(configuredApiKey, null, null, true);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Caminho como o roteador o ve (SEG-001): "/api/v1/%69nternal/..." tambem exige a chave.
    String path = CaminhoDaRequisicao.normalizado(request);
    if (!(path + "/").startsWith(INTERNAL_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String providedKey = request.getHeader(HEADER_NAME);
    if (providedKey == null || providedKey.isBlank()) {
      LOG.warn("Requisicao interna rejeitada: header {} ausente. Path: {}", HEADER_NAME, path);
      reject(response, "Header " + HEADER_NAME + " obrigatorio para endpoints internos");
      return;
    }

    String chave = providedKey.trim();
    String chamador = null;
    for (ChaveDeServico servico : chavesDeServico) {
      if (constantTimeEquals(servico.chave(), chave)) {
        if (!(path + "/").startsWith(servico.prefixo())) {
          LOG.warn("Requisicao interna rejeitada: chave do {} fora do escopo dele. Path: {}", servico.servico(), path);
          reject(response, HttpStatus.FORBIDDEN, "Chave interna sem acesso a este endpoint");
          return;
        }
        chamador = servico.servico();
        break;
      }
    }
    if (chamador == null && aceitarChaveLegada && constantTimeEquals(configuredApiKey, chave)) {
      chamador = CHAMADOR_LEGADO;
    }
    if (chamador == null) {
      LOG.warn("Requisicao interna rejeitada: chave invalida. Path: {}", path);
      reject(response, "Chave interna invalida");
      return;
    }

    request.setAttribute(ATRIBUTO_CHAMADA_INTERNA, Boolean.TRUE);
    request.setAttribute(ATRIBUTO_CHAMADOR, chamador);
    filterChain.doFilter(request, response);
  }

  private static void reject(HttpServletResponse response, String message) throws IOException {
    reject(response, HttpStatus.UNAUTHORIZED, message);
  }

  private static void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
    response.setStatus(status.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
    response.getWriter().flush();
  }

  private static boolean constantTimeEquals(String expected, String provided) {
    if (expected == null || provided == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        provided.getBytes(StandardCharsets.UTF_8));
  }
}
