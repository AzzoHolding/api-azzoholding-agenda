package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Testa {@link ServicoCatalogoRelatorios} — validacao de chave de relatorio e de periodo, que
 * disparam antes de qualquer consulta ao {@code EntityManager} (por isso nao precisam de mock de
 * persistencia). As 14 definicoes de SQL nativo em si nao sao exercitadas aqui pelo mesmo motivo
 * documentado em {@code ServicoRelatoriosTest}: dependem de tabelas reais.
 */
@ExtendWith(MockitoExtension.class)
class ServicoCatalogoRelatoriosTest {

  @Mock private ContextoTenant contextoTenant;

  private ServicoCatalogoRelatorios service;

  @BeforeEach
  void setUp() {
    service = new ServicoCatalogoRelatorios(contextoTenant);
    lenient().when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(UUID.randomUUID());

    // execute() so e alcancado pelo teste de formato desconhecido (todos os outros lancam antes de
    // tocar o EntityManager) — mock minimo que devolve lista vazia para qualquer query nativa.
    EntityManager entityManager = mock(EntityManager.class);
    Query query = mock(Query.class);
    lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    lenient().when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
    lenient().when(query.getResultList()).thenReturn(List.of());
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
  }

  @Test
  @DisplayName("rejeita chave de relatorio nao suportada")
  void rejeitaChaveNaoSuportada() {
    assertThatThrownBy(() -> service.gerar("relatorio-inexistente", null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Relatorio nao suportado");
  }

  @Test
  @DisplayName("rejeita periodo invertido mesmo com chave valida")
  void rejeitaPeriodoInvertido() {
    assertThatThrownBy(
            () -> service.gerar("ranking-servicos", "2026-02-10", "2026-02-01", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Periodo invalido");
  }

  @Test
  @DisplayName("rejeita periodo maior que 370 dias")
  void rejeitaPeriodoMuitoLongo() {
    assertThatThrownBy(
            () -> service.gerar("ranking-servicos", "2026-01-01", "2027-02-01", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximo de 370 dias");
  }

  @Test
  @DisplayName("rejeita formato de exportacao desconhecido")
  void rejeitaFormatoDesconhecido() {
    assertThatThrownBy(
            () -> service.exportar("ranking-servicos", "2026-02-01", "2026-02-10", null, "pdf", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Formato invalido");
  }

  @Test
  @DisplayName("todas as 14 chaves do catalogo resolvem uma definicao sem lancar excecao")
  void todasAsChavesResolvem() {
    String[] chaves = {
      "faturamento-servico",
      "faturamento-profissional",
      "faturamento-meio-pagamento",
      "novos-clientes",
      "clientes-inativos",
      "taxa-retorno",
      "no-show-cancelamentos",
      "ocupacao-profissional",
      "ranking-servicos",
      "curva-abc-clientes",
      "descontos",
      "comissoes-periodo",
      "vendas-produtos",
      "aniversariantes"
    };
    for (String chave : chaves) {
      assertThat(service.definition(chave).key()).isEqualTo(chave);
    }
  }
}
