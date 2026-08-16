package br.com.phdigitalcode.azzo.agenda.pro.entity.converter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusSubscriptionConverter implements AttributeConverter<StatusSubscription, String> {

  @Override
  public String convertToDatabaseColumn(StatusSubscription attribute) {
    return attribute != null ? attribute.getDescription() : null;
  }

  @Override
  public StatusSubscription convertToEntityAttribute(String dbData) {
    return StatusSubscription.fromValue(dbData);
  }
}
