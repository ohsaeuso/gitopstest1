package org.example.app.client

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class ExternalDepartmentClientResilienceTest {

    @Autowired
    private lateinit var client: ExternalDepartmentClient

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var retryRegistry: RetryRegistry

    @Autowired
    private lateinit var bulkheadRegistry: BulkheadRegistry

    @Autowired
    private lateinit var timeLimiterRegistry: TimeLimiterRegistry

    @BeforeEach
    fun resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("departments").transitionToClosedState()
    }

    // --- Circuit Breaker ---

    @Test
    fun fetchDepartments_givenCircuitBreakerOpen_thenFallbackReturned() {
        circuitBreakerRegistry.circuitBreaker("departments").transitionToOpenState()

        val result = client.fetchDepartments("user1")

        assertThat(result).containsExactly("unknown")
    }

    @Test
    fun fetchDepartments_givenCircuitBreakerOpen_thenCallNotPermittedNotRetried() {
        circuitBreakerRegistry.circuitBreaker("departments").transitionToOpenState()
        val retryCount = AtomicInteger(0)
        retryRegistry.retry("departments").eventPublisher.onRetry { retryCount.incrementAndGet() }

        client.fetchDepartments("user1")

        // CallNotPermittedException은 ignore-exceptions에 포함되어 재시도하지 않음
        assertThat(retryCount.get()).isEqualTo(0)
    }

    @Test
    fun departments_circuitBreakerConfig_matchesYaml() {
        val config = circuitBreakerRegistry.circuitBreaker("departments").circuitBreakerConfig

        assertThat(config.slidingWindowSize).isEqualTo(10)
        assertThat(config.failureRateThreshold).isEqualTo(50f)
        assertThat(config.slowCallRateThreshold).isEqualTo(50f)
        assertThat(config.slowCallDurationThreshold).isEqualTo(Duration.ofSeconds(2))
        assertThat(config.permittedNumberOfCallsInHalfOpenState).isEqualTo(3)
    }

    // --- Retry ---

    @Test
    fun departments_retryConfig_matchesYaml() {
        val config = retryRegistry.retry("departments").retryConfig

        assertThat(config.maxAttempts).isEqualTo(3)
    }

    // --- Bulkhead ---

    @Test
    fun departments_bulkheadConfig_matchesYaml() {
        val config = bulkheadRegistry.bulkhead("departments").bulkheadConfig

        assertThat(config.maxConcurrentCalls).isEqualTo(5)
        assertThat(config.maxWaitDuration).isEqualTo(Duration.ofMillis(100))
    }

    // --- TimeLimiter ---

    @Test
    fun fetchDepartmentsAsync_givenCallExceedsTimeLimitOf1s_thenFallbackReturned() {
        // fetchDepartmentsAsync 내부에서 2초 sleep → 1초 제한 초과 → departmentsAsyncFallback 호출
        val result = client.fetchDepartmentsAsync("user1").get(3, TimeUnit.SECONDS)

        assertThat(result).containsExactly("unknown")
    }

    @Test
    fun departments_timeLimiterConfig_matchesYaml() {
        val config = timeLimiterRegistry.timeLimiter("departments").timeLimiterConfig

        assertThat(config.timeoutDuration).isEqualTo(Duration.ofSeconds(1))
        assertThat(config.shouldCancelRunningFuture()).isTrue()
    }
}