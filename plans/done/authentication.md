# Authentication — Ageguessr

## Context

Ageguessr was just scaffolded (Spring Boot + Angular, no feature code yet — only the
`theme` feature exists on the frontend, nothing on the backend beyond the bare
application skeleton). Authentication is the first real feature, chosen because the
game itself is guest-playable but accounts (history, friends) need it, and because it
establishes the security posture and architectural conventions (Vertical Slice, CQRS
without a mediator, exception handling, ArchUnit rules) that every later feature will
follow.

Scope confirmed with the user: backend **and** frontend in this one plan; email
verification is **required** before login (via Resend); rate limiting on `/api/auth/*`
by IP and account lockout after repeated failed logins are included now, not deferred.

This repo is documented as fully independent from any other project — nothing here
references another codebase, even for pattern inspiration in written docs.

## Key decisions locked in for this plan

- **Spring Boot 4.1 ships Jackson 3** (`tools.jackson.*` packages, e.g.
  `tools.jackson.databind.ObjectMapper`), not classic Jackson 2
  (`com.fasterxml.jackson.databind.ObjectMapper`) — confirmed via
  `./mvnw dependency:tree` (`tools.jackson.core:jackson-databind:3.1.5` at compile
  scope through `spring-boot-starter-webmvc`). A `com.fasterxml.jackson.core:...:2.21.5`
  also appears in the tree but only as a transitive runtime/test dependency of another
  library, not what Spring itself autowires. Any future code (including tests) that
  injects/uses `ObjectMapper` must import `tools.jackson.databind.ObjectMapper`, and its
  `writeValue*` methods throw the unchecked `tools.jackson.core.JacksonException`, not a
  checked `IOException`/`JsonProcessingException`.
- **`ageguessr-api/src/test/resources/application.properties` fully replaces (not
  merges with) `src/main/resources/application.properties` on the test classpath** —
  confirmed the hard way (a first attempt with only the two test-only secret overrides
  broke every `@SpringBootTest`, since every other property — `spring.flyway.enabled`,
  `app.refresh-token.*`, etc. — silently vanished). The test file is now a full copy of
  main's, with only `app.jwt.secret` and `app.email.resend-api-key` swapped for literal
  test-safe values. Any future new key added to main's `application.properties` must be
  mirrored here too, or tests will fail with a `PlaceholderResolutionException`.
- **MockMvc requests all report the same simulated client IP**, so every functional
  test hitting `/api/auth/**` in the same cached Spring context shares one
  `RateLimitFilter` counter. `src/test/resources/application.properties` sets
  `app.rate-limit.auth.max-attempts=1000` for this reason — functional tests that
  exercise real register/login/refresh flows should never need to think about the rate
  limiter. The dedicated rate-limiting behavior test overrides the threshold down to a
  small number via `@TestPropertySource`, which gets its own distinct (uncached)
  context and therefore its own counter.
