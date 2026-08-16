package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatorioComissaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatorioDiarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoItem;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionEntry;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CommissionEntryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalWorkingHourRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TransacaoQueryRepository.SummaryTotals;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Testa {@link ServicoRelatorios} — {@code diario}, {@code comissoes} (resolucao de percentual
 * unico) e as validacoes de periodo do {@code heatmap}. As consultas de SQL nativo puro
 * ({@code relatorioEstoque}/{@code relatorioVendas}/{@code relatorioClientes}/{@code relatorioGerencial})
 * dependem de {@code EntityManager.createNativeQuery} e nao sao exercitadas aqui — o proprio
 * original tambem nao tinha testes para {@code ServicoRelatorios} (nenhum {@code @QuarkusTest}
 * cobre essas consultas nativas contra as tabelas reais).
 */
@ExtendWith(MockitoExtension.class)
class ServicoRelatoriosTest {

  @Mock private ContextoTenant contextoTenant;
  @Mock private AgendamentoRepository agendamentoRepository;
  @Mock private TransacaoQueryRepository transacaoQueryRepository;
  @Mock private ProfissionalRepository profissionalRepository;
  @Mock private ProfissionalWorkingHourRepository profissionalWorkingHourRepository;
  @Mock private CommissionEntryRepository commissionEntryRepository;

  private ServicoRelatorios service;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    service =
        new ServicoRelatorios(
            contextoTenant,
            agendamentoRepository,
            transacaoQueryRepository,
            profissionalRepository,
            profissionalWorkingHourRepository,
            commissionEntryRepository);
    tenantId = UUID.randomUUID();
    lenient().when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  @Nested
  @DisplayName("diario")
  class Diario {

    @Test
    @DisplayName("calcula saldo como receita menos despesa e conta os agendamentos do dia")
    void calculaSaldo() {
      when(agendamentoRepository.listByTenantAndDate(eq(tenantId), any(), any(Pageable.class)))
          .thenReturn(List.of(new Agendamento(), new Agendamento()));
      when(transacaoQueryRepository.summarizeFiltered(any()))
          .thenReturn(new SummaryTotals(new BigDecimal("300.00"), new BigDecimal("80.00")));

      RelatorioDiarioResponse response = service.diario("2026-02-10");

      assertThat(response.date).isEqualTo("2026-02-10");
      assertThat(response.totalAppointments).isEqualTo(2);
      assertThat(response.totalRevenue).isEqualByComparingTo("300.00");
      assertThat(response.totalExpenses).isEqualByComparingTo("80.00");
      assertThat(response.balance).isEqualByComparingTo("220.00");
    }
  }

  @Nested
  @DisplayName("comissoes")
  class Comissoes {

