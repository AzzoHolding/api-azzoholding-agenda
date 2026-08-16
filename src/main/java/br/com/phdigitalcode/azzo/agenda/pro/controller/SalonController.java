package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Locale;
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
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
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

  @PutMapping("/profile")
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

  private String detectarContentType(byte[] bytesArquivo, String nomeArquivo) {
    try (ByteArrayInputStream input = new ByteArrayInputStream(bytesArquivo)) {
      String detectado = URLConnection.guessContentTypeFromStream(input);
      if (detectado != null && !detectado.isBlank()) return detectado;
    } catch (IOException ignored) {
      // no-op
    }

    String nome = nomeArquivo == null ? "" : nomeArquivo.toLowerCase(Locale.ROOT);
    if (nome.endsWith(".webp")) return "image/webp";
    if (nome.endsWith(".png")) return "image/png";
    if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) return "image/jpeg";
    return "application/octet-stream";
  }
}
