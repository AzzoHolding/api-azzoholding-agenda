package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tranca o certificado A1 com uma chave que so existe quando o dono digita a senha.
 *
 * <p>Pedido do dono (06/11/2026). A chave do AES-256-GCM e derivada, a cada uso, de:
 *
 * <ul>
 *   <li>a <b>senha do certificado</b>, que nao e guardada em lugar nenhum;
 *   <li>a <b>pimenta</b> ({@code app.nfse.certificado.pimenta}), segredo do servidor que fica FORA do
 *       banco (variavel de ambiente, com backup). Sem ela, nenhum certificado abre;
 *   <li>o <b>sal</b>, aleatorio por certificado e guardado no banco ao lado dele. Nao e segredo: so
 *       obriga a quebrar cada certificado separadamente.
 * </ul>
 *
 * <p>A derivacao e Argon2id (19 MiB, 2 passadas, a recomendacao minima da OWASP), lenta de proposito:
 * quem roubar o banco e a pimenta ainda precisa adivinhar a senha, uma tentativa cara de cada vez.
 * Nem a chave nem um hash da senha sao gravados (um hash guardado viraria a propria chave). Senha
 * errada aparece como falha da etiqueta do GCM.
 */
@Component
public class CofreDoCertificado {

  /** Valor da coluna {@code protecao} dos certificados trancados por este cofre. */
  public static final String PROTECAO = "SENHA_ARGON2ID_V1";
  public static final String ERR_PIMENTA_AUSENTE = "NFSE_CERTIFICADO_PIMENTA_AUSENTE";

  private static final Logger LOG = LoggerFactory.getLogger(CofreDoCertificado.class);
  private static final int MEMORIA_KIB = 19 * 1024;
  private static final int PASSADAS = 2;
  private static final int PARALELISMO = 1;
  private static final int TAMANHO_DA_CHAVE = 32;
  private static final int TAMANHO_DO_SAL = 16;
  private static final int TAMANHO_DO_NONCE = 12;
  private static final int ETIQUETA_BITS = 128;

  private final SecureRandom aleatorio = new SecureRandom();
  private final byte[] pimenta;

  public CofreDoCertificado(@Value("${app.nfse.certificado.pimenta:}") String pimentaBase64) {
    this.pimenta = lerPimenta(pimentaBase64);
    if (this.pimenta == null) {
      LOG.warn("nfse_certificado_pimenta_ausente: certificados A1 nao podem ser enviados nem abertos ate configurar"
          + " NFSE_CERTIFICADO_PIMENTA");
    }
  }

  /** O certificado trancado: o conteudo cifrado e o sal, ambos em Base64. */
  public record Trancado(String conteudo, String sal) {}

  public Trancado trancar(byte[] pfx, String senha) {
    byte[] sal = new byte[TAMANHO_DO_SAL];
    aleatorio.nextBytes(sal);
    byte[] nonce = new byte[TAMANHO_DO_NONCE];
    aleatorio.nextBytes(nonce);
    byte[] chave = derivar(senha, sal);
    try {
      Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
      cifra.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(chave, "AES"), new GCMParameterSpec(ETIQUETA_BITS, nonce));
      cifra.updateAAD(PROTECAO.getBytes(StandardCharsets.US_ASCII));
      byte[] cifrado = cifra.doFinal(pfx);
      byte[] saida = ByteBuffer.allocate(nonce.length + cifrado.length).put(nonce).put(cifrado).array();
      Base64.Encoder b64 = Base64.getEncoder();
      return new Trancado(b64.encodeToString(saida), b64.encodeToString(sal));
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao cifrar o certificado.", ex);
    } finally {
      Arrays.fill(chave, (byte) 0);
    }
  }

  /** Devolve o .pfx. Senha errada (ou pimenta trocada): {@code NFSE_CERTIFICATE_PASSWORD_INVALID}. */
  public byte[] abrir(String conteudo, String salBase64, String senha) {
    byte[] entrada;
    byte[] sal;
    try {
      entrada = Base64.getDecoder().decode(conteudo);
      sal = Base64.getDecoder().decode(salBase64);
    } catch (Exception ex) {
      throw new IllegalStateException("Certificado gravado em formato invalido.", ex);
    }
    if (entrada.length <= TAMANHO_DO_NONCE) throw new IllegalStateException("Certificado gravado em formato invalido.");
    byte[] chave = derivar(senha, sal);
    try {
      Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
      cifra.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(chave, "AES"),
          new GCMParameterSpec(ETIQUETA_BITS, entrada, 0, TAMANHO_DO_NONCE));
      cifra.updateAAD(PROTECAO.getBytes(StandardCharsets.US_ASCII));
      return cifra.doFinal(entrada, TAMANHO_DO_NONCE, entrada.length - TAMANHO_DO_NONCE);
    } catch (AEADBadTagException ex) {
      throw new IllegalArgumentException(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_INVALID);
    } catch (Exception ex) {
      throw new IllegalStateException("Falha ao abrir o certificado.", ex);
    } finally {
      Arrays.fill(chave, (byte) 0);
    }
  }

  private byte[] derivar(String senha, byte[] sal) {
    if (pimenta == null) throw new IllegalArgumentException(ERR_PIMENTA_AUSENTE);
    if (senha == null || senha.isEmpty()) {
      throw new IllegalArgumentException(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
    }
    Argon2Parameters parametros = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withMemoryAsKB(MEMORIA_KIB)
        .withIterations(PASSADAS)
        .withParallelism(PARALELISMO)
        .withSalt(sal)
        .withSecret(pimenta)
        .build();
    Argon2BytesGenerator gerador = new Argon2BytesGenerator();
    gerador.init(parametros);
    byte[] chave = new byte[TAMANHO_DA_CHAVE];
    byte[] senhaEmBytes = senha.getBytes(StandardCharsets.UTF_8);
    try {
      gerador.generateBytes(senhaEmBytes, chave);
      return chave;
    } finally {
      Arrays.fill(senhaEmBytes, (byte) 0);
    }
  }

  /** Pimenta em Base64, com pelo menos 32 bytes. Vazia ou curta: o cofre fica fechado. */
  static byte[] lerPimenta(String pimentaBase64) {
    if (pimentaBase64 == null || pimentaBase64.isBlank()) return null;
    try {
      byte[] valor = Base64.getDecoder().decode(pimentaBase64.trim());
      return valor.length >= 32 ? valor : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
