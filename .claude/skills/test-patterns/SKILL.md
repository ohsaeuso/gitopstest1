## Integration test structure (Testcontainers)

Use an abstract base class to share the container:
​```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
abstract class IntegrationTestBase {
    @Container
    @ServiceConnection  // Spring Boot 3.1+ wires the connection automatically
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
​```
And extend it in tests:
​```java
class CheckoutFlowIT extends IntegrationTestBase {
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Test
    void fullFlow_fromCartToOrder() {
        var cart = cartService.create();
        cartService.addItem(cart.getId(), productId, 2);
        var order = checkoutService.checkout(cart.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("7000.00"));
    }
}
​```
**Suffix `IT`** for integration tests (run on `verify`, not `test`).
**Suffix `Test`** for unit tests (run on `test`).