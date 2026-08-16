package br.com.phdigitalcode.azzo.agenda.pro.entity.converter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusNotificationConverter implements AttributeConverter<StatusNotification, String> {

  @Override
  public String convertToDatabaseColumn(StatusNotification attribute) {
    return attribute != null ? attribute.getDescription() : null;
  }

  @Override
  public StatusNotification convertToEntityAttribute(String dbData) {
    return StatusNotification.fromValue(dbData);
  }
}
