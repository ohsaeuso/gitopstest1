package org.example.api.adapter.out.client

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
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

    @Test
    fun fetchDepartments_givenCircuitBreakerOpen_thenFallbackReturned() {
        circuitBreakerRegistry.circuitBreaker("departments").transitionToOpenState()

        val result = client.fetchDepartments("user1")

        assertThat(result.departments).containsExactly("unknown")
        assertThat(result.fromFallback).isTrue()
    }

    @Test
    fun fetchDepartments_givenCircuitBreakerOpen_thenCallNotPermittedNotRetried() {
        circuitBreakerRegistry.circuitBreaker("departments").transitionToOpenState()
        val retryCount = AtomicInteger(0)
        retryRegistry.retry("departments").eventPublisher.onRetry { retryCount.incrementAndGet() }

        client.fetchDepartments("user1")

        assertThat(retryCount.get()).isEqualTo(0)
    }

    @Test
    fun fetchDepartments_givenCircuitBreakerHalfOpen_thenFailedProbesTransitionToOpen() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")
        val bulkhead = bulkheadRegistry.bulkhead("departments")

        // resilience4j only allows HALF_OPEN from OPEN/DISABLED/FORCED_OPEN, not directly from CLOSED
        cb.transitionToOpenState()
        cb.transitionToHalfOpenState()
        repeat(5) { bulkhead.acquirePermission() }

        try {
            repeat(3) { client.fetchDepartments("user1") }

            assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    @Test
    fun fetchDepartments_givenRuntimeException_thenRetriedUpToMaxAttempts() {
        val bulkhead = bulkheadRegistry.bulkhead("departments")
        repeat(5) { bulkhead.acquirePermission() }

        val retryCount = AtomicInteger(0)
        retryRegistry.retry("departments").eventPublisher.onRetry { retryCount.incrementAndGet() }

        try {
            client.fetchDepartments("user1")

            assertThat(retryCount.get()).isEqualTo(2)
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    @Test
    fun fetchDepartments_givenBulkheadFull_thenFallbackReturned() {
        val bulkhead = bulkheadRegistry.bulkhead("departments")
        repeat(5) { bulkhead.acquirePermission() }

        try {
            val result = client.fetchDepartments("user1")

            assertThat(result.departments).containsExactly("unknown")
            assertThat(result.fromFallback).isTrue()
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    @Test
    fun fetchDepartmentsAsync_givenCallExceedsTimeLimitOf1s_thenFallbackReturned() {
        val result = client.fetchDepartmentsAsync("user1").get(3, TimeUnit.SECONDS)

        assertThat(result.departments).containsExactly("unknown")
        assertThat(result.fromFallback).isTrue()
    }

    @Test
    fun fetchDepartmentsAsync_givenTimeLimiterApplied_thenCompletesBeforeInternalSleepDeadline() {
        val start = System.currentTimeMillis()
        client.fetchDepartmentsAsync("user1").get(3, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isLessThan(2000)
    }
}
