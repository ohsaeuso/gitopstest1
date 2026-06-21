package org.example.app.service

import org.example.app.event.UserAccessedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAccessService(
    private val eventPublisher: ApplicationEventPublisher,
) {
    //@Transactional
    fun recordAccess(username: String) {
        eventPublisher.publishEvent(UserAccessedEvent(username))
    }
}