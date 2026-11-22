package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * O QR Code do Pix copia-e-cola, pronto para a tela: SVG em data URI.
 *
 * <p>Gerado aqui (a ZXing ja esta no projeto, pela DANFSe) para o PDV nao precisar de biblioteca
 * nova. O cliente aponta a camera para a tela do caixa; o texto continua ao lado para quem paga
 * pelo celular do proprio salao.
 */
public final class QrCodeDoPix {

  private QrCodeDoPix() {}

  /** {@code null} quando nao ha payload ou ele nao cabe num QR. */
  public static String dataUri(String copiaECola) {
    if (copiaECola == null || copiaECola.isBlank()) return null;
    try {
      BitMatrix matriz = new QRCodeWriter().encode(
          copiaECola, BarcodeFormat.QR_CODE, 0, 0,
          Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2,
              EncodeHintType.CHARACTER_SET, "UTF-8"));
      int lado = matriz.getWidth();
      StringBuilder caminho = new StringBuilder();
      for (int y = 0; y < lado; y++) {
        for (int x = 0; x < lado; x++) {
          if (matriz.get(x, y)) caminho.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
        }
      }
      String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + lado + " " + lado
          + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
          + "<path fill=\"#000\" d=\"" + caminho + "\"/></svg>";
      return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    } catch (WriterException | IllegalArgumentException e) {
      return null;
    }
  }
}
