package br.com.phdigitalcode.azzo.agenda.pro.security;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Equivalente Spring de {@code modules/security/infrastructure/RequiresPermission.java}
 * (interceptor CDI custom no Quarkus, {@code @InterceptorBinding}). Aqui e um marcador simples,
 * processado por AOP em {@link RequiresPermissionAspect} (ver risco 4 do inventario — RBAC fino).
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface RequiresPermission {
  String value();
}
