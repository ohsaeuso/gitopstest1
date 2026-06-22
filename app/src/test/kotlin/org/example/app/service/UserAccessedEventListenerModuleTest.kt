package org.example.app.service

import org.assertj.core.api.Assertions.assertThat
import org.example.app.IntegrationTestBase
import org.example.app.event.UserAccessedEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario


@ApplicationModuleTest
class UserAccessedEventListenerModuleTest : IntegrationTestBase() {

    @Autowired
    private val userAccessService: UserAccessService? = null

    @Autowired
    private val userAccessAuditService: UserAccessAuditService? = null

    @Test
    fun whenRecordAccess_thenUserAccessedEventPublished(scenario: Scenario) {
        scenario.stimulate( { userAccessService?.recordAccess("customer-1") })
            .andWaitForEventOfType(UserAccessedEvent::class.java)
            .toArriveAndVerify( { evt: UserAccessedEvent? ->
                assertThat(evt)
                    .hasFieldOrPropertyWithValue("username", "customer-1")
                    .hasFieldOrProperty("timestamp")
            })
    }

    @Test
    fun whenReceivingPublishOrderCompletedEvent_thenRewardCustomerWithLoyaltyPoints(scenario: Scenario) {
        scenario.publish(UserAccessedEvent("customer-1" ))
            .andWaitForStateChange{
                userAccessAuditService?.audit("customer-1")
            }
    }

    @Test
    fun whenRecordAccess_thenUserAccessAuditServiceAudit(scenario: Scenario) {
        scenario.stimulate( { userAccessService?.recordAccess("customer-1") })
            .andWaitForStateChange { userAccessAuditService?.audit("customer-1") }

    }

}