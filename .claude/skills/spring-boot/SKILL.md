# Skill: Spring Boot 3.5 — project patterns

## Pinned versions
- Spring Boot **3.5.x** (do not migrate to 4.x without architectural approval).
- Kotlin **2.2.0** with virtual threads enabled.
- `spring.threads.virtual.enabled=true` in `application.yml`.

## Stereotypes - which annotation to use
- `@RestController` → classes in `controller/`. Only HTTP mapping, no rules.
- `@Service` → classes in `service/`. Business rules. Transactional here.
- `@Repository` → interfaces extending `JpaRepository`. No manual implementation.
- `@Configuration` → classes in `config/`. Exposed beans.
- `@Component` → generic. **Avoid** if one of the above fits.

## Dependency injection
- ✅ CORRECT - constructor injection (final fields)
- ❌ WRONG - @Autowired on field

[continues with exception handling, validation, properties, actuator…]