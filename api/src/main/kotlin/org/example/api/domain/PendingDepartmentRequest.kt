package org.example.api.domain

import java.time.Instant

data class PendingDepartmentRequest(
    val id: Long? = null,
    val username: String,
    val createdAt: Instant = Instant.now(),
    val processed: Boolean = false
)
