package org.example.app.service

import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserAccessServiceResilienceTest {

    @Autowired
    private lateinit var userAccessService: UserAccessService

    @Autowired
    private lateinit var rateLimiterRegistry: RateLimiterRegistry

    @BeforeEach
    fun drainAllPermits() {
        val rl = rateLimiterRegistry.rateLimiter("access")
        val available = rl.metrics.availablePermissions.coerceAtLeast(0)
        repeat(available) { rl.acquirePermission() }
    }

    @Test
    fun recordAccess_givenPermitsExhausted_thenRequestNotPermitted() {
        assertThrows<RequestNotPermitted> {
            userAccessService.recordAccess("user1")
        }
    }

    @Test
    fun access_rateLimiterConfig_matchesYaml() {
        val config = rateLimiterRegistry.rateLimiter("access").rateLimiterConfig

        assertThat(config.limitForPeriod).isEqualTo(5)
        assertThat(config.limitRefreshPeriod).isEqualTo(Duration.ofSeconds(10))
        assertThat(config.timeoutDuration).isEqualTo(Duration.ZERO)
    }
}