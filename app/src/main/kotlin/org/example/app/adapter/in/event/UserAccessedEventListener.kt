package org.example.app.adapter.`in`.event

import org.example.app.application.port.`in`.AuditUserAccessUseCase
import org.example.app.domain.UserAccessedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class UserAccessedEventListener(
    private val auditUserAccessUseCase: AuditUserAccessUseCase,
) {

    @EventListener
    fun handle(event: UserAccessedEvent) {
        auditUserAccessUseCase.audit(event.username)
    }
}
