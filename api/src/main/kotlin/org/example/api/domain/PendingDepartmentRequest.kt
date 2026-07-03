package org.example.api.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pending_department_requests")
class PendingDepartmentRequest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val username: String,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var processed: Boolean = false
)