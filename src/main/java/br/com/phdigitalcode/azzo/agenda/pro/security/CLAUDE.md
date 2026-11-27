# security/ — autenticação, autorização, tenant e proteção de borda

Contexto geral da API: `CLAUDE.md` na raiz do `api_agenda`. **Esta pasta é sensível:** o que mudar aqui vale para todas as
rotas. Já houve pentest (SEG-001, SEG-018 e outros) e vários achados estão documentados nos comentários das classes.

## O que tem

| Assunto | Classes |
|---|---|
| Token e sessão | `JwtService`, `JwtPrincipal`, `JwtAuthenticationFilter` (ponte cookie → Bearer, valida o JWT e confere revogação), `AuthCookieService`, `RefreshTokenService`, `TokenRevocationService`, `PemKeyUtils` |
| Senha e MFA | `PasswordPolicyValidator`, `TotpService` |
| Cifra | `EncryptionService` (AES-256; segredo do MFA e outros dados cifrados; `ENCRYPTION_KEY` obrigatória em prod) |
| Tenant | `ContextoTenant` (escopo de requisição; falha se faltar tenant; `X-Tenant-Id` só no fluxo interno) |
| Permissões | `@RequiresPermission` + `RequiresPermissionAspect`, `PermissionService`, `RbacPermissionCache` (5 min, 200 mil entradas), `MenuRouteCache` (5 min, 10 mil), `AcessoPorPerfil`, `AcessoDeProfissional`, `ResolucaoDeAcesso` |
| Licença | `LicenseFilter` |
| Rotas internas | `InternalApiKeyFilter`, `CaminhoDaRequisicao` |
| Limite de uso | `RateLimitFilter` (bucket4j, por IP e por rota), `PublicCnpjRateLimiter` |
| Auditoria da requisição | `RequestAuditContext`, `RequestAuditContextFilter` |
| Cabeçalhos | `SecurityHeadersFilter` |
| Webhook | `WebhookVerifyTokenHashService` |

## Regras que não se quebram

1. **`LicenseFilter`:** plano vencido responde `402 PLAN_EXPIRED` em toda rota autenticada de não-ADMIN, exceto login, registro,
   refresh, catálogo público, perfil do salão, menus, `/me` e billing. `/webhook/*` é isento. O filtro ficou sem porte até
   28/09/2026; sem ele um tenant inadimplente seguia com acesso total.
2. **`InternalApiKeyFilter`:** uma chave **por serviço** (SEG-018). A do assistant só abre `/api/v1/internal/assistant/**`; a da
   Gestão só `/api/v1/internal/plans/**`. A chave única antiga ainda vale enquanto `app.internal.aceitar-chave-legada` for
   `true` (transição). Compara em tempo constante e **pelo caminho normalizado** (`CaminhoDaRequisicao`: decodificado, sem
   context path, sem parâmetros de caminho). A URI bruta já deixou `/api/v1/%69nternal/...` passar sem chave (SEG-001).
3. **Tenant nunca vem do corpo ou da query.** Só do JWT, ou do header no fluxo interno depois do `InternalApiKeyFilter`.
4. **IP do cliente:** `RateLimitFilter`, `RequestAuditContextFilter` e billing usam o **primeiro valor de `X-Forwarded-For`**.
   Se entrar CDN/proxy na frente, o Caddy precisa confiar nele (`trusted_proxies`), senão todo mundo vira o IP do proxy
   e o rate limit bloqueia vários usuários de uma vez. Plano em `PLANO-CDN-CLOUDFLARE.md` (raiz do projeto).
5. **Em `prod` o startup é fail-closed** (`config/StartupSecurityValidator`): sem chaves JWT, `ENCRYPTION_KEY`,
   `LGPD_CONTACT_EMAIL` e `META_APP_SECRET`, a API não sobe.
6. Não logue token, cookie nem chave. Use `util/LogSanitizer`.

## Cuidados

- Filtro novo é bean `@Component`: se tiver mais de um construtor, o principal leva `@Autowired` e precisa de teste que carregue
  pelo Spring (a queda de 20/10/2026 foi exatamente o `InternalApiKeyFilter`).
- Mudou regra de acesso? Rode os testes de `src/test/.../security/` e teste à mão um papel que **não** deveria ter acesso.
- Cache de permissão/menu dura até 5 min. Há invalidação explícita (`RbacPermissionCache` tem `@CacheEvict(allEntries = true)`);
  se uma alteração de perfil não aparecer na hora, confira se o caminho de escrita chama essa invalidação.
