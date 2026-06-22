package org.example.app.service

import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("local")
class UserAccessServiceResilienceTest {

    @Autowired
    private lateinit var userAccessService: UserAccessService

    @Autowired
    private lateinit var rateLimiterRegistry: RateLimiterRegistry

    @BeforeEach
    fun drainAllPermits() {
        // 남은 허용량을 모두 소진하여 각 테스트를 동일한 상태에서 시작
        val rl = rateLimiterRegistry.rateLimiter("access")
        val available = rl.metrics.availablePermissions.coerceAtLeast(0)
        repeat(available) { rl.acquirePermission() }
    }

    // --- Rate Limiter ---

    @Test
    fun recordAccess_givenPermitsExhausted_thenRequestNotPermitted() {
        // @BeforeEach에서 허용량 소진 완료 → 다음 호출은 즉시 거부
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