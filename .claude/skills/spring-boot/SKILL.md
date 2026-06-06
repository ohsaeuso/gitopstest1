# Skill: Spring Boot 3.5 — project patterns

## Pinned versions
- Spring Boot **3.5.x** (do not migrate to 4.x without architectural approval).
- Java **21 LTS** with virtual threads enabled.
- `spring.threads.virtual.enabled=true` in `application.yml`.

## Stereotypes - which annotation to use
- `@RestController` → classes in `controller/`. Only HTTP mapping, no rules.
- `@Service` → classes in `service/`. Business rules. Transactional here.
- `@Repository` → interfaces extending `JpaRepository`. No manual implementation.
- `@Configuration` → classes in `config/`. Exposed beans.
- `@Component` → generic. **Avoid** if one of the above fits.

## Dependency injection
​```java
// ✅ CORRECT - constructor injection (final fields)
@Service
public class CartService {
    private final CartRepository cartRepository;
    private final ProductService productService;
    public CartService(CartRepository cartRepository, ProductService productService) {
        this.cartRepository = cartRepository;
        this.productService = productService;
    }
}
// ❌ WRONG - @Autowired on field
@Service
public class CartService {
    @Autowired private CartRepository cartRepository;  // don't do this
}
​```
Lombok `@RequiredArgsConstructor` is accepted to reduce boilerplate.
[continues with exception handling, validation, properties, actuator…]