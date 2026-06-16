package org.example.app.event

import java.time.LocalDateTime

data class UserAccessedEvent(
    val username: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)