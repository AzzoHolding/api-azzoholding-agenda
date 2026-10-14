package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SalonDtos;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoSalonProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Espelha {@code modules/salon/api/SalonResource.java} ({@code @Path("/api/v1/salon")}).
 *
 * <p>{@code obterPublico} (usado por {@code PublicSalonsResource} do modulo {@code publicbooking},
 * ainda nao migrado) nao tem endpoint aqui — pertence ao controller publico que sera portado junto
 * com esse modulo. O metodo ja existe em {@link ServicoSalonProfile} para esse momento.
 */
@RestController
@RequestMapping("/api/v1/salon")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class SalonController {

  private static final Set<String> MIME_TYPES_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");

  private final ServicoSalonProfile servicoSalonProfile;
  private final long maxLogoBytes;

  public SalonController(
      ServicoSalonProfile servicoSalonProfile,
      @Value("${app.salon.logo.max-bytes:10485760}") long maxLogoBytes) {
    this.servicoSalonProfile = servicoSalonProfile;
    this.maxLogoBytes = maxLogoBytes;
  }

  @GetMapping("/profile")
  public SalonDtos.SalonProfile obterPerfil() {
    return servicoSalonProfile.obterPrivado();
  }

  /**
   * So o dono altera o perfil (achado SEG-006): nome, endereco de agendamento (slug), CPF/CNPJ,
   * contatos, endereco e horarios. A classe libera OWNER, PROFESSIONAL e STAFF para LER o perfil;
   * a escrita herdava isso e qualquer funcionario podia renomear o salao, trocar o link publico de
   * agendamento ou alterar o documento fiscal. O logo (abaixo) ja era so do dono.
   */
  @PutMapping("/profile")
  @PreAuthorize("hasRole('OWNER')")
  public SalonDtos.SalonProfile atualizarPerfil(@Valid @RequestBody SalonDtos.SalonProfile request) {
    return servicoSalonProfile.atualizarPrivado(request);
  }

  @PostMapping(value = "/profile/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('OWNER')")
  public SalonDtos.SalonProfile atualizarLogo(@RequestPart(name = "file", required = false) MultipartFile arquivo) {
    if (arquivo == null || arquivo.getOriginalFilename() == null || arquivo.getOriginalFilename().isBlank()) {
      throw new ApiClientErrorException("Imagem do estabelecimento obrigatoria.", HttpStatus.BAD_REQUEST.value());
    }

    try {
      byte[] bytesArquivo = arquivo.getBytes();
      if (bytesArquivo.length <= 0) {
        throw new ApiClientErrorException("Imagem do estabelecimento obrigatoria.", HttpStatus.BAD_REQUEST.value());
      }
      if (bytesArquivo.length > maxLogoBytes) {
        throw new ApiClientErrorException("Imagem excede o tamanho maximo permitido.", HttpStatus.BAD_REQUEST.value());
      }

      String contentType = detectarContentType(bytesArquivo, arquivo.getOriginalFilename());
      if (!MIME_TYPES_PERMITIDOS.contains(contentType)) {
        throw new ApiClientErrorException(
            "Formato de imagem nao suportado. Use JPG, PNG ou WEBP.", HttpStatus.BAD_REQUEST.value());
      }

      return servicoSalonProfile.atualizarLogo(bytesArquivo, arquivo.getOriginalFilename(), contentType);
    } catch (ApiClientErrorException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new ApiClientErrorException("Falha ao processar imagem do estabelecimento.", HttpStatus.BAD_REQUEST.value());
    } catch (IllegalStateException exception) {
      throw new ApiClientErrorException("Storage de imagens indisponivel.", HttpStatus.SERVICE_UNAVAILABLE.value());
    }
  }

  @DeleteMapping("/profile/logo")
  @PreAuthorize("hasRole('OWNER')")
  public SalonDtos.SalonProfile removerLogo() {
    return servicoSalonProfile.removerLogo();
  }

  /**
   * Tipo pelo CONTEUDO, nunca pelo nome (pentest 14/10/2026, SEG-021): o
   * {@code guessContentTypeFromStream} nao reconhece WEBP, e o codigo caia na extensao do nome —
   * qualquer conteudo (HTML, SVG com script) chamado {@code a.webp} era aceito como imagem.
   */
  static String detectarContentType(byte[] bytesArquivo, String nomeArquivo) {
    byte[] b = bytesArquivo == null ? new byte[0] : bytesArquivo;
    if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
    if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
    return "application/octet-stream";
  }
}
