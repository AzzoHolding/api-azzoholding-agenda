package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoSalonProfile;

/**
 * Cobre {@code modules/salon/api/SalonResource.java} — porte fiel do mapeamento de erro do upload
 * de logo (400 arquivo ausente/formato invalido/tamanho excedido, 503 storage indisponivel) e da
 * deteccao de content-type por assinatura de bytes com fallback pela extensao do nome.
 */
class SalonControllerTest {

  private static final long MAX_LOGO_BYTES = 10_485_760L;
  /** Assinatura PNG valida (magic bytes) para exercitar a deteccao real de content-type. */
  private static final byte[] PNG_MAGIC_BYTES = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
  };

  private ServicoSalonProfile servicoSalonProfile;
  private SalonController controller;

  @BeforeEach
  void setUp() {
    servicoSalonProfile = mock(ServicoSalonProfile.class);
    controller = new SalonController(servicoSalonProfile, MAX_LOGO_BYTES);
  }

  private MockMultipartFile arquivo(String nome, byte[] conteudo) {
    return new MockMultipartFile("file", nome, null, conteudo);
  }

  @Test
  void obterPerfilDelegaAoService() {
    SalonDtos.SalonProfile esperado = new SalonDtos.SalonProfile();
    when(servicoSalonProfile.obterPrivado()).thenReturn(esperado);

    assertThat(controller.obterPerfil()).isSameAs(esperado);
  }

  @Test
  void atualizarPerfilDelegaAoService() {
    SalonDtos.SalonProfile request = new SalonDtos.SalonProfile();
    SalonDtos.SalonProfile esperado = new SalonDtos.SalonProfile();
    when(servicoSalonProfile.atualizarPrivado(request)).thenReturn(esperado);

    assertThat(controller.atualizarPerfil(request)).isSameAs(esperado);
  }

  // ─── atualizarLogo: caminho feliz ─────────────────────────────────────────

  @Test
  void uploadDetectaContentTypePelaAssinaturaEDelegaAoService() {
    SalonDtos.SalonProfile esperado = new SalonDtos.SalonProfile();
    when(servicoSalonProfile.atualizarLogo(PNG_MAGIC_BYTES, "logo.png", "image/png")).thenReturn(esperado);

    SalonDtos.SalonProfile resposta = controller.atualizarLogo(arquivo("logo.png", PNG_MAGIC_BYTES));

    assertThat(resposta).isSameAs(esperado);
    verify(servicoSalonProfile).atualizarLogo(PNG_MAGIC_BYTES, "logo.png", "image/png");
  }

  @Test
  void fallbackPorExtensaoQuandoAssinaturaNaoEhReconhecida() {
    byte[] conteudo = "conteudo-sem-assinatura-conhecida".getBytes(StandardCharsets.UTF_8);
    when(servicoSalonProfile.atualizarLogo(any(), eq("logo.webp"), eq("image/webp")))
        .thenReturn(new SalonDtos.SalonProfile());

    controller.atualizarLogo(arquivo("logo.webp", conteudo));

    verify(servicoSalonProfile).atualizarLogo(conteudo, "logo.webp", "image/webp");
  }

  // ─── atualizarLogo: erros ─────────────────────────────────────────────────

  @Test
  void arquivoNuloEQuatrocentosSemTocarNoService() {
    assertThatThrownBy(() -> controller.atualizarLogo(null))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Imagem do estabelecimento obrigatoria.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);

    verifyNoInteractions(servicoSalonProfile);
  }

  @Test
  void arquivoComNomeEmBrancoEQuatrocentos() {
    assertThatThrownBy(() -> controller.atualizarLogo(arquivo("   ", PNG_MAGIC_BYTES)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Imagem do estabelecimento obrigatoria.");

    verify(servicoSalonProfile, never()).atualizarLogo(any(), any(), any());
  }

  @Test
  void arquivoVazioEQuatrocentos() {
    assertThatThrownBy(() -> controller.atualizarLogo(arquivo("logo.png", new byte[0])))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Imagem do estabelecimento obrigatoria.");
  }

  @Test
  void arquivoAcimaDoLimiteEQuatrocentos() {
    byte[] grande = new byte[(int) MAX_LOGO_BYTES + 1];
    assertThatThrownBy(() -> controller.atualizarLogo(arquivo("logo.png", grande)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Imagem excede o tamanho maximo permitido.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);

    verifyNoInteractions(servicoSalonProfile);
  }

  @Test
  void formatoNaoSuportadoEQuatrocentos() {
    byte[] textoQualquer = "so texto puro, sem assinatura de imagem".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> controller.atualizarLogo(arquivo("arquivo.txt", textoQualquer)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Formato de imagem nao suportado. Use JPG, PNG ou WEBP.");

    verifyNoInteractions(servicoSalonProfile);
  }

  @Test
  void falhaDeLeituraDoArquivoEQuatrocentos() throws IOException {
    MultipartFile ilegivel = mock(MultipartFile.class);
    when(ilegivel.getOriginalFilename()).thenReturn("logo.png");
    when(ilegivel.getBytes()).thenThrow(new IOException("disco"));

    assertThatThrownBy(() -> controller.atualizarLogo(ilegivel))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Falha ao processar imagem do estabelecimento.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void storageIndisponivelEQuinhentosETres() {
    when(servicoSalonProfile.atualizarLogo(any(), any(), any()))
        .thenThrow(new IllegalStateException("MinIO desabilitado"));

    assertThatThrownBy(() -> controller.atualizarLogo(arquivo("logo.png", PNG_MAGIC_BYTES)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Storage de imagens indisponivel.")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(503);
  }

  @Test
  void erroDoServiceNaoEReembalhadoPeloControlador() {
    when(servicoSalonProfile.atualizarLogo(any(), any(), any()))
        .thenThrow(new ApiClientErrorException("Salao nao encontrado", 400));

    assertThatThrownBy(() -> controller.atualizarLogo(arquivo("logo.png", PNG_MAGIC_BYTES)))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Salao nao encontrado");
  }

  // ─── removerLogo ────────────────────────────────────────────────────────

  @Test
  void removerLogoDelegaAoService() {
    SalonDtos.SalonProfile esperado = new SalonDtos.SalonProfile();
    when(servicoSalonProfile.removerLogo()).thenReturn(esperado);

    assertThat(controller.removerLogo()).isSameAs(esperado);
  }
}
