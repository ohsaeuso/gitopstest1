package org.example.app.client

import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.github.resilience4j.timelimiter.annotation.TimeLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class ExternalDepartmentClient {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Circuit Breaker: 실패율 50% 초과 시 OPEN → 10초 후 HALF_OPEN 전환
     * Retry: 최대 3회 재시도, 300ms 간격 (CallNotPermittedException 제외)
     * Bulkhead: 최대 5개 동시 호출 허용
     */
    @CircuitBreaker(name = "departments", fallbackMethod = "departmentsFallback")
    @Retry(name = "departments")
    @Bulkhead(name = "departments")
    fun fetchDepartments(username: String): List<String> {
        log.debug("Fetching departments for user: {}", username)
        simulateUnstableExternalCall()
        return listOf("Engineering", "HR", "Sales")
    }

    /**
     * TimeLimiter: 1초 초과 시 TimeoutException → Circuit Breaker가 slow call로 기록
     * CompletableFuture 반환 타입 필수
     */
    @CircuitBreaker(name = "departments", fallbackMethod = "departmentsAsyncFallback")
    @TimeLimiter(name = "departments")
    fun fetchDepartmentsAsync(username: String): CompletableFuture<List<String>> =
        CompletableFuture.supplyAsync {
            log.debug("Async fetching departments for user: {}", username)
            Thread.sleep(2000) // TimeLimiter(1s) 초과를 유도
            listOf("Engineering", "HR", "Sales")
        }

    private fun departmentsFallback(username: String, ex: Exception): List<String> {
        log.warn("Department service unavailable for user={}, cause={}", username, ex.javaClass.simpleName)
        return listOf("unknown")
    }

    private fun departmentsAsyncFallback(username: String, ex: Exception): CompletableFuture<List<String>> {
        log.warn("Async department service timed out for user={}", username)
        return CompletableFuture.completedFuture(listOf("unknown"))
    }

    private fun simulateUnstableExternalCall() {
        if (Math.random() < 0.4) {
            throw RuntimeException("External department service unavailable")
        }
    }
}