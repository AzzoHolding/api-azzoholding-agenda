package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;

/**
 * Espelha {@code infrastructure/storage/MinioStorageService.java} do Quarkus — <b>o recorte usado
 * pela importacao em massa de estoque</b>.
 *
 * <p>O original expoe tambem {@code salvarArquivoImportacaoServicos/Especialidades}. Pertencem a
 * modulos ainda nao migrados ({@code services}, {@code auth}); traze-los agora seria codigo morto.
 * Acrescentar aqui quando cada modulo for portado — as chaves de storage ja seguem o mesmo formato
 * {@code tenant/{tenantId}/...}.
 *
 * <p>{@code salvarArquivoSalaoLogo}/{@code removerArquivoSalaoLogo} foram portados junto com o
 * modulo {@code salon} (perfil do estabelecimento), usados por {@code ServicoSalonProfile}.
 *
 * <p>{@code salvarArquivoFiscalDanfe}/{@code removerArquivoFiscalDanfe} foram portados na
 * fronteira 5 de {@code fiscal} (DANFE), usados por {@code FiscalDanfeJobService}/
 * {@code FiscalJobWorker}.
 *
 * <p>{@code salvarArquivoImportacaoClientes} e o par {@code salvarArquivoClienteAvatar}/
 * {@code removerArquivoClienteAvatar} foram portados junto com o fechamento do gap de
 * {@code customers} (importacao em massa + avatar de cliente), usados por
 * {@code ServicoImportacaoClientes}/{@code ClientesController} e {@code ClienteService}.
 *
 * <p><b>Comportamentos do original preservados:</b>
 *
 * <ol>
 *   <li>Com o storage <b>desabilitado</b>, {@code salvarArquivoImportacao} <b>nao falha</b>: gera e
 *       devolve a storage key sem enviar nada. O job e criado normalmente e so vai falhar mais
 *       tarde, no processamento, quando {@code baixarArquivo} recusar.
 *   <li>{@code isStorageOperacional()} devolve {@code true} quando o storage esta desabilitado — e
 *       o que faz o health check ficar UP em ambiente sem MinIO.
 *   <li>{@code removerArquivoImportacao} e best-effort: nunca propaga falha, so loga.
 *   <li>{@code gerarUrlAssinadaLeitura} <b>nao</b> gera presigned URL do MinIO: devolve a URL do
 *       proxy do proprio backend, para nunca expor o storage ao browser. O endpoint
 *       {@code /api/v1/storage/proxy} pertence ao modulo {@code storage}, ainda nao migrado — a URL
 *       ja e emitida, mas so respondera quando esse modulo for portado.
 * </ol>
 */
@Service
public class MinioStorageService {

  private static final Logger LOG = LoggerFactory.getLogger(MinioStorageService.class);

  private final boolean minioEnabled;
  private final String endpoint;
  private final String accessKey;
  private final String secretKey;
  private final String bucket;
  private final int presignExpirationMinutes;
  private final String proxyBaseUrl;

  private volatile MinioClient client;
  private volatile boolean operacional;

  public MinioStorageService(
      @Value("${app.storage.minio.enabled:false}") boolean minioEnabled,
      @Value("${app.storage.minio.endpoint:http://localhost:9000}") String endpoint,
      @Value("${app.storage.minio.access-key:minio}") String accessKey,
      @Value("${app.storage.minio.secret-key:minio123}") String secretKey,
      @Value("${app.storage.minio.bucket:azzo-importacoes}") String bucket,
      @Value("${app.storage.minio.presign-expiration-minutes:10}") int presignExpirationMinutes,
      @Value("${app.storage.minio.proxy-base-url:http://localhost:8080}") String proxyBaseUrl) {
    this.minioEnabled = minioEnabled;
    this.endpoint = endpoint;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.bucket = bucket;
    this.presignExpirationMinutes = presignExpirationMinutes;
    this.proxyBaseUrl = proxyBaseUrl;
  }

  @PostConstruct
  void init() {
    inicializarClienteSeNecessario();
  }

