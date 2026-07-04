package org.example.api.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

internal interface PendingDepartmentRequestJpaRepository : JpaRepository<PendingDepartmentRequestJpaEntity, Long> {
    fun findByProcessedFalse(): List<PendingDepartmentRequestJpaEntity>
    fun existsByUsernameAndProcessedFalse(username: String): Boolean
}
