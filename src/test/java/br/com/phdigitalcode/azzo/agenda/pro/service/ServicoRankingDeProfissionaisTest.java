package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.RankingDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RankingProfissionais;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RankingProfissionaisRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Criterio;
import br.com.phdigitalcode.azzo.agenda.pro.service.CalculoDoRanking.Metricas;

class ServicoRankingDeProfissionaisTest {

  private final UUID tenant = UUID.randomUUID();
  private final UUID usuarioDaBia = UUID.randomUUID();
  private RankingProfissionaisRepository rankings;
  private ProfissionalRepository profissionais;
  private AuditService audit;
  private ServicoRankingDeProfissionais servico;
  private Profissional bia;

  @BeforeEach
  void setUp() {
    rankings = mock(RankingProfissionaisRepository.class);
    profissionais = mock(ProfissionalRepository.class);
    audit = mock(AuditService.class);
    ContextoTenant contexto = mock(ContextoTenant.class);
    when(contexto.obterTenantIdOuFalhar()).thenReturn(tenant);
    AuthenticatedUser logado = mock(AuthenticatedUser.class);
    when(logado.idOuFalhar()).thenReturn(usuarioDaBia);
    servico = spy(new ServicoRankingDeProfissionais(rankings, profissionais, contexto, logado, audit, new ObjectMapper()));

    bia = new Profissional();
    bia.setId(UUID.randomUUID());
    bia.setTenantId(tenant);
    bia.setName("Bia");
    when(profissionais.findByTenantIdAndUserId(tenant, usuarioDaBia)).thenReturn(Optional.of(bia));
  }

  private RankingProfissionais ranking(String nome, boolean visivel) {
    RankingProfissionais r = new RankingProfissionais();
    r.setId(UUID.randomUUID());
    r.setTenantId(tenant);
    r.setNome(nome);
    r.setModo("PONTOS");
    r.setCriteriosJson("[{\"chave\":\"ATENDIMENTOS_CONCLUIDOS\",\"pontos\":1,\"aCada\":1}]");
    r.setVisivelParaProfissional(visivel);
    return r;
  }

  private Metricas m(UUID id, String nome, int atendimentos) {
    Map<Criterio, BigDecimal> v = new EnumMap<>(Criterio.class);
    v.put(Criterio.ATENDIMENTOS_CONCLUIDOS, BigDecimal.valueOf(atendimentos));
    return new Metricas(id, nome, v);
  }

  @Test
  void oProfissionalVeSoAPropriaPosicaoESoNosRankingsVisiveis() {
    when(rankings.ativosDoSalao(tenant)).thenReturn(List.of(ranking("Aberto", true), ranking("Só do dono", false)));
    doReturn(List.of(m(UUID.randomUUID(), "Ana", 9), m(bia.getId(), "Bia", 5), m(UUID.randomUUID(), "Carla", 1)))
        .when(servico).metricas(eq(tenant), any());

    RankingDtos.MinhasPosicoes resposta = servico.minhaPosicao("2026-10-01", "2026-10-31");

    assertThat(resposta.rankings).hasSize(1);
    RankingDtos.MinhaPosicao minha = resposta.rankings.get(0);
    assertThat(minha.nome).isEqualTo("Aberto");
    assertThat(minha.posicao).isEqualTo(2);
    assertThat(minha.participantes).isEqualTo(3);
    assertThat(minha.total).isEqualByComparingTo("5");
    // Nenhum dado dos colegas sai na resposta.
    assertThat(minha.toString() + minha.criterios).doesNotContain("Ana").doesNotContain("Carla");
  }

  @Test
  void semRankingVisivelNaoCalculaNada() {
    when(rankings.ativosDoSalao(tenant)).thenReturn(List.of(ranking("Só do dono", false)));
    assertThat(servico.minhaPosicao(null, null).rankings).isEmpty();
    verify(servico, never()).metricas(any(), any());
  }

  @Test
  void usuarioSemProfissionalVinculadoRecebe403() {
    when(profissionais.findByTenantIdAndUserId(tenant, usuarioDaBia)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servico.minhaPosicao(null, null))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus()).isEqualTo(403);
  }

  @Test
  void criarRecusaNomeRepetidoEPesoQueNaoSoma100() {
    RankingDtos.RankingRequest pedido = new RankingDtos.RankingRequest();
    pedido.nome = "Vendas";
    pedido.modo = "peso";
    RankingDtos.Regra regra = new RankingDtos.Regra();
    regra.chave = "FATURAMENTO_SERVICOS";
    regra.peso = BigDecimal.valueOf(80);
    pedido.criterios = List.of(regra);
    assertThatThrownBy(() -> servico.criar(pedido)).hasMessageContaining("somam 80%");

    regra.peso = BigDecimal.valueOf(100);
    when(rankings.nomeEmUso(tenant, "Vendas", null)).thenReturn(true);
    assertThatThrownBy(() -> servico.criar(pedido)).hasMessageContaining("Já existe um ranking");
    verify(rankings, never()).save(any());
  }

  @Test
  void criarGuardaSoOsCamposDoModoEAudita() {
    RankingDtos.RankingRequest pedido = new RankingDtos.RankingRequest();
    pedido.nome = "  Campeão de vendas ";
    pedido.modo = "PONTOS";
    RankingDtos.Regra regra = new RankingDtos.Regra();
    regra.chave = "faturamento_servicos";
    regra.pontos = BigDecimal.ONE;
    regra.aCada = BigDecimal.valueOf(100);
    regra.peso = BigDecimal.TEN; // ignorado no modo PONTOS
    pedido.criterios = List.of(regra);

    RankingDtos.Ranking salvo = servico.criar(pedido);

    assertThat(salvo.nome).isEqualTo("Campeão de vendas");
    assertThat(salvo.criterios.get(0).chave).isEqualTo("FATURAMENTO_SERVICOS");
    assertThat(salvo.criterios.get(0).peso).isNull();
    verify(audit).recordSuccess(any());
  }

  @Test
  void periodoInvalidoOuLongoDemaisERecusado() {
    assertThatThrownBy(() -> ServicoRankingDeProfissionais.periodo("2026-10-31", "2026-10-01"))
        .hasMessageContaining("vem antes");
    assertThatThrownBy(() -> ServicoRankingDeProfissionais.periodo("2024-01-01", "2026-01-01"))
        .hasMessageContaining("até 1 ano");
    assertThatThrownBy(() -> ServicoRankingDeProfissionais.periodo("ontem", null)).hasMessageContaining("aaaa-mm-dd");
  }
}
