package br.com.phdigitalcode.azzo.agenda.pro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * A tela de auditoria ficava sem os filtros: `:from IS NULL` numa consulta nativa nao da ao
 * Postgres como deduzir o tipo do parametro, e ele recusa a consulta INTEIRA — com ou sem valor
 * ("could not determine data type of parameter $3", 2026-09-20). Toda comparacao com data nesta
 * consulta precisa de CAST explicito.
 */
class AuditEventRepositoryQueryTest {

  @Test
  @DisplayName("a consulta dos filtros converte as datas explicitamente")
  void consultaDeFiltrosConverteAsDatas() throws Exception {
    Method metodo =
        AuditEventRepository.class.getMethod(
            "findFilterOptionRows", java.util.UUID.class, String.class,
            java.time.Instant.class, java.time.Instant.class);
    // Sem os comentarios: eles CITAM o padrao errado para explicar por que ele nao pode voltar.
    String sql =
        metodo.getAnnotation(Query.class).value().lines()
            .filter(linha -> !linha.strip().startsWith("--"))
            .collect(java.util.stream.Collectors.joining("\n"));

    assertThat(sql).contains("CAST(:from AS timestamptz)").contains("CAST(:to AS timestamptz)");
    assertThat(sql).doesNotContain(":from IS NULL").doesNotContain(":to IS NULL");
  }
}
