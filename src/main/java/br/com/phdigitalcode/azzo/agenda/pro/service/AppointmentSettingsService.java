package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentSchedulingSettingsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AppointmentSchedulingSettingsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AgendamentoConfiguracao;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoConfiguracaoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/scheduling/application/AppointmentSettingsService.java}.
 *
 * <p>Nenhum metodo e {@code readOnly}: os quatro do original sao {@code @Transactional} de escrita
 * porque todos passam por {@link #getOrCreateByTenantId}, que insere a linha de configuracao na
 * primeira leitura. Inclusive {@link #getSettings} e {@link #allowsManualConflictByTenantId} —
 * marcar qualquer um deles como {@code readOnly = true} quebraria essa criacao sob demanda.
 */
@Service
public class AppointmentSettingsService {

  private final AgendamentoConfiguracaoRepository agendamentoConfiguracaoRepository;
  private final ContextoTenant contextoTenant;

  public AppointmentSettingsService(
      AgendamentoConfiguracaoRepository agendamentoConfiguracaoRepository,
      ContextoTenant contextoTenant) {
    this.agendamentoConfiguracaoRepository = agendamentoConfiguracaoRepository;
    this.contextoTenant = contextoTenant;
  }

  @Transactional
  public AppointmentSchedulingSettingsResponse getSettings() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toResponse(getOrCreateByTenantId(tenantId));
  }

  /**
   * <b>Assimetria preservada do original</b>: a resposta e montada a partir da entidade ainda em
   * memoria, antes do flush — entao o {@code updatedAt} devolvido e o <b>anterior</b> a esta
   * atualizacao, nao o novo (o {@code @PreUpdate} so roda no commit). O Panache se comporta do
   * mesmo jeito. Nao foi "corrigido": mudaria o corpo da resposta que o frontend ja consome.
   */
  @Transactional
  public AppointmentSchedulingSettingsResponse updateSettings(
      AppointmentSchedulingSettingsRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AgendamentoConfiguracao configuracao = getOrCreateByTenantId(tenantId);
    if (request != null && request.allowConflictingAppointmentsOnManualScheduling != null) {
      configuracao.setPermitirAgendamentoManualComConflito(
          request.allowConflictingAppointmentsOnManualScheduling);
    }
    agendamentoConfiguracaoRepository.save(configuracao);
    return toResponse(configuracao);
  }

  @Transactional
  public boolean allowsManualConflictByTenantId(UUID tenantId) {
    return Boolean.TRUE.equals(
        getOrCreateByTenantId(tenantId).getPermitirAgendamentoManualComConflito());
  }

  @Transactional
  public AgendamentoConfiguracao getOrCreateByTenantId(UUID tenantId) {
    return agendamentoConfiguracaoRepository
        .findByTenantId(tenantId)
        .orElseGet(
            () -> {
              AgendamentoConfiguracao nova = new AgendamentoConfiguracao();
              nova.setTenantId(tenantId);
              // save() -> em.persist() dispara o @PrePersist, que preenche id, o default
              // permitirAgendamentoManualComConflito=false e os timestamps. Sem isso, o
              // toResponse() logo abaixo leria campos nulos.
              return agendamentoConfiguracaoRepository.save(nova);
            });
  }

  private AppointmentSchedulingSettingsResponse toResponse(AgendamentoConfiguracao configuracao) {
    AppointmentSchedulingSettingsResponse response = new AppointmentSchedulingSettingsResponse();
    response.allowConflictingAppointmentsOnManualScheduling =
        configuracao.getPermitirAgendamentoManualComConflito();
    response.updatedAt =
        configuracao.getUpdatedAt() != null ? configuracao.getUpdatedAt().toString() : null;
    return response;
  }
}
