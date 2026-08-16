package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentSchedulingSettingsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentSchedulingSettingsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoConfiguracaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

@ExtendWith(MockitoExtension.class)
class AppointmentSettingsServiceTest {

  private final UUID tenantId = UUID.randomUUID();

  @Mock private AgendamentoConfiguracaoRepository repository;
  @Mock private ContextoTenant contextoTenant;

  private AppointmentSettingsService service;

  @BeforeEach
  void setUp() {
    service = new AppointmentSettingsService(repository, contextoTenant);
    // Simula o que o JPA faz de verdade: save() -> em.persist() dispara o @PrePersist da entidade.
    // Sem isso o teste nao exercitaria os defaults que o service depende de encontrar preenchidos.
    // lenient: os testes que so leem uma configuracao ja existente nunca chegam a salvar.
    lenient()
        .when(repository.save(any(AgendamentoConfiguracao.class)))
        .thenAnswer(
            invocation -> {
              AgendamentoConfiguracao entidade = invocation.getArgument(0);
              if (entidade.getId() == null) {
                entidade.setId(UUID.randomUUID());
                if (entidade.getPermitirAgendamentoManualComConflito() == null) {
                  entidade.setPermitirAgendamentoManualComConflito(Boolean.FALSE);
                }
                Instant agora = Instant.now();
                if (entidade.getCreatedAt() == null) entidade.setCreatedAt(agora);
                if (entidade.getUpdatedAt() == null) entidade.setUpdatedAt(agora);
              }
              return entidade;
            });
  }

  @Test
  @DisplayName("primeira leitura cria a configuracao do tenant com conflito manual desabilitado")
  void criaConfiguracaoNaPrimeiraLeitura() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

    AppointmentSchedulingSettingsResponse response = service.getSettings();

    ArgumentCaptor<AgendamentoConfiguracao> captor =
        ArgumentCaptor.forClass(AgendamentoConfiguracao.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(response.allowConflictingAppointmentsOnManualScheduling).isFalse();
    assertThat(response.updatedAt).isNotBlank();
  }

  @Test
  @DisplayName("configuracao ja existente e devolvida sem nova escrita")
  void naoRecriaConfiguracaoExistente() {
    Instant updatedAt = Instant.parse("2026-01-15T10:00:00Z");
    AgendamentoConfiguracao existente = configuracao(Boolean.TRUE, updatedAt);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existente));

    AppointmentSchedulingSettingsResponse response = service.getSettings();

    verify(repository, never()).save(any());
    assertThat(response.allowConflictingAppointmentsOnManualScheduling).isTrue();
    assertThat(response.updatedAt).isEqualTo(updatedAt.toString());
  }

  @Test
  @DisplayName("update grava o novo valor da flag")
  void updateGravaFlag() {
    AgendamentoConfiguracao existente = configuracao(Boolean.FALSE, Instant.now());
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existente));

    AppointmentSchedulingSettingsRequest request = new AppointmentSchedulingSettingsRequest();
    request.allowConflictingAppointmentsOnManualScheduling = Boolean.TRUE;

    AppointmentSchedulingSettingsResponse response = service.updateSettings(request);

    assertThat(existente.getPermitirAgendamentoManualComConflito()).isTrue();
    assertThat(response.allowConflictingAppointmentsOnManualScheduling).isTrue();
    verify(repository).save(existente);
  }

  @Test
  @DisplayName("update com request nulo ou campo nulo preserva o valor atual")
  void updateComRequestNuloPreservaValor() {
    AgendamentoConfiguracao existente = configuracao(Boolean.TRUE, Instant.now());
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existente));

    assertThat(service.updateSettings(null).allowConflictingAppointmentsOnManualScheduling).isTrue();

    AppointmentSchedulingSettingsRequest vazio = new AppointmentSchedulingSettingsRequest();
    assertThat(service.updateSettings(vazio).allowConflictingAppointmentsOnManualScheduling)
        .isTrue();
    assertThat(existente.getPermitirAgendamentoManualComConflito()).isTrue();
  }

  @Test
  @DisplayName("updatedAt da resposta e o anterior a atualizacao (assimetria preservada do original)")
  void updateDevolveUpdatedAtAnterior() {
    Instant anterior = Instant.parse("2026-01-15T10:00:00Z");
    AgendamentoConfiguracao existente = configuracao(Boolean.FALSE, anterior);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existente));

    AppointmentSchedulingSettingsRequest request = new AppointmentSchedulingSettingsRequest();
    request.allowConflictingAppointmentsOnManualScheduling = Boolean.TRUE;

    // O @PreUpdate so roda no flush; a resposta e montada antes dele, como no Quarkus.
    assertThat(service.updateSettings(request).updatedAt).isEqualTo(anterior.toString());
  }

  @Test
  @DisplayName("allowsManualConflictByTenantId le a flag do tenant e trata nulo como falso")
  void allowsManualConflict() {
    when(repository.findByTenantId(tenantId))
        .thenReturn(Optional.of(configuracao(Boolean.TRUE, Instant.now())));
    assertThat(service.allowsManualConflictByTenantId(tenantId)).isTrue();

    when(repository.findByTenantId(tenantId))
        .thenReturn(Optional.of(configuracao(Boolean.FALSE, Instant.now())));
    assertThat(service.allowsManualConflictByTenantId(tenantId)).isFalse();

    when(repository.findByTenantId(tenantId))
        .thenReturn(Optional.of(configuracao(null, Instant.now())));
    assertThat(service.allowsManualConflictByTenantId(tenantId)).isFalse();
  }

  @Test
  @DisplayName("allowsManualConflictByTenantId cria a configuracao se o tenant ainda nao tiver uma")
  void allowsManualConflictCriaQuandoAusente() {
    when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

    assertThat(service.allowsManualConflictByTenantId(tenantId)).isFalse();
    verify(repository).save(any(AgendamentoConfiguracao.class));
  }

  private AgendamentoConfiguracao configuracao(Boolean permite, Instant updatedAt) {
    AgendamentoConfiguracao configuracao = new AgendamentoConfiguracao();
    configuracao.setId(UUID.randomUUID());
    configuracao.setTenantId(tenantId);
    configuracao.setPermitirAgendamentoManualComConflito(permite);
    configuracao.setCreatedAt(updatedAt);
    configuracao.setUpdatedAt(updatedAt);
    return configuracao;
  }
}