  /**
   * Envia o arquivo de importacao de estoque e devolve a storage key. Com o storage desabilitado
   * devolve a key sem enviar nada (comportamento do original).
   */
  public String salvarArquivoImportacao(byte[] arquivo, String nomeArquivo, UUID tenantId) {
    String storageKey = gerarStorageKey(nomeArquivo, tenantId);
    if (!minioEnabled) return storageKey;

    inicializarClienteSeNecessario();
    if (!operacional) {
      throw new IllegalStateException("Storage MinIO indisponivel para upload.");
    }

    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(storageKey)
              .contentType(resolverContentType(nomeArquivo))
              .stream(new ByteArrayInputStream(arquivo), arquivo.length, -1)
              .build());
      return storageKey;
    } catch (Exception exception) {
      LOG.error("Falha ao enviar arquivo de importacao para o MinIO (key={}).", storageKey, exception);
      throw new IllegalStateException("Falha ao enviar arquivo para o storage.", exception);
    }
  }

  /**
   * Envia o PDF do DANFE gerado e devolve a storage key. Espelha {@code salvarArquivoFiscalDanfe}
   * do original — porta junto com a fronteira 5 do modulo fiscal.
   */
  public String salvarArquivoFiscalDanfe(byte[] arquivo, String invoiceId, UUID tenantId) {
    String nomeArquivo = "danfe-" + (invoiceId == null || invoiceId.isBlank() ? UUID.randomUUID() : invoiceId) + ".pdf";
    String storageKey = gerarStorageKeyFiscalDanfe(nomeArquivo, tenantId);
    if (!minioEnabled) return storageKey;

    inicializarClienteSeNecessario();
    if (!operacional) {
      throw new IllegalStateException("Storage MinIO indisponivel para upload de DANFE.");
    }

    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(storageKey)
              .contentType("application/pdf")
              .stream(new ByteArrayInputStream(arquivo), arquivo.length, -1)
              .build());
      return storageKey;
    } catch (Exception exception) {
      LOG.error("Falha ao enviar DANFE para o MinIO (key={}).", storageKey, exception);
      throw new IllegalStateException("Falha ao enviar DANFE para o storage.", exception);
    }
  }

  /**
   * Espelha {@code salvarArquivoImportacaoClientes} do original — usado por
   * {@code ServicoImportacaoClientes}/{@code ClientesController}.
   */
  public String salvarArquivoImportacaoClientes(byte[] arquivo, String nomeArquivo, UUID tenantId) {
    String storageKey = gerarStorageKeyClientes(nomeArquivo, tenantId);
    if (!minioEnabled) return storageKey;

    inicializarClienteSeNecessario();
    if (!operacional) {
      throw new IllegalStateException("Storage MinIO indisponivel para upload.");
    }

    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(storageKey)
              .contentType(resolverContentType(nomeArquivo))
              .stream(new ByteArrayInputStream(arquivo), arquivo.length, -1)
              .build());
      return storageKey;
    } catch (Exception exception) {
      LOG.error("Falha ao enviar arquivo de importacao de clientes para o MinIO (key={}).", storageKey, exception);
      throw new IllegalStateException("Falha ao enviar arquivo para o storage.", exception);
    }
  }

  /** Espelha {@code salvarArquivoClienteAvatar} do original — usado por {@code ClienteService}. */
  public String salvarArquivoClienteAvatar(
      byte[] arquivo, String nomeArquivo, String contentType, UUID tenantId, UUID clientId) {
    String nomeEfetivo = nomeArquivo == null || nomeArquivo.isBlank() ? "avatar-cliente.webp" : nomeArquivo;
    String storageKey = gerarStorageKeyClienteAvatar(nomeEfetivo, tenantId, clientId);
    if (!minioEnabled) return storageKey;

    inicializarClienteSeNecessario();
    if (!operacional) {
      throw new IllegalStateException("Storage MinIO indisponivel para upload do avatar do cliente.");
    }

    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(storageKey)
              .contentType(contentType == null || contentType.isBlank() ? resolverContentType(nomeEfetivo) : contentType)
              .stream(new ByteArrayInputStream(arquivo), arquivo.length, -1)
              .build());
      return storageKey;
    } catch (Exception exception) {
      LOG.error("Falha ao enviar avatar do cliente para o MinIO (key={}).", storageKey, exception);
      throw new IllegalStateException("Falha ao enviar avatar do cliente para o storage.", exception);
    }
  }

  /** Espelha {@code salvarArquivoSalaoLogo} do original — usado por {@code ServicoSalonProfile}. */
  public String salvarArquivoSalaoLogo(byte[] arquivo, String nomeArquivo, String contentType, UUID tenantId) {
    String nomeEfetivo = nomeArquivo == null || nomeArquivo.isBlank() ? "logo-estabelecimento.webp" : nomeArquivo;
    String storageKey = gerarStorageKeySalaoLogo(nomeEfetivo, tenantId);
    if (!minioEnabled) return storageKey;

    inicializarClienteSeNecessario();
    if (!operacional) {
      throw new IllegalStateException("Storage MinIO indisponivel para upload da logo do salao.");
    }

    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(storageKey)
              .contentType(contentType == null || contentType.isBlank() ? resolverContentType(nomeEfetivo) : contentType)
              .stream(new ByteArrayInputStream(arquivo), arquivo.length, -1)
              .build());
      return storageKey;
    } catch (Exception exception) {
      LOG.error("Falha ao enviar logo do salao para o MinIO (key={}).", storageKey, exception);
      throw new IllegalStateException("Falha ao enviar logo do salao para o storage.", exception);
    }
  }

  public byte[] baixarArquivo(String storageKey, UUID tenantId) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("Storage key obrigatoria para download.");
    }
    validarEscopoTenant(storageKey, tenantId);
    if (!minioEnabled) {
      throw new IllegalStateException("Storage MinIO desabilitado para download de arquivo.");
    }

    inicializarClienteSeNecessario();
    if (!operacional) {
      throw new IllegalStateException("Storage MinIO indisponivel para download.");
    }

    try (var stream =
            client.getObject(GetObjectArgs.builder().bucket(bucket).object(storageKey).build());
        var output = new ByteArrayOutputStream()) {
      stream.transferTo(output);
      return output.toByteArray();
    } catch (Exception exception) {
      LOG.error("Falha ao baixar objeto no MinIO (key={}).", storageKey, exception);
      throw new IllegalStateException("Falha ao baixar arquivo do storage.", exception);
    }
  }

  /** Best-effort: nenhuma falha de remocao propaga para o chamador. */
  public void removerArquivoImportacao(String storageKey, UUID tenantId) {
    if (!minioEnabled || storageKey == null || storageKey.isBlank()) return;
    validarEscopoTenant(storageKey, tenantId);

    inicializarClienteSeNecessario();
    if (!operacional) {
      LOG.warn("Storage indisponivel para remover objeto expirado (key={}).", storageKey);
      return;
    }

    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
    } catch (Exception exception) {
      LOG.warn("Falha ao remover objeto de importacao no MinIO (key={}).", storageKey, exception);
    }
  }

  /** Best-effort: espelha {@code removerArquivoFiscalDanfe} do original. */
  public void removerArquivoFiscalDanfe(String storageKey, UUID tenantId) {
    if (!minioEnabled || storageKey == null || storageKey.isBlank()) return;
    validarEscopoTenant(storageKey, tenantId);

    inicializarClienteSeNecessario();
    if (!operacional) {
      LOG.warn("Storage indisponivel para remover DANFE (key={}).", storageKey);
      return;
    }

    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
    } catch (Exception exception) {
      LOG.warn("Falha ao remover DANFE no MinIO (key={}).", storageKey, exception);
    }
  }

  /** Best-effort: espelha {@code removerArquivoClienteAvatar} do original. */
  public void removerArquivoClienteAvatar(String storageKey, UUID tenantId) {
    if (!minioEnabled || storageKey == null || storageKey.isBlank()) return;
    validarEscopoTenant(storageKey, tenantId);

    inicializarClienteSeNecessario();
    if (!operacional) {
      LOG.warn("Storage indisponivel para remover avatar do cliente (key={}).", storageKey);
      return;
    }

    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
    } catch (Exception exception) {
      LOG.warn("Falha ao remover avatar do cliente no MinIO (key={}).", storageKey, exception);
    }
  }

  /** Best-effort: espelha {@code removerArquivoSalaoLogo} do original. */
  public void removerArquivoSalaoLogo(String storageKey, UUID tenantId) {
    if (!minioEnabled || storageKey == null || storageKey.isBlank()) return;
    validarEscopoTenant(storageKey, tenantId);

    inicializarClienteSeNecessario();
    if (!operacional) {
      LOG.warn("Storage indisponivel para remover logo do salao (key={}).", storageKey);
      return;
    }

    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
    } catch (Exception exception) {
      LOG.warn("Falha ao remover logo do salao no MinIO (key={}).", storageKey, exception);
    }
  }

  /** URL do proxy do backend — nunca expoe o MinIO diretamente ao browser. */
  public String gerarUrlAssinadaLeitura(String storageKey, UUID tenantId) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("Storage key obrigatoria para gerar URL.");
    }
    validarEscopoTenant(storageKey, tenantId);
    String base =
        proxyBaseUrl.endsWith("/")
            ? proxyBaseUrl.substring(0, proxyBaseUrl.length() - 1)
            : proxyBaseUrl;
    return base
        + "/api/v1/storage/proxy?key="
        + URLEncoder.encode(storageKey, StandardCharsets.UTF_8);
  }

  public Instant calcularExpiracaoUrlAssinada() {
    return Instant.now().plusSeconds(Math.max(presignExpirationMinutes, 1) * 60L);
  }

  public boolean isStorageHabilitado() {
    return minioEnabled;
  }

  public boolean isStorageOperacional() {
    if (!minioEnabled) return true;
    inicializarClienteSeNecessario();
    return operacional;
  }

  private synchronized void inicializarClienteSeNecessario() {
    if (!minioEnabled || operacional) return;
    try {
      client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();

      boolean bucketExiste = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!bucketExiste) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
      operacional = true;
    } catch (Exception exception) {
      operacional = false;
      LOG.error(
          "Falha ao inicializar cliente MinIO. Fluxos que dependem de upload ficarao indisponiveis.",
          exception);
    }
  }

  private String gerarStorageKeyFiscalDanfe(String nomeArquivo, UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TenantId obrigatorio para gerar storage key.");
    }
    YearMonth periodoAtual = YearMonth.now();
    return String.format(
        Locale.ROOT,
        "tenant/%s/fiscal/danfe/%d/%02d/%s-%s",
        tenantId,
        periodoAtual.getYear(),
        periodoAtual.getMonthValue(),
        UUID.randomUUID(),
        sanitizarNomeArquivo(nomeArquivo));
  }

  private String gerarStorageKeyClientes(String nomeArquivo, UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TenantId obrigatorio para gerar storage key.");
    }
    YearMonth periodoAtual = YearMonth.now();
    return String.format(
        Locale.ROOT,
        "tenant/%s/clientes/importacoes/%d/%02d/%s-%s",
        tenantId,
        periodoAtual.getYear(),
        periodoAtual.getMonthValue(),
        UUID.randomUUID(),
        sanitizarNomeArquivo(nomeArquivo));
  }

  private String gerarStorageKeyClienteAvatar(String nomeArquivo, UUID tenantId, UUID clientId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TenantId obrigatorio para gerar storage key.");
    }
    if (clientId == null) {
      throw new IllegalArgumentException("ClientId obrigatorio para gerar storage key.");
    }
    return String.format(
        Locale.ROOT,
        "tenant/%s/clients/%s/avatar/%s-%s",
        tenantId,
        clientId,
        UUID.randomUUID(),
        sanitizarNomeArquivo(nomeArquivo));
  }

  private String gerarStorageKeySalaoLogo(String nomeArquivo, UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TenantId obrigatorio para gerar storage key.");
    }
    return String.format(
        Locale.ROOT, "tenant/%s/salao/logo/%s-%s", tenantId, UUID.randomUUID(), sanitizarNomeArquivo(nomeArquivo));
  }

  private String gerarStorageKey(String nomeArquivo, UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TenantId obrigatorio para gerar storage key.");
    }
    YearMonth periodoAtual = YearMonth.now();
    return String.format(
        Locale.ROOT,
        "tenant/%s/estoque/importacoes/%d/%02d/%s-%s",
        tenantId,
        periodoAtual.getYear(),
        periodoAtual.getMonthValue(),
        UUID.randomUUID(),
        sanitizarNomeArquivo(nomeArquivo));
  }

  private void validarEscopoTenant(String storageKey, UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TenantId obrigatorio para validar storage key.");
    }
    String prefixoEsperado = "tenant/" + tenantId + "/";
    if (!storageKey.startsWith(prefixoEsperado)) {
      throw new IllegalStateException("Storage key fora do escopo do tenant.");
    }
  }

  private String sanitizarNomeArquivo(String nomeArquivo) {
    if (nomeArquivo == null || nomeArquivo.isBlank()) {
      return "arquivo-importacao.xlsx";
    }
    return nomeArquivo.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase(Locale.ROOT);
  }

  private String resolverContentType(String nomeArquivo) {
    String nome = nomeArquivo == null ? "" : nomeArquivo.toLowerCase(Locale.ROOT);
    if (nome.endsWith(".webp")) return "image/webp";
    if (nome.endsWith(".png")) return "image/png";
    if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) return "image/jpeg";
    if (nome.endsWith(".csv")) return "text/csv";
    if (nome.endsWith(".xlsx")) {
      return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    return "application/octet-stream";
  }
}
