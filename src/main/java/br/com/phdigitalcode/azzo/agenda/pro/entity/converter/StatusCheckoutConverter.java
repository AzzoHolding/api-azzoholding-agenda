package br.com.phdigitalcode.azzo.agenda.pro.entity.converter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusCheckoutConverter implements AttributeConverter<StatusCheckout, String> {

  @Override
  public String convertToDatabaseColumn(StatusCheckout attribute) {
    return attribute != null ? attribute.getDescription() : null;
  }

  @Override
  public StatusCheckout convertToEntityAttribute(String dbData) {
    return StatusCheckout.fromValue(dbData);
  }
}
