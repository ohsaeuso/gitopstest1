package org.example.api.application.port.out

import org.example.api.domain.PendingDepartmentRequest

interface PendingDepartmentRequestPort {
    fun existsPendingFor(username: String): Boolean
    fun queue(username: String)
    fun findAllPending(): List<PendingDepartmentRequest>
    fun markProcessed(request: PendingDepartmentRequest)
}
