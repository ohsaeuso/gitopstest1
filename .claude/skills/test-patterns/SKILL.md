## Integration test structure (Testcontainers)

Use an abstract base class to share the container:
```kotlin
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
abstract class IntegrationTestBase {
    companion object {
        @Container
        @ServiceConnection  // Spring Boot 3.1+ wires the connection automatically
        @JvmStatic
        val oracle: OracleContainer = OracleContainer("gvenzl/oracle-free:23-slim-faststart")
    }
}
``` 
And extend it in tests:
```kotlin
class CheckoutFlowIT : IntegrationTestBase() {
    @Autowired lateinit var cartService: CartService
    @Autowired lateinit var checkoutService: CheckoutService

    @Test
    fun checkout_givenValidCart_thenOrderPending() {
        val cart = cartService.create()
        cartService.addItem(cart.id, productId, 2)
        val order = checkoutService.checkout(cart.id)
        assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT)
        assertThat(order.total).isEqualByComparingTo(BigDecimal("7000.00"))
    }
}
```
**Suffix `IT`** for integration tests (run on `verify`, not `test`).
**Suffix `Test`** for unit tests (run on `test`).