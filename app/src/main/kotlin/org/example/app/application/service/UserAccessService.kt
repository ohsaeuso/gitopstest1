package org.example.app.application.service

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.example.app.application.port.`in`.RecordUserAccessUseCase
import org.example.app.domain.UserAccessedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class UserAccessService(
    private val eventPublisher: ApplicationEventPublisher,
) : RecordUserAccessUseCase {

    // 10초 당 최대 5회 허용. 초과 시 RequestNotPermitted → GlobalExceptionHandler가 429 반환
    @RateLimiter(name = "access")
    override fun recordAccess(username: String) {
        eventPublisher.publishEvent(UserAccessedEvent(username))
    }
}
