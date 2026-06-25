package org.example.app.service

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.example.app.event.UserAccessedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class UserAccessService(
    private val eventPublisher: ApplicationEventPublisher,
) {

    // 10초 당 최대 5회 허용. 초과 시 RequestNotPermitted → GlobalExceptionHandler가 429 반환
    @RateLimiter(name = "access")
    fun recordAccess(username: String) {
        eventPublisher.publishEvent(UserAccessedEvent(username))
    }
}