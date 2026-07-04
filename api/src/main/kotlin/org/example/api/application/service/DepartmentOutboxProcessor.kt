package org.example.api.application.service

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.example.api.application.port.`in`.ReprocessPendingDepartmentsUseCase
import org.example.api.application.port.out.DepartmentGateway
import org.example.api.application.port.out.PendingDepartmentRequestPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DepartmentOutboxProcessor(
    private val pendingRequestPort: PendingDepartmentRequestPort,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val gateway: DepartmentGateway
) : ReprocessPendingDepartmentsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun reprocess() {
        val cb = circuitBreakerRegistry.circuitBreaker("departments")
        if (cb.state != CircuitBreaker.State.CLOSED) return

        val pending = pendingRequestPort.findAllPending()
        if (pending.isEmpty()) return

        log.info("Processing {} pending department requests", pending.size)
        for (request in pending) {
            val result = gateway.fetchDepartments(request.username)
            if (result.fromFallback) {
                log.warn("Failed to resolve pending request for user={}, stopping batch", request.username)
                return
            }
            pendingRequestPort.markProcessed(request)
            log.info("Resolved pending request for user={}", request.username)
        }
    }
}
