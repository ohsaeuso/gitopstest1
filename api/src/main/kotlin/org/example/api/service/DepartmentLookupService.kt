package org.example.api.service

import org.example.api.client.ExternalDepartmentClient
import org.example.api.domain.PendingDepartmentRequest
import org.example.api.repository.PendingDepartmentRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class DepartmentLookupService(
    private val client: ExternalDepartmentClient,
    private val pendingRequestRepository: PendingDepartmentRequestRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getDepartments(username: String): List<String> {
        val result = client.fetchDepartments(username)
        if (result.fromFallback) {
            queuePendingRequest(username)
        }
        return result.departments
    }

    fun getDepartmentsAsync(username: String): CompletableFuture<List<String>> =
        client.fetchDepartmentsAsync(username).thenApply { result ->
            if (result.fromFallback) {
                queuePendingRequest(username)
            }
            result.departments
        }

    private fun queuePendingRequest(username: String) {
        if (!pendingRequestRepository.existsByUsernameAndProcessedFalse(username)) {
            pendingRequestRepository.save(PendingDepartmentRequest(username = username))
            log.info("Queued pending department request for user={}", username)
        }
    }
}