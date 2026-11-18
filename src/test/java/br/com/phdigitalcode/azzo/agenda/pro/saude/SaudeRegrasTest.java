package br.com.phdigitalcode.azzo.agenda.pro.saude;

import static br.com.phdigitalcode.azzo.agenda.pro.saude.Saude.m;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaudeRegrasTest {

  private static List<Map<String, Object>> perguntas() {
    return ModelosDeSaude.validarPerguntas(List.of(
        m("id", "alergia", "texto", "Tem alergia?", "tipo", "SIM_NAO", "obrigatoria", true),
        m("id", "alergia_qual", "texto", "Qual?", "tipo", "TEXTO", "obrigatoria", true,
            "condicao", m("pergunta", "alergia", "valor", "SIM"),
            "alerta", m("tipo", "ALERGIA", "quando", "PREENCHIDO", "rotulo", "Alergia")),
        m("id", "gestante", "texto", "Está gestante?", "tipo", "SIM_NAO", "obrigatoria", true,
            "alerta", m("tipo", "CONTRAINDICACAO", "quando", "SIM", "rotulo", "Gestante")),
        m("id", "fio", "texto", "Tipo de fio", "tipo", "ESCOLHA", "opcoes", List.of("Liso", "Crespo"))));
  }

  @Test
  @DisplayName("modelo: identificador unico, condicao so para pergunta anterior, escolha com 2 opcoes")
  void validarPerguntas() {
    assertThatThrownBy(() -> ModelosDeSaude.validarPerguntas(List.of())).hasMessageContaining("ao menos uma");
    assertThatThrownBy(() -> ModelosDeSaude.validarPerguntas(List.of(
        m("id", "a", "texto", "A", "tipo", "SIM_NAO"), m("id", "a", "texto", "B", "tipo", "SIM_NAO"))))
        .hasMessageContaining("mesmo identificador");
    assertThatThrownBy(() -> ModelosDeSaude.validarPerguntas(List.of(
        m("id", "b", "texto", "B", "tipo", "TEXTO", "condicao", m("pergunta", "a", "valor", "SIM")),
        m("id", "a", "texto", "A", "tipo", "SIM_NAO")))).hasMessageContaining("pergunta anterior");
    assertThatThrownBy(() -> ModelosDeSaude.validarPerguntas(List.of(
        m("id", "c", "texto", "C", "tipo", "ESCOLHA", "opcoes", List.of("so uma"))))).hasMessageContaining("duas opções");
    assertThatThrownBy(() -> ModelosDeSaude.validarPerguntas(List.of(m("id", "d", "texto", "D", "tipo", "FOTO"))))
        .hasMessageContaining("Tipo de pergunta");
  }

  @Test
  @DisplayName("todos os modelos prontos passam na validacao")
  void modelosProntosValidos() {
    for (ModelosProntos.Modelo mod : ModelosProntos.modelos()) {
      assertThat(ModelosDeSaude.validarPerguntas(mod.perguntas())).isNotEmpty();
    }
    assertThat(ModelosProntos.termos()).extracting(ModelosProntos.Termo::tipo)
        .containsExactlyInAnyOrder("TERMO_PROCEDIMENTO", "AUTORIZACAO_FOTOS", "AUTORIZACAO_MARKETING");
  }

  @Test
  @DisplayName("pergunta condicional: so aparece e so e obrigatoria quando a outra resposta bate")
  void condicaoEObrigatorias() {
    List<Map<String, Object>> p = perguntas();
    Map<String, Object> nao = AnamneseDaCliente.respostasLimpas(p, Map.of("alergia", "nao", "alergia_qual", "dipirona", "gestante", "NAO"));
    assertThat(nao).doesNotContainKey("alergia_qual");
    assertThat(AnamneseDaCliente.faltando(p, nao)).isEmpty();
    Map<String, Object> sim = AnamneseDaCliente.respostasLimpas(p, Map.of("alergia", "SIM", "gestante", "NAO"));
    assertThat(AnamneseDaCliente.faltando(p, sim)).containsExactly("Qual?");
    assertThatThrownBy(() -> AnamneseDaCliente.respostasLimpas(p, Map.of("fio", "Ondulado"))).hasMessageContaining("Opção inválida");
    assertThatThrownBy(() -> AnamneseDaCliente.respostasLimpas(p, Map.of("alergia", "talvez"))).hasMessageContaining("sim ou não");
  }

  @Test
  @DisplayName("alertas saem das respostas: 'Alergia: dipirona' e 'Gestante'")
  void alertas() {
    List<Map<String, Object>> p = perguntas();
    Map<String, Object> r = AnamneseDaCliente.respostasLimpas(p, Map.of("alergia", "SIM", "alergia_qual", "dipirona", "gestante", "SIM"));
    assertThat(AnamneseDaCliente.alertasDasRespostas(p, r)).extracting(a -> a.get("tipo") + "|" + a.get("texto"))
        .containsExactly("ALERGIA|Alergia: dipirona", "CONTRAINDICACAO|Gestante");
    Map<String, Object> semAlerta = AnamneseDaCliente.respostasLimpas(p, Map.of("alergia", "NAO", "gestante", "NAO"));
    assertThat(AnamneseDaCliente.alertasDasRespostas(p, semAlerta)).isEmpty();
  }

  @Test
  @DisplayName("validade da anamnese e menor de idade")
  void validadeEMenor() {
    LocalDate hoje = LocalDate.of(2026, 11, 18);
    Instant haTreze = Instant.parse("2025-10-01T12:00:00Z");
    assertThat(AnamneseDaCliente.vencida(haTreze, 12, hoje)).isTrue();
    assertThat(AnamneseDaCliente.vencida(haTreze, null, hoje)).isFalse();
    assertThat(AnamneseDaCliente.vencida(Instant.parse("2026-06-01T12:00:00Z"), 12, hoje)).isFalse();
    assertThat(AnamneseDaCliente.menor(LocalDate.of(2010, 1, 1), hoje)).isTrue();
    assertThat(AnamneseDaCliente.menor(LocalDate.of(2000, 1, 1), hoje)).isFalse();
    assertThat(AnamneseDaCliente.menor(null, hoje)).isFalse();
  }

  @Test
  @DisplayName("ficha com conteudo: qualquer campo, secao, intercorrencia ou foto")
  void conteudoDaFicha() {
    Map<String, Object> vazia = new HashMap<>();
    List<Map<String, Object>> secoes = List.of(m("procedimento", null, "produtos", List.of()));
    assertThat(FichaDeAtendimento.temConteudo(vazia, secoes, 0, 0)).isFalse();
    assertThat(FichaDeAtendimento.temConteudo(vazia, List.of(m("procedimento", "Coloração 6.0")), 0, 0)).isTrue();
    assertThat(FichaDeAtendimento.temConteudo(m("cuidados", "Não lavar por 48 h"), secoes, 0, 0)).isTrue();
    assertThat(FichaDeAtendimento.temConteudo(vazia, secoes, 1, 0)).isTrue();
    assertThat(FichaDeAtendimento.temConteudo(vazia, secoes, 0, 2)).isTrue();
  }

  @Test
  @DisplayName("hash da evidencia muda com qualquer campo; o retrato da anamnese mostra a resposta")
  void evidenciaERetrato() {
    Map<String, Object> e = new HashMap<>(Map.of("cliente", "c1", "ip", "1.1.1.1", "em", "2026-11-18T10:00:00Z"));
    String h = AssinaturasDaCliente.hashDaEvidencia(e);
    assertThat(h).hasSize(64).isEqualTo(AssinaturasDaCliente.hashDaEvidencia(new HashMap<>(e)));
    e.put("ip", "1.1.1.2");
    assertThat(AssinaturasDaCliente.hashDaEvidencia(e)).isNotEqualTo(h);
    List<Map<String, Object>> p = perguntas();
    String retrato = AssinaturasDaCliente.retratoDaAnamnese("Geral", 2, p,
        AnamneseDaCliente.respostasLimpas(p, Map.of("alergia", "SIM", "alergia_qual", "dipirona", "gestante", "NAO")));
    assertThat(retrato).contains("Geral (versão 2)").contains("dipirona").contains("Não");
  }

  @Test
  @DisplayName("foto: so JPEG/PNG pelo conteudo; sai JPEG sem EXIF e com lado maior de ate 2000 px")
  void fotoSemMetadados() throws Exception {
    BufferedImage grande = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(grande, "png", png);
    ArquivosProtegidos.Imagem img = ArquivosProtegidos.prepararFoto(png.toByteArray());
    assertThat(img.contentType()).isEqualTo("image/jpeg");
    assertThat(img.largura()).isEqualTo(2000);
    assertThat(img.altura()).isEqualTo(1000);
    // Sem segmento APP1 (EXIF) logo depois do SOI.
    byte[] b = img.bytes();
    assertThat(b[2] & 0xff).isEqualTo(0xFF);
    assertThat(b[3] & 0xff).isNotEqualTo(0xE1);
    assertThat(ArquivosProtegidos.tipoPeloConteudo("GIF89a....".getBytes())).isNull();
    assertThatThrownBy(() -> ArquivosProtegidos.prepararFoto("nao e imagem".getBytes())).hasMessageContaining("JPEG ou PNG");
  }

  @Test
  @DisplayName("link: token aleatorio longo, 4 ultimos digitos e primeiro nome")
  void link() {
    Set<String> tokens = new HashSet<>();
    for (int i = 0; i < 200; i++) tokens.add(LinksDaCliente.novoToken());
    assertThat(tokens).hasSize(200);
    assertThat(tokens.iterator().next()).hasSize(43);
    assertThat(LinksDaCliente.ultimos4("(21) 99876-5432")).isEqualTo("5432");
    assertThat(LinksDaCliente.ultimos4("12")).isNull();
    assertThat(LinksDaCliente.primeiroNome("Maria da Silva")).isEqualTo("Maria");
  }
}
