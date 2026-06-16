package org.example.app.service

import org.assertj.core.api.Assertions.assertThat
import org.example.app.event.UserAccessedEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario
import java.util.function.Consumer


@ApplicationModuleTest
class UserAccessedEventListenerModuleTest {

    @Autowired
    private val userAccessService: UserAccessService? = null

    @Autowired
    private val userAccessAuditService: UserAccessAuditService? = null

    // https://www.baeldung.com/spring-test-application-events

    @Test
    fun whenRecordAccess_thenUserAccessAuditServiceAudit(scenario: Scenario) {
        scenario.stimulate(Runnable { userAccessService?.recordAccess("customer-1") })
            .andWaitForEventOfType(UserAccessedEvent::class.java)
            .toArriveAndVerify(Consumer { evt: UserAccessedEvent? ->
                assertThat(evt)
                    .hasFieldOrPropertyWithValue("username", "customer-1")
                    .hasFieldOrProperty("timestamp")
            })
    }
}