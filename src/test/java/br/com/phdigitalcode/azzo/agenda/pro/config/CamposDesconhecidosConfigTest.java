package br.com.phdigitalcode.azzo.agenda.pro.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SettingsDtos;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class CamposDesconhecidosConfigTest {

  /** Mesmo com o mapper em modo estrito, o campo desconhecido e registrado e ignorado, sem 400. */
  @Test
  void campoDesconhecidoEhIgnoradoSemDerrubarARequisicao() {
    JsonMapper.Builder builder =
        JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    new CamposDesconhecidosConfig().registrarCamposDesconhecidos().customize(builder);
    JsonMapper mapper = builder.build();

    SettingsDtos.DiscountPolicyRequest lido =
        mapper.readValue(
            "{\"posMaxDiscountPercent\": 10, \"outro\": {\"a\": [1, 2]}, \"maxDiscountPercent\": 20}",
            SettingsDtos.DiscountPolicyRequest.class);

    assertThat(lido.maxDiscountPercent).isEqualTo(20);
  }
}
