package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

/**
 * Equivalente Spring (AOP) de
 * {@code modules/security/infrastructure/RequiresPermissionInterceptor.java} (interceptor CDI
 * custom no Quarkus). Intercepta qualquer metodo (ou classe) anotado com {@link RequiresPermission}
 * e valida a permissao antes de prosseguir — mesma prioridade "AUTHORIZATION" do original (roda
 * depois da autenticacao JWT, que ja populou o {@code SecurityContext} via
 * {@link JwtAuthenticationFilter}).
 */
@Aspect
@Component
public class RequiresPermissionAspect {

  private final PermissionService permissionService;

  public RequiresPermissionAspect(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Around("@annotation(br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission) "
      + "|| @within(br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission)")
  public Object validatePermission(ProceedingJoinPoint joinPoint) throws Throwable {
    String requiredPermission = resolveRequiredPermission(joinPoint);
    if (requiredPermission != null && !requiredPermission.isBlank()) {
      permissionService.validarPermissao(requiredPermission);
    }
    return joinPoint.proceed();
  }

  private String resolveRequiredPermission(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();

    RequiresPermission methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
    if (methodAnnotation != null) return methodAnnotation.value();

    RequiresPermission classAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), RequiresPermission.class);
    return classAnnotation != null ? classAnnotation.value() : null;
  }
}
