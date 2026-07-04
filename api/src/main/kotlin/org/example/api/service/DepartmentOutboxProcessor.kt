package org.example.api.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.example.api.client.ExternalDepartmentClient
import org.example.api.repository.PendingDepartmentRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["department.outbox.processor.enabled"], havingValue = "true", matchIfMissing = true)
class DepartmentOutboxProcessor(
    private val repository: PendingDepartmentRequestRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val client: ExternalDepartmentClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${department.outbox.processor.delay-ms:5000}")
    fun process() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")
        if (cb.state != CircuitBreaker.State.CLOSED) return

        val pending = repository.findByProcessedFalse()
        if (pending.isEmpty()) return

        log.info("Processing {} pending department requests", pending.size)
        for (request in pending) {
            val result = client.fetchDepartments(request.username)
            if (result.fromFallback) {
                log.warn("Failed to resolve pending request for user={}, stopping batch", request.username)
                return
            }
            request.processed = true
            repository.save(request)
            log.info("Resolved pending request for user={}", request.username)
        }
    }
}