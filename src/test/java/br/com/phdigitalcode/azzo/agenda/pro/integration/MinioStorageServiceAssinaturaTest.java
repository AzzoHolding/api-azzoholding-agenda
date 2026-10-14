package br.com.phdigitalcode.azzo.agenda.pro.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pentest 14/10/2026: SEG-020 — URL do proxy de storage sem assinatura nem prazo. */
class MinioStorageServiceAssinaturaTest {

  private final UUID tenantId = UUID.randomUUID();
  private final String chave = "tenant/" + tenantId + "/salao/logo/abc-logo.png";
  private MinioStorageService servico;

  @BeforeEach
  void preparar() {
    servico = novoServico("segredo-de-teste-1234567890");
  }

  private static MinioStorageService novoServico(String segredo) {
    MinioStorageService s =
        new MinioStorageService(false, "http://x", "a", "b", "bucket", 10, "https://app.exemplo");
    s.definirSegredoDeAssinatura(segredo);
    return s;
  }

  private static Map<String, String> parametros(String url) {
    Map<String, String> m = new HashMap<>();
    for (String par : URI.create(url).getRawQuery().split("&")) {
      String[] kv = par.split("=", 2);
      m.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    return m;
  }

  @Test
  void urlGeradaTemPrazoEAssinaturaQueValidam() {
    Map<String, String> p = parametros(servico.gerarUrlAssinadaLeitura(chave, tenantId));

    assertThat(p.get("key")).isEqualTo(chave);
    assertThat(servico.assinaturaDeLeituraValida(chave, Long.parseLong(p.get("exp")), p.get("sig"))).isTrue();
  }

  @Test
  void trocarAChaveParaOutroSalaoInvalidaAAssinatura() {
    Map<String, String> p = parametros(servico.gerarUrlAssinadaLeitura(chave, tenantId));
    String alheia = "tenant/" + UUID.randomUUID() + "/salao/logo/abc-logo.png";

    assertThat(servico.assinaturaDeLeituraValida(alheia, Long.parseLong(p.get("exp")), p.get("sig"))).isFalse();
  }

  @Test
  void esticarOPrazoInvalidaAAssinatura() {
    Map<String, String> p = parametros(servico.gerarUrlAssinadaLeitura(chave, tenantId));

    assertThat(servico.assinaturaDeLeituraValida(chave, Long.parseLong(p.get("exp")) + 86400, p.get("sig")))
        .isFalse();
  }

  @Test
  void prazoVencidoOuSemAssinaturaERecusado() {
    long vencido = Instant.now().minusSeconds(5).getEpochSecond();

    assertThat(servico.assinaturaDeLeituraValida(chave, vencido, "qualquer")).isFalse();
    assertThat(servico.assinaturaDeLeituraValida(chave, Instant.now().plusSeconds(60).getEpochSecond(), null))
        .isFalse();
  }

  @Test
  void assinaturaDeOutroSegredoNaoVale() {
    Map<String, String> p = parametros(novoServico("outro-segredo-xyz").gerarUrlAssinadaLeitura(chave, tenantId));

    assertThat(servico.assinaturaDeLeituraValida(chave, Long.parseLong(p.get("exp")), p.get("sig"))).isFalse();
  }
}
