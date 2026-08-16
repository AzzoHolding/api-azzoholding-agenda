package br.com.phdigitalcode.azzo.agenda.pro.config;

import java.io.File;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Espelha {@code %dev.quarkus.http.ssl-port}/{@code %dev.quarkus.http.insecure-requests=enabled}
 * do Quarkus original: em dev, HTTPS na porta 8443 fica disponivel AO MESMO TEMPO que o HTTP
 * normal em {@code server.port} — nao substitui, adiciona.
 *
 * <p>O Spring Boot so suporta um conector via {@code server.ssl.*}/{@code server.port} (um ou
 * outro, nao os dois simultaneamente), por isso o segundo conector Tomcat e adicionado
 * manualmente aqui. {@code cert.pem}/{@code key.pem} sao os mesmos certificados autoassinados
 * (CN=localhost) do Quarkus original, na raiz do projeto (nao versionados, ver {@code .gitignore}
 * — cada dev copia/gera o proprio, mesma convencao do azzo-agenda-pro).
 *
 * <p>{@code @Profile("dev")}: nunca ativo em {@code prod}/{@code test}, mesma postura do
 * {@code %dev.} do original.
 *
 * <p>Caminhos de certificado sao resolvidos para ABSOLUTOS antes de chegar ao Tomcat: caminhos
 * relativos (ex.: {@code cert.pem}) sao resolvidos pelo Tomcat contra {@code catalina.base} (um
 * diretorio temporario interno do Spring Boot), nao contra o diretorio de trabalho do processo —
 * sem isso, o boot falha com {@code FileNotFoundException} apontando para dentro de
 * {@code %TEMP%\tomcat.<porta>.<id>\}.
 */
@Configuration
@Profile("dev")
public class DevHttpsConnectorConfig {

  @Value("${app.dev.ssl-port:8443}")
  private int sslPort;

  @Value("${app.dev.ssl-cert-file:cert.pem}")
  private String certificateFile;

  @Value("${app.dev.ssl-key-file:key.pem}")
  private String certificateKeyFile;

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> devHttpsConnectorCustomizer() {
    return factory -> factory.addAdditionalConnectors(createSslConnector());
  }

  private Connector createSslConnector() {
    Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    connector.setPort(sslPort);
    connector.setScheme("https");
    connector.setSecure(true);

    Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
    protocol.setSSLEnabled(true);

    SSLHostConfig sslHostConfig = new SSLHostConfig();
    SSLHostConfigCertificate certificate =
        new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
    certificate.setCertificateFile(toAbsolutePath(certificateFile));
    certificate.setCertificateKeyFile(toAbsolutePath(certificateKeyFile));
    sslHostConfig.addCertificate(certificate);
    protocol.addSslHostConfig(sslHostConfig);

    return connector;
  }

  private String toAbsolutePath(String path) {
    File file = new File(path);
    return file.isAbsolute() ? file.getAbsolutePath()
        : new File(System.getProperty("user.dir"), path).getAbsolutePath();
  }
}
