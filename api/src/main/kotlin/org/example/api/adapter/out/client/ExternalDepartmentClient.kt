package org.example.api.adapter.out.client

import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.github.resilience4j.timelimiter.annotation.TimeLimiter
import org.example.api.application.port.out.DepartmentGateway
import org.example.api.domain.DepartmentLookupResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class ExternalDepartmentClient : DepartmentGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    // fallbackMethod must live on @Retry, not @CircuitBreaker: Spring's fixed aspect order
    // puts @Retry outermost, so a fallback on @CircuitBreaker would catch every exception
    // (including bulkhead rejections) on the first attempt and retries would never fire.
    @CircuitBreaker(name = "departments")
    @Retry(name = "departments", fallbackMethod = "departmentsFallback")
    @Bulkhead(name = "departments")
    override fun fetchDepartments(username: String): DepartmentLookupResult {
        log.debug("Fetching departments for user: {}", username)
        simulateUnstableExternalCall()
        return DepartmentLookupResult(listOf("Engineering", "HR", "Sales"), fromFallback = false)
    }

    @CircuitBreaker(name = "departments", fallbackMethod = "departmentsAsyncFallback")
    @TimeLimiter(name = "departments")
    override fun fetchDepartmentsAsync(username: String): CompletableFuture<DepartmentLookupResult> =
        CompletableFuture.supplyAsync {
            log.debug("Async fetching departments for user: {}", username)
            Thread.sleep(2000)
            DepartmentLookupResult(listOf("Engineering", "HR", "Sales"), fromFallback = false)
        }

    private fun departmentsFallback(username: String, ex: Exception): DepartmentLookupResult {
        log.warn("Department service unavailable for user={}, cause={}", username, ex.javaClass.simpleName)
        return DepartmentLookupResult(listOf("unknown"), fromFallback = true)
    }

    private fun departmentsAsyncFallback(username: String, ex: Exception): CompletableFuture<DepartmentLookupResult> {
        log.warn("Async department service timed out for user={}", username)
        return CompletableFuture.completedFuture(DepartmentLookupResult(listOf("unknown"), fromFallback = true))
    }

    private fun simulateUnstableExternalCall() {
        if (Math.random() < 0.4) {
            throw RuntimeException("External department service unavailable")
        }
    }
}
