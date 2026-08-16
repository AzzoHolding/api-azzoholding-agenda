package br.com.phdigitalcode.azzo.agenda.pro.entity.converter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPayment;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusPaymentConverter implements AttributeConverter<StatusPayment, String> {

  @Override
  public String convertToDatabaseColumn(StatusPayment attribute) {
    return attribute != null ? attribute.getDescription() : null;
  }

  @Override
  public StatusPayment convertToEntityAttribute(String dbData) {
    return StatusPayment.fromValue(dbData);
  }
}
