package org.example.app.service

import org.assertj.core.api.Assertions.assertThat
import org.example.app.event.UserAccessedEvent
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.PublishedEvents

@ApplicationModuleTest
class UserAccessedEventListenerModuleTest {

    @Test
    fun handle_givenUserAccessedEvent_thenAuditServiceLogsAccess(
        publisher: ApplicationEventPublisher,
        events: PublishedEvents,
    ) {
        publisher.publishEvent(UserAccessedEvent("alice"))

        assertThat(events.ofType(UserAccessedEvent::class.java)).hasSize(1)
    }
}