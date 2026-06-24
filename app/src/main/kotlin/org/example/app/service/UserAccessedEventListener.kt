package org.example.app.service

import org.example.app.event.UserAccessedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class UserAccessedEventListener(
    private val userAccessAuditService: UserAccessAuditService,
) {

    @EventListener
    fun handle(event: UserAccessedEvent) {
        userAccessAuditService.audit(event.username)
    }
}