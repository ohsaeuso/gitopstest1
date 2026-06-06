# CLAUDE.md — ecommerce-api

## About this project
REST API in Kotlin 2.2.0 (Java 21) + Spring Boot 3.5. Main domains:
Integrates with Oracle 23.

## Mandatory stack
- **Language:** Kotlin 2.2.0.
- **Framework:** Spring Boot 3.5.x, Spring Data JPA, Spring Validation, Spring Security 6.
- **Database:** Oracle 23 in production, Testcontainers for integration tests.
- **Build:** Gradle 9.+ (`./mvnw`). NEVER edit pom.xml without justifying the dependency.
- **Migrations:** Flyway. Files in `src/main/resources/db/migration/V{n}__{name}.sql`.

## Code conventions
- Root package: `com.example.task`.
- Layers: `controller` (REST) → `service` (rules) → `repository` (JPA).
- REST endpoints follow `/api/v1/{resource}`. Version in URL, not header.
- Errors: throw domain exceptions extending `DomainException` in `domain.exception`.
  A `@RestControllerAdvice` in `config.GlobalExceptionHandler` converts to RFC 7807.
- Structured logs via SLF4J + Logback JSON. **Never** `System.out.println`.
- Don't use `@Autowired` on fields. Always constructor injection.

## Test conventions
- Minimum coverage: 80% lines in `service/`.
- Integration: `@SpringBootTest` + `@Testcontainers` with real Oracle.
- Don't use `@MockBean` (deprecated in 3.4+). Use `@MockitoBean`.
- Names: `method_givenCondition_thenResult`.

## Git conventions
- Branches: `feat/ECOM-{n}-{slug}`, `fix/ECOM-{n}-{slug}`, `chore/{slug}`.
- Commits: Conventional Commits.
- PRs: title format `[ECOM-{n}] {description}`. Always reference the issue.

## Feature workflow
When the user says "implement ECOM-X" or similar:
1. Use `/jira-pull ECOM-X` to pull issue context.
2. Move the issue to "In Progress" via the Atlassian MCP.
3. Create the branch following the convention above.
4. Consult the appropriate skill (jpa-domain, api-rest, etc.) before coding.
5. Write tests BEFORE implementation when feasible (TDD for rules).
6. Run `./gradlew verify` locally. Do not open a PR with red tests.
7. Use `/pr-open` to open the PR - it fills the template and links the issue.
## Agent limits
- **NEVER** run commands against production. Valid envs: `local`, `dev`, `staging`.
- **NEVER** `git push --force` on shared branches (`main`, `develop`).
- **NEVER** commit credentials. Verify with `git diff --staged` before committing.
- **NEVER** approve your own PR. Always wait for human reviewer.
- If a skill doesn't exist for what you're doing, **stop** and ask for guidance.
## Quick reference files
- `.claude/skills/spring-boot/SKILL.md` - Spring Boot patterns for this project.
- `.claude/skills/jpa-domain/SKILL.md` - domain modeling and JPA.
- `.claude/skills/test-patterns/SKILL.md` - JUnit 5 + Mockito + Testcontainers.
- `.claude/skills/observability/SKILL.md` - logs, metrics, tracing.
- `docs/architecture.md` - overall architectural view.