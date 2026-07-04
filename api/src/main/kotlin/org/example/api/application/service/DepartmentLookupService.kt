package org.example.api.application.service

import org.example.api.application.port.`in`.DepartmentLookupUseCase
import org.example.api.application.port.out.DepartmentGateway
import org.example.api.application.port.out.PendingDepartmentRequestPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class DepartmentLookupService(
    private val gateway: DepartmentGateway,
    private val pendingRequestPort: PendingDepartmentRequestPort
) : DepartmentLookupUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getDepartments(username: String): List<String> {
        val result = gateway.fetchDepartments(username)
        if (result.fromFallback) {
            queuePendingRequest(username)
        }
        return result.departments
    }

    override fun getDepartmentsAsync(username: String): CompletableFuture<List<String>> =
        gateway.fetchDepartmentsAsync(username).thenApply { result ->
            if (result.fromFallback) {
                queuePendingRequest(username)
            }
            result.departments
        }

    private fun queuePendingRequest(username: String) {
        if (!pendingRequestPort.existsPendingFor(username)) {
            pendingRequestPort.queue(username)
            log.info("Queued pending department request for user={}", username)
        }
    }
}
