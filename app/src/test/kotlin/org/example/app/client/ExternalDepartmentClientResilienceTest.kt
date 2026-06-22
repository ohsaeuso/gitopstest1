package org.example.app.client

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
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

    @BeforeEach
    fun resetState() {
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
    fun fetchDepartments_givenCircuitBreakerHalfOpen_thenFailedProbesTransitionToOpen() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")
        val bulkhead = bulkheadRegistry.bulkhead("departments")

        cb.transitionToHalfOpenState()
        // Bulkhead를 소진해 프로브 호출이 모두 실패하도록 강제
        repeat(5) { bulkhead.acquirePermission() }

        try {
            // HALF_OPEN에서 허용된 3회 프로브 호출 모두 실패 → OPEN 전이
            repeat(3) { client.fetchDepartments("user1") }

            assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    // --- Retry ---

    @Test
    fun fetchDepartments_givenRuntimeException_thenRetriedUpToMaxAttempts() {
        val bulkhead = bulkheadRegistry.bulkhead("departments")
        // BulkheadFullException은 RuntimeException을 상속 → retry-exceptions에 해당해 재시도됨
        repeat(5) { bulkhead.acquirePermission() }

        val retryCount = AtomicInteger(0)
        retryRegistry.retry("departments").eventPublisher.onRetry { retryCount.incrementAndGet() }

        try {
            client.fetchDepartments("user1")

            // maxAttempts=3 → 초기 1회 + 재시도 2회 = retry 이벤트 2회
            assertThat(retryCount.get()).isEqualTo(2)
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    // --- Bulkhead ---

    @Test
    fun fetchDepartments_givenBulkheadFull_thenFallbackReturned() {
        val bulkhead = bulkheadRegistry.bulkhead("departments")
        repeat(5) { bulkhead.acquirePermission() }

        try {
            val result = client.fetchDepartments("user1")

            assertThat(result).containsExactly("unknown")
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    // --- TimeLimiter ---

    @Test
    fun fetchDepartmentsAsync_givenCallExceedsTimeLimitOf1s_thenFallbackReturned() {
        // 내부에서 2초 sleep → 1초 제한 초과 → departmentsAsyncFallback 호출
        val result = client.fetchDepartmentsAsync("user1").get(3, TimeUnit.SECONDS)

        assertThat(result).containsExactly("unknown")
    }

    @Test
    fun fetchDepartmentsAsync_givenTimeLimiterApplied_thenCompletesBeforeInternalSleepDeadline() {
        val start = System.currentTimeMillis()
        client.fetchDepartmentsAsync("user1").get(3, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - start

        // 내부 sleep은 2000ms지만 TimeLimiter가 1s에 차단 → 2초 미만에 완료돼야 함
        assertThat(elapsed).isLessThan(2000)
    }
}