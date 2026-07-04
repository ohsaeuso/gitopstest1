package org.example.api.adapter.out.persistence

import org.example.api.application.port.out.PendingDepartmentRequestPort
import org.example.api.domain.PendingDepartmentRequest
import org.springframework.stereotype.Component

@Component
class PendingDepartmentRequestPersistenceAdapter(
    private val jpaRepository: PendingDepartmentRequestJpaRepository
) : PendingDepartmentRequestPort {

    override fun existsPendingFor(username: String): Boolean =
        jpaRepository.existsByUsernameAndProcessedFalse(username)

    override fun queue(username: String) {
        jpaRepository.save(PendingDepartmentRequestJpaEntity(username = username))
    }

    override fun findAllPending(): List<PendingDepartmentRequest> =
        jpaRepository.findByProcessedFalse().map { it.toDomain() }

    override fun markProcessed(request: PendingDepartmentRequest) {
        val id = requireNotNull(request.id) { "Cannot mark an unpersisted PendingDepartmentRequest as processed" }
        val entity = jpaRepository.findById(id)
            .orElseThrow { IllegalStateException("PendingDepartmentRequest $id no longer exists") }
        entity.processed = true
        jpaRepository.save(entity)
    }

    private fun PendingDepartmentRequestJpaEntity.toDomain() = PendingDepartmentRequest(
        id = this.id,
        username = this.username,
        createdAt = this.createdAt,
        processed = this.processed
    )
}
