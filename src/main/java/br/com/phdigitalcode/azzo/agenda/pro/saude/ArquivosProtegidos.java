package br.com.phdigitalcode.azzo.agenda.pro.saude;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Fotos e imagens de assinatura das clientes, cifradas (AES-GCM) no Postgres (plano, execucao: muda o projeto do
 * MinIO para nao depender dele). A imagem e decodificada e gravada de novo: sai sem EXIF (inclusive a localizacao
 * GPS) e no tamanho certo.
 */
@Component
public class ArquivosProtegidos {

  static final int MAXIMO_RECEBIDO = 12 * 1024 * 1024;
  static final int MAXIMO_GUARDADO = 1_500_000;
  static final int LADO_MAXIMO = 2000;

  private final NamedParameterJdbcTemplate jdbc;
  private final EncryptionService cifra;

  public ArquivosProtegidos(NamedParameterJdbcTemplate jdbc, EncryptionService cifra) {
    this.jdbc = jdbc;
    this.cifra = cifra;
  }

  record Imagem(byte[] bytes, int largura, int altura, String contentType) {}

  /** JPEG ou PNG pelos primeiros bytes (nao pelo nome nem pelo tipo que o navegador diz). */
  static String tipoPeloConteudo(byte[] b) {
    if (b == null || b.length < 8) return null;
    if ((b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) return "image/jpeg";
    if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
    return null;
  }

  /** Foto: JPEG ou PNG, ate 12 MB; sai JPEG sem metadados, lado maior ate 2000 px e ate 1,5 MB. */
  static Imagem prepararFoto(byte[] original) {
    if (original == null || original.length == 0) throw new IllegalArgumentException("Envie a foto.");
    if (original.length > MAXIMO_RECEBIDO) throw new IllegalArgumentException("A foto passa de 12 MB.");
    if (tipoPeloConteudo(original) == null) throw new IllegalArgumentException("A foto precisa ser JPEG ou PNG.");
    BufferedImage img = ler(original);
    BufferedImage reduzida = reduzir(img, LADO_MAXIMO);
    for (float qualidade : new float[] {0.85f, 0.75f, 0.65f, 0.5f}) {
      byte[] jpeg = jpeg(reduzida, qualidade);
      if (jpeg.length <= MAXIMO_GUARDADO) return new Imagem(jpeg, reduzida.getWidth(), reduzida.getHeight(), "image/jpeg");
    }
    BufferedImage menor = reduzir(reduzida, 1200);
    return new Imagem(jpeg(menor, 0.6f), menor.getWidth(), menor.getHeight(), "image/jpeg");
  }

  /** Assinatura desenhada: PNG ate 400 KB, regravado (sem metadados). */
  static Imagem prepararAssinatura(byte[] original) {
    if (original == null || original.length == 0) throw new IllegalArgumentException("Faltou a assinatura desenhada.");
    if (original.length > 400_000) throw new IllegalArgumentException("A imagem da assinatura é grande demais.");
    if (!"image/png".equals(tipoPeloConteudo(original))) throw new IllegalArgumentException("A assinatura precisa ser PNG.");
    BufferedImage img = reduzir(ler(original), 1200);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return new Imagem(out.toByteArray(), img.getWidth(), img.getHeight(), "image/png");
    } catch (Exception e) {
      throw new IllegalArgumentException("Não foi possível ler a assinatura.");
    }
  }

  private static BufferedImage ler(byte[] bytes) {
    try {
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
      if (img == null) throw new IllegalArgumentException("Não foi possível ler a imagem.");
      if ((long) img.getWidth() * img.getHeight() > 60_000_000L) throw new IllegalArgumentException("A imagem é grande demais.");
      return img;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Não foi possível ler a imagem.");
    }
  }

  static BufferedImage reduzir(BufferedImage img, int lado) {
    int w = img.getWidth();
    int h = img.getHeight();
    double escala = Math.min(1.0, (double) lado / Math.max(w, h));
    int nw = Math.max(1, (int) Math.round(w * escala));
    int nh = Math.max(1, (int) Math.round(h * escala));
    boolean alfa = img.getColorModel().hasAlpha();
    BufferedImage destino = new BufferedImage(nw, nh, alfa ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = destino.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    if (!alfa) {
      g.setColor(java.awt.Color.WHITE);
      g.fillRect(0, 0, nw, nh);
    }
    g.drawImage(img, 0, 0, nw, nh, null);
    g.dispose();
    return destino;
  }

  private static byte[] jpeg(BufferedImage img, float qualidade) {
    BufferedImage rgb = img;
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = rgb.createGraphics();
      g.setColor(java.awt.Color.WHITE);
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.drawImage(img, 0, 0, null);
      g.dispose();
    }
    try {
      Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
      ImageWriter writer = it.next();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
        writer.setOutput(ios);
        ImageWriteParam p = writer.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(qualidade);
        writer.write(null, new IIOImage(rgb, null, null), p);
      } finally {
        writer.dispose();
      }
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gravar a foto.", e);
    }
  }

  UUID guardar(UUID t, UUID clientId, String finalidade, Imagem imagem) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into arquivos_protegidos (id, tenant_id, client_id, finalidade, content_type, tamanho, conteudo_cifrado, hash_conteudo)
        values (:id, :t, :c, :f, :ct, :tam, :b, :h)""", Saude.m("id", id, "t", t, "c", clientId, "f", finalidade,
        "ct", imagem.contentType(), "tam", imagem.bytes().length, "b", cifra.encryptBytes(imagem.bytes()),
        "h", Saude.sha256(imagem.bytes())));
    return id;
  }

  record Arquivo(byte[] bytes, String contentType, String hash) {}

  Arquivo ler(UUID t, UUID id) {
    List<Map<String, Object>> r = jdbc.queryForList("select content_type, conteudo_cifrado, hash_conteudo from arquivos_protegidos"
        + " where tenant_id = :t and id = :id", Map.of("t", t, "id", id));
    if (r.isEmpty()) throw new IllegalArgumentException("Arquivo não encontrado.");
    byte[] bytes = cifra.decryptBytes((byte[]) r.get(0).get("conteudo_cifrado"));
    return new Arquivo(bytes, String.valueOf(r.get(0).get("content_type")), String.valueOf(r.get(0).get("hash_conteudo")));
  }
}