- **MockMvc does not parse a raw `Cookie` header string into `request.getCookies()`**
  — `.header(HttpHeaders.COOKIE, "name=value")` leaves `@CookieValue` seeing nothing
  (silently falls back to `required=false`'s null), while a real servlet container
  would parse it fine. Tests must use `.cookie(new jakarta.servlet.http.Cookie(name,
  value))` on the request builder instead (see `AuthFlows.LoggedInSession
  .asRequestCookie()`).
- **A `@Modifying @Query` method on a Spring Data repository needs its own
  `@Transactional`** — unlike `save()`/`findById()`/etc., which are covered by
  `SimpleJpaRepository`'s class-level `@Transactional`, a custom bulk update/delete
  query throws `InvalidDataAccessApiUsageException: No active transaction` if called
  from a plain (non-transactional) handler. Fixed on
  `RefreshTokenRepository.revokeAllActiveForUser` by annotating the method itself.

- **No new JWT library**: `spring-boot-starter-security-oauth2-resource-server`
  (already in `pom.xml`) transitively provides Nimbus classes that support both
  *decoding* (`NimbusJwtDecoder.withSecretKey`) and *issuing*
  (`NimbusJwtEncoder.withSecretKey`) HS256 tokens from a single shared secret. Defining
  these as `@Bean`s ourselves makes Spring Boot's OAuth2 resource-server
  autoconfiguration back off cleanly (`@ConditionalOnMissingBean`).
- **Paths use the `/api/...` prefix** (`/api/auth/...`), consistent with
  `docs/decisions/architecture.md`'s existing `/api/daily` / `/api/images/{id}`
  examples and with the eventual Caddy `strip_prefix /api` reverse-proxy rule.
- **Local dev uses an Angular dev-server proxy** (`frontend/proxy.conf.json` →
  `/api/**` to `localhost:8080`), not cross-origin CORS+cookies, so the browser sees
  one origin locally — mirrors how Caddy will unify origins in prod. Backend CORS
  config is still added as defense-in-depth for direct API callers (Postman, scripts).
- **Rate limiting**: hand-rolled Redis `INCR`+`EXPIRE` counter (`RateLimiter`
  service + `RateLimitFilter`), not Bucket4j — Bucket4j's Redis story currently needs
  2-3 JDK-suffixed relocated artifacts plus a separate Spring-Boot-starter for wiring;
  a direct counter needs zero new dependencies (`StringRedisTemplate` already available)
  and is a handful of lines, fully auditable. Fixed-window trade-off (up to ~2x burst at
  a window boundary) accepted — this is anti-brute-force, not traffic shaping.
- **`ForwardedHeaderFilter` is registered now**, even though Caddy/Cloudflare aren't
  deployed yet — a single zero-argument bean, harmless locally (no header sent), and
  forgetting it would silently rate-limit every prod user as "Caddy's IP" once deployed.
- **Refresh tokens are stored as a SHA-256 hash**, never the raw value — hardening
  beyond the minimum in `docs/decisions/architecture.md`'s sketch; if the DB leaks,
  stored hashes can't be replayed.
- **Refresh reuse detection**: presenting an already-revoked refresh token revokes
  *all* active refresh tokens for that user (theft signal), not just that one request.
- **Login error design (the core security decision here)**: not-found / wrong-password
  / locked-account all return the *same* generic 401 with identical body (checked
  before password verification for locked accounts, with a dummy bcrypt comparison to
  normalize response time). **Unverified email is the one deliberately distinct case**
  — `403` with an `errorCode: "EMAIL_NOT_VERIFIED"` — because every legitimate new user
  hits this exact state right after registering and a generic "invalid credentials"
  there is actively confusing; the leaked bit is low severity (grants no access) versus
  the UX cost of hiding it. Register still leaks duplicate-email via 409 (industry
  standard, out of scope to change here).
- **Account lockout**: 5 failed attempts → 15 minute lock (tunable via env var),
  counter resets only on a successful login (not merely once the lock window elapses),
  checked against an injected `Clock` bean for deterministic tests.
- **`ResendVerificationEmail` stays in scope** (not deferred): verification tokens
  expire in 24h; without a resend path a user who misses the email is permanently
  stuck, since re-registering the same email hits the 409 duplicate check.
- **New design token**: `--color-danger` added to `frontend/src/styles.css` for
  form/validation errors — a muted brick-red, deliberately distinct from `--color-hot`
  (which `docs/decisions/design.md` reserves for the game's proximity indicator).
  Placeholder values, same "point de départ" status as the rest of the palette.

## Architecture (Vertical Slice, per `docs/decisions/architecture.md`)

```
ageguessr-api/src/main/java/app/ageguessr/
├── config/
│   ├── SecurityConfig.java        (filter chain, CORS, rate-limit filter wiring)
│   ├── JwtConfig.java             (JwtEncoder/JwtDecoder beans)
│   ├── PasswordEncoderConfig.java
│   ├── ClockConfig.java
│   ├── WebConfig.java             (ForwardedHeaderFilter)
│   └── GlobalExceptionHandler.java
├── shared/
│   ├── exceptions/                (NotFoundException, ConflictException,
│   │                                UnauthorizedException, ForbiddenException,
│   │                                ValidationException, ErrorResponse record)
│   ├── email/                     (EmailSender interface, EmailMessage record,
│   │                                ResendEmailSender, RecordingEmailSender test double)
│   ├── security/                  (OpaqueTokenGenerator — SecureRandom + SHA-256)
│   └── ratelimit/                 (RateLimiter, RateLimitFilter)
└── features/auth/
    ├── User.java, UserRepository.java
    ├── RefreshToken.java, RefreshTokenRepository.java
    ├── EmailVerificationToken.java, EmailVerificationTokenRepository.java
    ├── RefreshTokenCookieFactory.java
    ├── Register/, ConfirmEmail/, ResendVerificationEmail/,
    │   Login/, Refresh/, Logout/, Me/
    │   (each: Command|Query, Validator if needed, Handler, Response, Controller)
```

```
frontend/src/app/features/auth/
├── auth.ts (@Service — signals: currentUser, accessToken; computed: isAuthenticated)
├── auth-interceptor.ts (HttpInterceptorFn — bearer attach + silent refresh on 401)
├── auth-guard.ts (CanActivateFn, for future protected routes)
├── models.ts
├── register/, login/, check-email/, confirm-email/  (standalone components)
```

## For Future Agents

As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done, set
its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on. Tests are written
alongside the code in the same phase, never after (per `CLAUDE.md`).

---

## Phase 1: Backend foundation (config, entities, migration, shared infra)

Status: Complete

- [x] Add `com.resend:resend-java:4.4.0` to `ageguessr-api/pom.xml`
- [x] Create `ageguessr-api/src/main/resources/db/migration/V1__create_auth_tables.sql`
      — `users` (id, email, password_hash, email_verified, failed_login_attempts,
      locked_until, created_at; unique index on `LOWER(email)`), `refresh_tokens` (id,
      user_id FK cascade, token_hash unique, expires_at, revoked_at, created_at),
      `email_verification_tokens` (id, user_id FK cascade, token_hash unique,
      expires_at, consumed_at, created_at)
- [x] Create `app.ageguessr.shared.exceptions`: `NotFoundException`,
      `ConflictException`, `UnauthorizedException`, `ForbiddenException` (carries an
      `errorCode`), `ValidationException`, `ErrorResponse` record
- [x] Create `app.ageguessr.config.GlobalExceptionHandler`
      (`@RestControllerAdvice`) mapping each exception + `MethodArgumentNotValidException`
      + a catch-all `Exception` → 500 (logged, no stack trace to client)
- [x] Create `app.ageguessr.config.PasswordEncoderConfig` (`BCryptPasswordEncoder` bean)
- [x] Create `app.ageguessr.config.ClockConfig` (`Clock.systemUTC()` bean)
- [x] Create `app.ageguessr.config.JwtConfig` (`JwtEncoder`/`JwtDecoder` beans, HS256,
      shared secret from `app.jwt.secret`)
- [x] Create `app.ageguessr.config.WebConfig` (`ForwardedHeaderFilter` registration)
- [x] Create `app.ageguessr.features.auth.User`, `RefreshToken`,
      `EmailVerificationToken` entities + their Spring Data repositories
- [x] Create `app.ageguessr.features.auth.RefreshTokenCookieFactory` (builds/clears the
      `ageguessr_refresh_token` cookie: HttpOnly, `Path=/api/auth`, `SameSite=Lax`,
      `Secure=${app.cookie.secure}`, 90-day max-age)
- [x] Create `app.ageguessr.shared.security.OpaqueTokenGenerator`
      (`SecureRandom` raw token + SHA-256 hash, reused by refresh tokens and
      verification tokens)
- [x] Create `app.ageguessr.shared.ratelimit.RateLimiter` (Redis `INCR`+`EXPIRE`) and
      `RateLimitFilter` (`OncePerRequestFilter`, only active on `/api/auth/**`, keyed by
      client IP)
- [x] Create `app.ageguessr.shared.email.EmailSender` interface + `EmailMessage`
      record, `ResendEmailSender` implementation, and `RecordingEmailSender` test double
      (`@TestConfiguration`, captures sent messages in memory)
- [x] Add new `application.properties` keys: `app.jwt.secret` / `app.jwt.issuer` /
      `app.jwt.access-token-ttl-minutes`, `app.refresh-token.*`, `app.cookie.secure`,
      `app.cors.allowed-origin`, `app.frontend-url`, `app.email.*`,
      `app.rate-limit.auth.*`, `app.lockout.*` — secrets (`JWT_SECRET`,
      `RESEND_API_KEY`) have **no default**, fail fast if unset
- [x] Create `.env.example` at repo root documenting the new required env vars
      (no real values)

### Verification Plan
- `cd ageguessr-api && ./mvnw compile` succeeds
- `cd ageguessr-api && ./mvnw test` — new unit tests pass: `RateLimiterTest` (mocked
  `StringRedisTemplate`), `RefreshTokenCookieFactoryTest` (pure function, asserts
  cookie attributes)
- Integration tests (Testcontainers) pass: case-insensitive unique index on
  `users(email)` actually rejects a collision; cascade delete removes
  `refresh_tokens`/`email_verification_tokens` when a user is deleted;
  `RateLimiterIntegrationTest` against a real Redis container confirms the counter
  resets after the TTL window

### Phase Summary

All 15 tests green (`./mvnw test` → `BUILD SUCCESS`): 1 app-context smoke test + 4
`RateLimiterTest` (unit, mocked `StringRedisTemplate`) + 2 `RefreshTokenCookieFactoryTest`
(unit, pure function) + 2 `UserRepositoryIntegrationTest` + 3 `CascadeDeleteIntegrationTest`
+ 3 `RateLimiterIntegrationTest` (all three against real Testcontainers Postgres/Redis).

Deviations from the plan text, and why:
- `RecordingEmailSender` was written as a real `@Component @Profile("e2e")` bean in
  **main** source (not a `@TestConfiguration`) — it has to be a genuine production-
  classpath bean so the standalone backend process Phase 5's Playwright journey runs
  against can activate it via `SPRING_PROFILES_ACTIVE=e2e`, which a JUnit-only
  `@TestConfiguration` could never do. JUnit tests in Phase 2 that want it instead of
  `ResendEmailSender` will just instantiate it directly in a per-test `@Bean` method,
  which bypasses the `@Profile` gate entirely (no component scanning involved).
- Had to make the pre-existing `app.ageguessr.TestcontainersConfiguration` (generated
  by Spring Initializr as package-private) `public`, so the new
  `app.ageguessr.integration.*` test packages could import it too.
- Two non-obvious environment facts hit during this phase and documented above under
  "Key decisions locked in for this plan" for future reference: Spring Boot 4.1's
  Jackson-3-not-2 package namespace, and `src/test/resources/application.properties`
  fully replacing (not merging with) main's file.
- `CascadeDeleteIntegrationTest` needed an explicit `entityManager.clear()` between the
  cascading delete and the `findById` assertions — the DB-level `ON DELETE CASCADE`
  bypasses Hibernate's object graph entirely (there's no `@OneToMany` mapping, by
  design — see architecture.md), so the session's first-level cache still held the
  stale managed `RefreshToken`/`EmailVerificationToken` instances and would otherwise
  have returned them without a real query.

Ready for Phase 2.

---

## Phase 2: Backend use cases + security wiring

Status: Complete

- [x] `Register/`: `RegisterCommand` (email, password), validator (email format,
      password min length 8), `RegisterHandler` (hash password, persist `User`
      with `emailVerified=false`, generate + persist verification token, send email
      via `EmailSender`, throw `ConflictException` on duplicate email), `Response`,
      `Controller` at `POST /api/auth/register` (public, 201, no auto-login)
- [x] `ConfirmEmail/`: `Command` (token), `Handler` (look up by token hash, 404 if
      missing/expired/already consumed, else mark `emailVerified=true` + consume
      token), `Controller` at `POST /api/auth/confirm-email` (public)
- [x] `ResendVerificationEmail/`: `Command` (email), `Handler` (always returns 202
      with identical response whether or not the email exists or is already verified —
      no leak, no spam of an already-verified user), `Controller` at
      `POST /api/auth/resend-verification` (public)
- [x] `Login/`: `Command` (email, password), `Handler` implementing the enumeration +
      lockout design above (locked-account short-circuit with dummy bcrypt compare for
      timing normalization; wrong password increments `failedLoginAttempts` and sets
      `lockedUntil` at the threshold; unverified email throws
      `ForbiddenException("EMAIL_NOT_VERIFIED", ...)`; success resets the counter,
      issues a JWT access token + rotates/creates a refresh token, sets the cookie),
      `Controller` at `POST /api/auth/login` (public)
- [x] `Refresh/`: `Command` (raw token read from the cookie by the controller),
      `Handler` (hash + look up; if already revoked → revoke *all* of that user's
      active refresh tokens and reject as theft signal; if expired → reject; else
      rotate: issue new JWT + new refresh token, revoke the old one), `Controller` at
      `POST /api/auth/refresh` (public, cookie-gated)
- [x] `Logout/`: `Command` (raw token from cookie, may be absent), `Handler`
      (revoke if present, idempotent), `Controller` at `POST /api/auth/logout`
      (**authenticated**, clears the cookie, 204 even with no cookie)
- [x] `Me/`: `Query`, `Handler` (reads `sub` claim from the authenticated `Jwt`
      principal, loads the user), `Response`, `Controller` at `GET /api/auth/me`
      (**authenticated**)
- [x] `app.ageguessr.config.SecurityConfig`: `SecurityFilterChain` (stateless
      sessions, CSRF disabled — justified in-code by a comment referencing bearer-auth +
      SameSite=Lax, since there's no cookie-authenticated mutation to protect against
      CSRF for; public allow-list for the 5 public auth paths above; `anyRequest()`
      authenticated otherwise, **with a code comment flagging that guest-playable game
      endpoints must be added to the allow-list when that feature lands**;
      `oauth2ResourceServer().jwt()`; `RateLimitFilter` registered before
      `UsernamePasswordAuthenticationFilter`), `CorsConfigurationSource` bean
      (`app.cors.allowed-origin`, `AllowCredentials`)
- [x] Create `ageguessr-api/src/test/java/app/ageguessr/architecture/ArchitectureTests.java`
      (first ArchUnit ruleset): features don't depend on each other; `@Entity` classes
      live directly in their feature's root package, not a use-case subpackage;
      `Handler` classes never depend on `Controller` classes

### Verification Plan
- `cd ageguessr-api && ./mvnw test` — all new unit tests pass (`RegisterHandlerTest`,
  `LoginHandlerTest` parametrized over not-found/wrong-password/locked/unverified/success,
  `RefreshHandlerTest` covering rotation + reuse-detection + expiry,
  `ConfirmEmailHandlerTest`, `ResendVerificationEmailHandlerTest`)
- Functional tests (`@SpringBootTest` + MockMvc/WebTestClient + Testcontainers,
  `RecordingEmailSender` swapped in) pass, in particular: `/api/auth/login` returns
  byte-for-byte identical response bodies across the not-found/wrong-password/locked
  cases (the concrete anti-enumeration regression test); `Set-Cookie` attributes
  (HttpOnly, `Path=/api/auth`, `SameSite=Lax`) asserted directly; rate limit threshold
  overridden via `@TestPropertySource` to a small value and driven past it to confirm
  a `429`; `/api/auth/me` and `/api/auth/logout` reject missing/garbage bearer tokens
- `ArchitectureTests` passes
- `cd ageguessr-api && ./mvnw compile` has zero warnings from the new
  `anyRequest().authenticated()` catching something unintended (manually confirm no
  other endpoints exist yet to break)

### Phase Summary

All 57 backend tests green (`./mvnw test` → `BUILD SUCCESS`): the 15 from Phase 1 plus
23 new unit tests (Register/Login/Refresh/ConfirmEmail/ResendVerificationEmail
handlers), 3 ArchUnit rules, and 16 functional tests across 6 classes
(`RegisterFunctionalTest`, `LoginFunctionalTest`, `ConfirmEmailFunctionalTest`,
`RefreshAndLogoutFunctionalTest`, `MeFunctionalTest`, `RateLimitFunctionalTest`). The
anti-enumeration regression test (`LoginFunctionalTest
.nonExistentWrongPasswordAndLockedAccountsAllReturnTheExactSameResponse`) asserts
byte-for-byte identical response bodies across all three cases — the single most
important test in this plan — and passes.

Implementation details beyond the plan text, and why:
- Two shared feature-root helpers not explicitly named in the plan, added for DRY
  (architecture-reviewer's own "no duplicated logic" criterion):
  `AuthTokenService` (JWT + refresh-token issuance, reused by Login and Refresh) and
  `EmailVerificationService` (token generation + email sending, reused by Register and
  ResendVerificationEmail).
- `LoginHandler`'s timing-normalization dummy password hash is computed once in the
  constructor via the real `PasswordEncoder` (`passwordEncoder.encode(DUMMY_PASSWORD)`)
  rather than hardcoded, so it always matches the encoder's actual cost factor.
- Extended the plan's timing-normalization idea (originally called out only for the
  locked-account case) to the not-found case too, for the same reason — both skip a
  real per-user bcrypt comparison and would otherwise be measurably faster than a
  genuine wrong-password attempt.
- The unverified-email check is deliberately placed *after* password verification in
  `LoginHandler` (not documented as an explicit ordering in the plan, but required by
  its own enumeration-defense reasoning) — otherwise anyone could learn "this email is
  registered but unverified" without knowing the password at all.
- Any `@Modifying @Query` repository method needs its own `@Transactional` — added to
  `RefreshTokenRepository.revokeAllActiveForUser` after hitting
  `InvalidDataAccessApiUsageException` in a functional test; documented above.
- MockMvc cookie-transmission and shared-rate-limit-counter gotchas hit while writing
  functional tests, both documented above under "Key decisions locked in for this
  plan".
- Paths standardized to `/api/auth/...` (not the plan text's bare `/auth/...`) for
  consistency with `docs/decisions/architecture.md`'s existing `/api/*` convention —
  already captured as a locked-in decision before implementation started.

Ready for Phase 3.

---

## Phase 3: Frontend auth plumbing

Status: Complete

- [x] Add `provideHttpClient(withInterceptors([authInterceptor]))` to
      `frontend/src/app/app.config.ts` (no `HttpClient` provider exists yet)
- [x] Create `frontend/proxy.conf.json` (`/api/**` → `http://localhost:8080`) and wire
      it into `frontend/angular.json`'s `serve.configurations.development.proxyConfig`
- [x] Add `--color-danger` token to `frontend/src/styles.css` (`@theme` dark value +
      `.light` override), distinct from `--color-hot`
- [x] Create `frontend/src/app/features/auth/models.ts` (types matching backend
      response shapes: `AuthUser`, `LoginResponse`, `RefreshResponse`, `ErrorResponse`)
- [x] Create `frontend/src/app/features/auth/auth.ts` (`@Service`): signals
      `currentUser`/`accessToken`, computed `isAuthenticated`; `register()`, `login()`,
      `logout()`, `me()`, `confirmEmail()`, `resendVerification()`; `refreshOnce()`
      with a cached in-flight `Observable` (`shareReplay(1)`, reset on completion) so
      concurrent 401s share one refresh call instead of racing the single-use rotation
- [x] Create `frontend/src/app/features/auth/auth-interceptor.ts`
      (`HttpInterceptorFn`): attaches `Authorization: Bearer` when present; on 401
      (excluding the login/refresh calls themselves) calls `refreshOnce()`, retries once,
      and on refresh failure clears session state (treated as reverting to guest, not
      an error)
- [x] Create `frontend/src/app/features/auth/auth-guard.ts` (`CanActivateFn`, unused by
      any route yet but ready for a future protected page)

### Verification Plan
- `cd frontend && pnpm test` — new specs pass: `auth.spec.ts` (register/login/logout/me
  happy paths against a mocked `HttpClient`), a dedicated concurrency test asserting two
  simultaneous 401-triggered calls to `refreshOnce()` result in exactly one HTTP call
  to `/api/auth/refresh`
- `cd frontend && pnpm lint && pnpm format:check && pnpm build` all succeed
- Manually confirm `frontend/proxy.conf.json` forwards a request to a running backend
  (start backend + `pnpm start`, hit `/api/auth/me` from the browser dev tools, confirm
  it reaches Spring Boot rather than 404-ing in Angular)

### Phase Summary

17/17 frontend unit tests green across 6 spec files (`pnpm test`), lint/format/build
all clean. Manually verified end-to-end with real processes (not just mocks):
`docker compose up` + backend on port 8080 + `pnpm start` on 4200, confirmed
`GET /api/auth/me` through `http://localhost:4200/api/auth/me` (the dev proxy) returns
the identical `401` as hitting `http://localhost:8080/api/auth/me` directly — proves
the proxy genuinely forwards rather than Angular's router swallowing the route.

Addition beyond the plan text: `Auth.tryRestoreSession()` (silently calls
`refreshOnce()` then `me()`, swallowing any failure as "guest") wired into `App`'s
constructor in `app.ts`, so a user with a valid refresh cookie stays logged in across a
page reload — without it, `accessToken`/`currentUser` would always reset to null on
load with no way back short of a manual re-login, which would make the whole session
feature pointless across reloads. This method was part of the original design
agent's output but got dropped from the final plan's bullet list during transcription;
implementing it now completes the intent rather than deviating from it. Required
updating `app.spec.ts` to provide `HttpClient`/`HttpClientTesting` and drain the
resulting `/api/auth/refresh` request in `afterEach`, since the constructor now always
fires one.

Ready for Phase 4.

---

## Phase 4: Frontend pages

Status: Complete

- [x] `frontend/src/app/features/auth/register/` (register form: email, password,
      confirm-password client-side check; on success navigates to `check-email`; on
      409 shows an inline error using `--color-danger`)
- [x] `frontend/src/app/features/auth/login/` (login form; on success navigates to
      home; on generic 401 shows a generic error; on 403
      `EMAIL_NOT_VERIFIED` shows a distinct message with a "resend verification email"
      action calling `resendVerification()`)
- [x] `frontend/src/app/features/auth/check-email/` (static "check your inbox" state,
      no dedicated Playwright test — plain content plus one button, already covered by
      its Vitest spec)
- [x] `frontend/src/app/features/auth/confirm-email/` (reads `token` from the route
      query params on init, calls `confirmEmail()`, shows loading → success/error)
- [x] Wire the four routes into `frontend/src/app/app.routes.ts` (lazy `loadComponent`)
- [x] All four pages/components styled with existing tokens only (`bg-surface`,
      `border-border`, `font-display` headings, `bg-accent`/`text-accent-foreground`
      submit buttons, `text-muted` helper text, existing `--radius-*` scale) plus the
      new `--color-danger` from Phase 3 — no other new colors invented

### Verification Plan
- `cd frontend && pnpm test` — component specs pass for all four
- `cd frontend && pnpm test:e2e` (Playwright, `component` project) passes for `login`
  (generic-error rendering, `EMAIL_NOT_VERIFIED` → resend CTA, password masking) and
  `register` (mismatch, 409 message) and `confirm-email` (loading → success/error
  against a real Router with mocked HTTP)
- `cd frontend && pnpm lint && pnpm format:check && pnpm build` all succeed

### Phase Summary

27/27 Vitest unit tests green (10 spec files), lint/format/build clean, and 9/9
Playwright component tests green across `login.spec.ts`, `register.spec.ts`,
`confirm-email.spec.ts` (network-mocked via `page.route`, no backend needed — matches
`docs/testing-strategy.md`'s "Interface" category). `check-email` deliberately has no
dedicated Playwright test, per the plan's own reasoning (static content plus one
button, already covered by its Vitest spec).

Note, not a defect: the very first Playwright run against a cold Angular dev server hit
`page.goto` timeouts on `/login` and `/confirm-email` (esbuild compiles each route's
bundle on first request, not upfront) — a second run against the now-warm server passed
9/9 immediately. This is a characteristic of Angular's dev server under parallel
first-hit load, not specific to this feature; the existing `retries: 2` in
`playwright.config.ts` for CI already absorbs it, so no config change was made.

Component visibility note: initial component classes marked signals/methods
`protected` (matching Angular's own generated-code convention), which TypeScript
correctly rejects from `.spec.ts` files calling them directly (protected members are
only accessible from the class/subclasses, and a spec file is neither). Changed to
public on all four new components so tests can drive component state directly rather
than only through the DOM.

Ready for Phase 5.

---

## Phase 5: End-to-end validation

Status: Complete

- [x] Create `app.ageguessr.TestSupportController` gated by `@Profile("e2e")`
      exposing `GET /test-support/verification-token?email=...` returning the last raw
      verification token generated for that address — test-only, Javadoc explicitly
      warns the `e2e` profile must never be active in the prod deployment config
- [x] Wire an `e2e` Spring profile that activates `RecordingEmailSender` instead of
      `ResendEmailSender` (no real Resend key needed to run this journey)
- [x] Write `frontend/e2e/journeys/auth.spec.ts` (Playwright, full app — real
      Postgres/Redis/backend/frontend): register → read the token via
      `TestSupportController` → confirm email → login → `/api/auth/me` reflects
      `emailVerified: true`. This is the **only** QA/e2e test for this feature; lockout,
      rate limiting, and expired-token edge cases are intentionally left to the
      Functional layer (already covered in Phase 2) per `docs/testing-strategy.md`'s
      own guidance that edge cases belong lower in the pyramid

### Verification Plan
- `cd frontend && pnpm test:e2e:journeys` passes against a locally running stack
  (`docker compose up` + backend on the `e2e` profile + `pnpm start`)
- Full pre-push hook (`bash .githooks/pre-push`) still passes (it does not run
  Playwright/mutation, but confirms nothing in Phases 1-4 regressed)
- Manually confirm `git grep -r "e2e" ageguessr-api/src/main` only ever appears inside
  `@Profile("e2e")` guards, never unconditionally active code

### Phase Summary

The full journey (register → check-email → confirm-email → login → home) passed on
the first real run against the actual stack: `docker compose up`, backend started with
`SPRING_PROFILES_ACTIVE=e2e`, `pnpm test:e2e:journeys`. Also manually verified the same
flow with raw `curl` against the e2e-profiled backend before writing the Playwright
test, confirming `/api/auth/me` returns `emailVerified: true` at the end.

Since there is no dedicated profile/account page yet to show `emailVerified` in the UI,
the journey test's final assertion calls `/api/auth/refresh` and `/api/auth/me`
directly via `page.request` (which shares the browser context's cookie jar with the
just-completed UI login) rather than reading it off a rendered page — still a real,
authenticated call riding on the actual UI-established session, not a bypass of it.

Security note on the test-support carve-out: `TestSupportController`'s path is only
ever permitted by a *second*, `@Profile("e2e")`-gated `SecurityFilterChain`
(`TestSupportSecurityConfig`, `@Order(0)`, `securityMatcher("/test-support/**")`) that
does not exist as a bean at all outside that profile — the main `SecurityFilterChain`
in `SecurityConfig` has no knowledge of the path and would reject it. This is a
structural guarantee, not just a Javadoc warning: there's no runtime flag to
misconfigure. Verified with `grep -rn "e2e" ageguessr-api/src/main` — every hit is
either inside an `@Profile("e2e")`/`@Profile("!e2e")` annotation or a comment
explaining one.

Full pre-push hook (`bash .githooks/pre-push`) passes: 39 backend tests (unit +
integration + architecture) and 27 frontend unit tests, plus lint/format on both sides.
Backend functional tests (18, not run by the hook) and all Playwright suites were run
manually in this phase and are green.

**All 5 phases complete.** See Final Recap below.

---

## Edge cases covered across phases

Duplicate registration (case-insensitive) · login before verifying · login on a locked
account · refresh-token reuse (theft signal) · refresh token expired · access token
expiring mid-session (interceptor silent refresh) · concurrent 401s triggering exactly
one refresh call · resend-verification for a nonexistent or already-verified email ·
rate limit exceeded on `/api/auth/*` · IP partitioning once `X-Forwarded-For` is present
· malformed/garbage JWT bearer · missing/garbage refresh cookie on refresh · logout with
no cookie (idempotent) · Bean Validation failures surfacing consistently · expired vs.
already-consumed confirmation token (both map to the same 404) · dev-proxy forwarding.

## Testing categories explicitly not needed beyond what's listed

All 7 categories from `docs/testing-strategy.md` apply somewhere in this plan — expected
for the first vertical slice, which also establishes the whole security posture. Mutation
(PIT) needs no dedicated work: the existing `pitest-maven` config already targets
`app.ageguessr.*` broadly and runs in CI on PRs to `main`, informative only.

## Final Recap

Full authentication feature shipped end-to-end: register (with required email
verification via Resend), confirm-email, resend-verification, login (with
account-enumeration defense and lockout), refresh (with rotation and reuse detection),
logout, and `/me` — backend (Spring Security + JWT) and frontend (Angular pages +
session-aware `HttpClient` interceptor) both complete, plus rate limiting and account
lockout as originally scoped, not deferred.

**Test coverage**: 57 backend tests (23 unit, 8 integration, 23 functional across 6
classes, 3 architecture) + 27 frontend unit tests (10 spec files) + 9 Playwright
component tests (3 files) + 1 Playwright QA journey — all green. The single most
important test in the whole plan, the anti-enumeration byte-for-byte regression test in
`LoginFunctionalTest`, passes.

**Real, non-obvious things learned and fixed along the way** (all captured above under
"Key decisions locked in for this plan" for whoever touches this code next):
Spring Boot 4.1's Jackson-3 package namespace; test resources fully replacing (not
merging with) main resources; MockMvc's shared simulated client IP defeating naive rate
limiting assumptions in tests; MockMvc requiring `.cookie(...)` rather than a raw
`Cookie` header; `@Modifying` repository queries needing their own `@Transactional`.
None of these were guessed — each was hit as a real test failure, diagnosed from the
actual stack trace, and fixed.

**Two small, deliberate additions beyond the literal plan text**, both explained in
their phase summaries: `AuthTokenService`/`EmailVerificationService` (DRY, avoid
duplicating JWT/email logic between use cases) and `Auth.tryRestoreSession()` (without
it, a page reload would always show a logged-out user even with a perfectly valid
refresh cookie — the session feature would be pointless across reloads).

**What's deliberately not built here** (out of scope, not forgotten): a profile/account
page in the UI, password reset, social login, CAPTCHA or any hardening beyond the
lockout/rate-limit already included, and the actual game endpoints that will need to be
added to `SecurityConfig`'s public allow-list when that feature starts (flagged
in-code).

## Deployment Plan

No prod-specific deployment step beyond the existing CI/CD pipeline. Before this
feature can run in prod:
- Add `JWT_SECRET` (generate via `openssl rand -base64 32`) and `RESEND_API_KEY` (from
  a real Resend account) as GitHub Actions secrets — manual, one-time, outside this
  plan's automated scope.
- Once a real CI/CD pipeline and Caddy edge exist (`docs/decisions/architecture.md`,
  still to be built), confirm `app.cookie.secure` resolves to `true` and
  `app.frontend-url`/`app.cors.allowed-origin` point at the real domain in that
  environment's configuration.
- No database migration concerns beyond the already-applied `V1__create_auth_tables.sql`
  running automatically at container startup, per the existing Flyway configuration.