    @Test
    @DisplayName("rejeita periodo invertido")
    void rejeitaPeriodoInvertido() {
      assertThatThrownBy(() -> service.comissoes("2026-02-10", "2026-02-01", UUID.randomUUID().toString(), null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("exige professionalId ou professionalUserId")
    void exigeProfessionalId() {
      assertThatThrownBy(() -> service.comissoes("2026-02-01", "2026-02-10", null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("professionalId obrigatorio");
    }

    @Test
    @DisplayName("resolve taxa unica quando todas as entradas nao revertidas tem o mesmo percentual")
    void resolveTaxaUnica() {
      UUID profissionalId = UUID.randomUUID();

      when(agendamentoRepository.listByTenantAndProfessional(eq(tenantId), eq(profissionalId), any(Pageable.class)))
          .thenReturn(List.of());

      CommissionEntry entry1 = new CommissionEntry();
      entry1.setEntryStatus("OPEN");
      entry1.setPercentValue(new BigDecimal("10.00"));
      entry1.setTotalAmountCents(1000L);

      CommissionEntry entry2 = new CommissionEntry();
      entry2.setEntryStatus("PAID");
      entry2.setPercentValue(new BigDecimal("10.00"));
      entry2.setTotalAmountCents(500L);

      CommissionEntry reversed = new CommissionEntry();
      reversed.setEntryStatus("REVERSED");
      reversed.setPercentValue(new BigDecimal("99.00"));
      reversed.setTotalAmountCents(9999L);

      when(commissionEntryRepository.listByTenantAndProfessionalAndCreatedAtRange(
              eq(tenantId), eq(profissionalId), any(Instant.class), any(Instant.class)))
          .thenReturn(List.of(entry1, entry2, reversed));

      RelatorioComissaoResponse response = service.comissoes("2026-02-01", "2026-02-10", profissionalId.toString(), null);

      assertThat(response.commissionRate).isEqualByComparingTo("10.00");
      // soma apenas as nao revertidas: 1000 + 500 = 1500 centavos = 15.00
      assertThat(response.commissionValue).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("taxa e null quando ha mais de um percentual distinto entre as entradas")
    void taxaNulaComPercentuaisDivergentes() {
      UUID profissionalId = UUID.randomUUID();

      when(agendamentoRepository.listByTenantAndProfessional(eq(tenantId), eq(profissionalId), any(Pageable.class)))
          .thenReturn(List.of());

      CommissionEntry entry1 = new CommissionEntry();
      entry1.setEntryStatus("OPEN");
      entry1.setPercentValue(new BigDecimal("10.00"));
      entry1.setTotalAmountCents(1000L);

      CommissionEntry entry2 = new CommissionEntry();
      entry2.setEntryStatus("OPEN");
      entry2.setPercentValue(new BigDecimal("20.00"));
      entry2.setTotalAmountCents(500L);

      when(commissionEntryRepository.listByTenantAndProfessionalAndCreatedAtRange(
              eq(tenantId), eq(profissionalId), any(Instant.class), any(Instant.class)))
          .thenReturn(List.of(entry1, entry2));

      RelatorioComissaoResponse response = service.comissoes("2026-02-01", "2026-02-10", profissionalId.toString(), null);

      assertThat(response.commissionRate).isNull();
    }

    @Test
    @DisplayName("soma apenas a receita de agendamentos concluidos dentro do periodo")
    void somaReceitaApenasConcluidosNoPeriodo() {
      UUID profissionalId = UUID.randomUUID();

      Agendamento concluidoDentro = agendamentoComPreco(LocalDate.of(2026, 2, 5), StatusAgendamento.COMPLETED, "100.00");
      Agendamento concluidoForaPeriodo = agendamentoComPreco(LocalDate.of(2026, 3, 1), StatusAgendamento.COMPLETED, "999.00");
      Agendamento canceladoDentro = agendamentoComPreco(LocalDate.of(2026, 2, 6), StatusAgendamento.CANCELLED, "50.00");

      when(agendamentoRepository.listByTenantAndProfessional(eq(tenantId), eq(profissionalId), any(Pageable.class)))
          .thenReturn(List.of(concluidoDentro, concluidoForaPeriodo, canceladoDentro));
      when(commissionEntryRepository.listByTenantAndProfessionalAndCreatedAtRange(
              eq(tenantId), eq(profissionalId), any(Instant.class), any(Instant.class)))
          .thenReturn(List.of());

      RelatorioComissaoResponse response = service.comissoes("2026-02-01", "2026-02-28", profissionalId.toString(), null);

      assertThat(response.totalRevenue).isEqualByComparingTo("100.00");
    }

    private Agendamento agendamentoComPreco(LocalDate date, StatusAgendamento status, String price) {
      Agendamento agendamento = new Agendamento();
      agendamento.setDate(date);
      agendamento.setStatus(status);
      AgendamentoItem item = new AgendamentoItem();
      item.setTotalPrice(new BigDecimal(price));
      agendamento.setItems(new java.util.ArrayList<>(List.of(item)));
      return agendamento;
    }
  }

  @Nested
  @DisplayName("heatmap")
  class Heatmap {

    @Test
    @DisplayName("exige periodo (dataInicio/dataFim obrigatorios)")
    void exigePeriodo() {
      assertThatThrownBy(() -> service.heatmap(null, "2026-02-10", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita periodo invertido")
    void rejeitaPeriodoInvertido() {
      assertThatThrownBy(() -> service.heatmap("2026-02-10", "2026-02-01", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejeita periodo maior que 370 dias")
    void rejeitaPeriodoMuitoLongo() {
      assertThatThrownBy(() -> service.heatmap("2026-01-01", "2027-02-01", null)).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
