package br.com.phdigitalcode.azzo.agenda.pro.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Equivalente Spring de {@code quarkus-cache} (Caffeine) via {@code @EnableCaching} +
 * {@code CaffeineCacheManager}. Caches portados: {@code rbac-user-permissions} (5 min TTL /
 * 200k entradas), usado por {@code PermissionService} (RBAC fino); {@code menu-routes-by-tenant-role}
 * (5 min TTL / 10k entradas), usado por {@code MenuRouteCache} (menu dinamico, modulo {@code auth}).
 *
 * <p>{@code cnpj-cache} pertence ao modulo {@code company} — o nome/TTL e declarado aqui de
 * antemao (mesmo valor do {@code application.properties} original) para que o modulo futuro so
 * precise usar {@code @Cacheable(cacheNames = "...")} sem reconfigurar o cache manager.
 */
@Configuration
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(
        buildCache("rbac-user-permissions", Duration.ofMinutes(5), 200_000),
        buildCache("menu-routes-by-tenant-role", Duration.ofMinutes(5), 10_000),
        buildCache("cnpj-cache", Duration.ofHours(24), 500)));
    return manager;
  }

  private CaffeineCache buildCache(String name, Duration expireAfterWrite, long maximumSize) {
    return new CaffeineCache(
        name,
        Caffeine.newBuilder()
            .expireAfterWrite(expireAfterWrite)
            .maximumSize(maximumSize)
            .build());
  }
}
