package org.example.api.repository

import org.example.api.domain.PendingDepartmentRequest
import org.springframework.data.jpa.repository.JpaRepository

interface PendingDepartmentRequestRepository : JpaRepository<PendingDepartmentRequest, Long> {
    fun findByProcessedFalse(): List<PendingDepartmentRequest>
    fun existsByUsernameAndProcessedFalse(username: String): Boolean
}