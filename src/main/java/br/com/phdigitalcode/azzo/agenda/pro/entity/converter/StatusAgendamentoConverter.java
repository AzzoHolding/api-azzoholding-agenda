package br.com.phdigitalcode.azzo.agenda.pro.entity.converter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Porte verbatim de {@code modules/scheduling/domain/converter/StatusAgendamentoConverter.java}.
 *
 * <p><b>Atencao:</b> a coluna {@code appointments.status} guarda a <b>descricao em portugues</b>
 * ("Concluido", "Nao compareceu", ...), nao o nome do enum. Toda query SQL nativa sobre esse campo
 * precisa usar a descricao.
 */
@Converter(autoApply = false)
public class StatusAgendamentoConverter implements AttributeConverter<StatusAgendamento, String> {

  @Override
  public String convertToDatabaseColumn(StatusAgendamento attribute) {
    return attribute != null ? attribute.getDescription() : null;
  }

  @Override
  public StatusAgendamento convertToEntityAttribute(String dbData) {
    return StatusAgendamento.fromValue(dbData);
  }
}
