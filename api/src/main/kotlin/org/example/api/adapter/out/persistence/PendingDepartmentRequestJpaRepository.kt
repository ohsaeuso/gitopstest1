package org.example.api.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PendingDepartmentRequestJpaRepository : JpaRepository<PendingDepartmentRequestJpaEntity, Long> {
    fun findByProcessedFalse(): List<PendingDepartmentRequestJpaEntity>
    fun existsByUsernameAndProcessedFalse(username: String): Boolean
}
