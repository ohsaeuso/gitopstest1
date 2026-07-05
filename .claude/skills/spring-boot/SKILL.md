# Skill: Spring Boot 3.5 — project patterns

## Pinned versions
- Spring Boot **3.5.x** (do not migrate to 4.x without architectural approval).
- Kotlin **2.2.0** with virtual threads enabled.
- `spring.threads.virtual.enabled=true` in `application.yml`.

## Stereotypes - which annotation to use (hexagonal layout)
- `@RestController` → classes in `adapter/in/web/`. Implements/calls a `port.in` use case interface, no business rules. Only HTTP mapping.
- `@Service` → classes in `application/service/`. Implements a `port.in` use case interface, depends only on `port.out` interfaces + `domain` types. Business rules and `@Transactional` live here.
- `@Repository`/JPA → interfaces extending `JpaRepository`, plus the `@Entity` class and a persistence adapter implementing a `port.out` interface — all confined to `adapter/out/persistence/`. Never let the JPA entity or the Spring Data repository leak outside that package; `application` only ever sees the `port.out` interface.
- `@Configuration` → classes in `config/`. Exposed beans, cross-cutting Spring wiring, not part of the hexagon.
- `@Component` → generic. Used for other driving/driven adapters that don't fit the above (`adapter/in/scheduler`, `adapter/in/event`, `adapter/out/client`). **Avoid** elsewhere if one of the above fits.

## Dependency injection
- ✅ CORRECT - constructor injection (final fields)
- ❌ WRONG - @Autowired on field

## Stacking resilience4j annotations (@CircuitBreaker/@Retry/@Bulkhead/@RateLimiter/@TimeLimiter)
- Annotation order in the code (top-to-bottom) has **no effect** on execution order. Each one is a
  separate Spring AOP aspect with its own fixed `@Order`; combined they execute (outer→inner):
  `Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead`.
- Put `fallbackMethod` on whichever annotation is **outermost** among the ones you're combining.
  If `@Retry` + `@CircuitBreaker` are both present, `fallbackMethod` must go on `@Retry` — putting
  it on `@CircuitBreaker` (inner) makes it swallow every exception (bulkhead rejections included)
  on the very first attempt, so `@Retry` never sees a failure and silently never retries.
- To verify the actual order yourself rather than assume: decompile the relevant
  `resilience4j-spring6-*-sources.jar` from the Gradle cache and check `getXxxAspectOrder()` in
  `io.github.resilience4j.spring6.<name>.configure.<Name>ConfigurationProperties`, or add a
  temporary debug log/println and observe which exception reaches which fallback.
- `@Bulkhead`/`@RateLimiter` etc. also require `spring-boot-starter-aop` on the module's classpath
  (brings `aspectjweaver`) — without it, Spring's AOP auto-proxying never activates and the
  annotations become silent no-ops (see `org.example.app.application.service.UserAccessService`
  history: rate limiting didn't actually work until this dependency was added).

[continues with exception handling, validation, properties, actuator…]