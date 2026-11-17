package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ServicoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServiceCategoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Defeitos medios e baixos da analise de 15/11/2026. */
class DefeitosMediosEBaixosTest {

  private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

  // ─── M1 ────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("M1: a agenda aceita lancar ate 7 dias atras e recusa data mais antiga")
  void agendaRecusaDataAntiga() {
    assertThatCode(() -> ServicoAgendamentos.exigirDataDentroDaJanela(HOJE)).doesNotThrowAnyException();
    assertThatCode(() -> ServicoAgendamentos.exigirDataDentroDaJanela(HOJE.minusDays(7)))
        .doesNotThrowAnyException();
    assertThatCode(() -> ServicoAgendamentos.exigirDataDentroDaJanela(HOJE.plusYears(3)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> ServicoAgendamentos.exigirDataDentroDaJanela(HOJE.minusDays(8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("até 7 dias atrás");
    assertThatThrownBy(() -> ServicoAgendamentos.exigirDataDentroDaJanela(LocalDate.of(2020, 1, 10)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ─── M4 ────────────────────────────────────────────────────────────────────

  private ServicoService servicoServiceCom(Servico... existentes) {
    UUID tenantId = existentes.length > 0 ? existentes[0].getTenantId() : UUID.randomUUID();
    ServicoRepository repo = mock(ServicoRepository.class);
    ContextoTenant contexto = mock(ContextoTenant.class);
    when(contexto.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repo.findByTenantId(tenantId)).thenReturn(List.of(existentes));
    when(repo.save(any(Servico.class))).thenAnswer(inv -> {
      Servico s = inv.getArgument(0);
      if (s.getId() == null) s.setId(UUID.randomUUID());
      return s;
    });
    for (Servico s : existentes) {
      when(repo.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(java.util.Optional.of(s));
    }
    return new ServicoService(
        repo, mock(ProfissionalRepository.class), mock(ServiceCategoryRepository.class), contexto);
  }

  private static Servico servico(UUID tenantId, String nome, boolean ativo) {
    Servico s = new Servico();
    s.setId(UUID.randomUUID());
    s.setTenantId(tenantId);
    s.setName(nome);
    s.setDuration(30);
    s.setPrice(new BigDecimal("50.00"));
    s.setActive(ativo);
    return s;
  }

  private static ServicoRequest pedido(String nome) {
    ServicoRequest req = new ServicoRequest();
    req.name = nome;
    req.duration = 30;
    req.price = new BigDecimal("50.00");
    return req;
  }

  @Test
  @DisplayName("M4: servico novo com o nome de outro ativo e recusado, sem olhar maiusculas e espacos")
  void servicoComNomeRepetidoERecusado() {
    UUID tenant = UUID.randomUUID();
    ServicoService service = servicoServiceCom(servico(tenant, "Corte Feminino", true));

    assertThatThrownBy(() -> service.criar(pedido("  corte feminino ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Já existe um serviço ativo com o nome");
  }

  @Test
  @DisplayName("M4: o nome de um servico INATIVO pode ser usado de novo")
  void nomeDeServicoInativoPodeSerReusado() {
    UUID tenant = UUID.randomUUID();
    ServicoService service = servicoServiceCom(servico(tenant, "Corte Feminino", false));

    assertThat(service.criar(pedido("Corte Feminino"))).isNotNull();
  }

  @Test
  @DisplayName("M4: editar um servico sem trocar o nome continua aceito, mesmo com repetido antigo")
  void editarSemTrocarONomeNaoConflita() {
    UUID tenant = UUID.randomUUID();
    Servico antigo = servico(tenant, "Escova", true);
    Servico repetido = servico(tenant, "Escova", true);
    ServicoService service = servicoServiceCom(antigo, repetido);

    ServicoRequest req = pedido("Escova");
    req.price = new BigDecimal("60.00");
    assertThat(service.atualizar(antigo.getId(), req)).isNotNull();
  }

  // ─── B4 ────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("B4: o valor das variaveis entra escapado no corpo do e-mail")
  void valoresDoEmailSaoEscapados() {
    Map<String, String> escapados =
        EmailTemplateRendererService.escaparValores(Map.of("userName", "<script>alert(1)</script> & 'x'"));
    assertThat(escapados.get("userName"))
        .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &#39;x&#39;");
    assertThat(EmailTemplateRendererService.escaparHtml(null)).isNull();
  }
}
