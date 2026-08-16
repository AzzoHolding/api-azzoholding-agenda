package br.com.phdigitalcode.azzo.agenda.pro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do api_agenda (migracao Spring Boot do azzo-agenda-pro / Quarkus).
 *
 * <p>Ver {@code backend/spring-boot-app/MIGRACAO-QUARKUS-SPRING.md} para o inventario completo,
 * o plano de migracao e o registro do que foi portado em cada etapa.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
