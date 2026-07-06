package org.example.api.adapter.out.client

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
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
        // Bulkhead 소진으로 HALF_OPEN 프로브 3회를 결정론적으로 실패시킴 (docs/resilience4j.md 참고)
        repeat(5) { bulkhead.acquirePermission() }

        try {
            repeat(3) { client.fetchDepartments("user1") }

            assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
        } finally {
            repeat(5) { bulkhead.onComplete() }
        }
    }

    @Test
    fun fetchDepartments_givenCircuitBreakerHalfOpen_thenSuccessfulProbesTransitionToClosed() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")

        cb.transitionToOpenState()
        cb.transitionToHalfOpenState()
        // 실제 호출 대신 CB API로 성공 결과를 직접 주입 (40% 랜덤 실패에 기대지 않고 permittedNumberOfCallsInHalfOpenState=3 소진을 결정론적으로 재현)
        repeat(3) { cb.onSuccess(0, TimeUnit.MILLISECONDS) }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun fetchDepartments_givenFailureRateExceedsThreshold_thenCircuitBreakerOpensFromClosed() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")

        // sliding-window-size(10)만큼 실패를 CB API로 직접 주입해 failure-rate-threshold(50%) 초과 시
        // 수동 전환 없이도 CLOSED에서 스스로 OPEN 되는지 검증
        repeat(10) { cb.onError(0, TimeUnit.MILLISECONDS, RuntimeException("simulated failure")) }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
    }

    @Test
    fun fetchDepartments_givenSlowCallRateExceedsThreshold_thenCircuitBreakerOpensFromClosed() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")

        // slow-call-duration-threshold(2s)를 초과하는 소요시간으로 성공 결과를 주입해
        // slow-call-rate-threshold(50%) 초과만으로도 OPEN 전이되는지 검증 (실패 없이 느린 호출만으로)
        repeat(10) { cb.onSuccess(3, TimeUnit.SECONDS) }

        assertThat(cb.state).isEqualTo(CircuitBreaker.State.OPEN)
    }

    @Test
    fun departments_circuitBreakerConfig_matchesYaml() {
        val config = circuitBreakerRegistry.circuitBreaker("departments").circuitBreakerConfig

        assertThat(config.slidingWindowType).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        assertThat(config.slidingWindowSize).isEqualTo(10)
        assertThat(config.failureRateThreshold).isEqualTo(50.0f)
        assertThat(config.slowCallRateThreshold).isEqualTo(50.0f)
        assertThat(config.slowCallDurationThreshold).isEqualTo(Duration.ofSeconds(2))
        assertThat(config.permittedNumberOfCallsInHalfOpenState).isEqualTo(3)
    }

    @Test
    fun fetchDepartments_givenRuntimeException_thenRetriedUpToMaxAttempts() {
        // BulkheadFullException(RuntimeException 하위타입)으로 40% 랜덤 실패를 결정론적으로 대체 (docs/resilience4j.md 참고)
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
        // max-concurrent-calls(5)을 직접 소진시켜 다음 호출이 확실히 거부되게 함 (docs/resilience4j.md 참고)
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
