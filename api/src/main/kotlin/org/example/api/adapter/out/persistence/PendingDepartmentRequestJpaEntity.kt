package org.example.api.adapter.out.persistence

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pending_department_requests")
class PendingDepartmentRequestJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var username: String = ""

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var processed: Boolean = false

    constructor(username: String) : this() {
        this.username = username
    }

}
