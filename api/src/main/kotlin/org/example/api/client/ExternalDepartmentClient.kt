package org.example.api.client

import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.github.resilience4j.timelimiter.annotation.TimeLimiter
import org.example.api.domain.PendingDepartmentRequest
import org.example.api.repository.PendingDepartmentRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class ExternalDepartmentClient(
    private val pendingRequestRepository: PendingDepartmentRequestRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @CircuitBreaker(name = "departments", fallbackMethod = "departmentsFallback")
    @Retry(name = "departments")
    @Bulkhead(name = "departments")
    fun fetchDepartments(username: String): List<String> {
        log.debug("Fetching departments for user: {}", username)
        simulateUnstableExternalCall()
        return listOf("Engineering", "HR", "Sales")
    }

    @CircuitBreaker(name = "departments", fallbackMethod = "departmentsAsyncFallback")
    @TimeLimiter(name = "departments")
    fun fetchDepartmentsAsync(username: String): CompletableFuture<List<String>> =
        CompletableFuture.supplyAsync {
            log.debug("Async fetching departments for user: {}", username)
            Thread.sleep(2000)
            listOf("Engineering", "HR", "Sales")
        }

    private fun departmentsFallback(username: String, ex: Exception): List<String> {
        log.warn("Department service unavailable for user={}, cause={}", username, ex.javaClass.simpleName)
        if (!pendingRequestRepository.existsByUsernameAndProcessedFalse(username)) {
            pendingRequestRepository.save(PendingDepartmentRequest(username = username))
            log.info("Queued pending department request for user={}", username)
        }
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