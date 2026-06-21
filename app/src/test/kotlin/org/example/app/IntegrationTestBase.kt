package org.example.app

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer


// https://docs.spring.io/spring-modulith/reference/testing.html
// https://docs.spring.io/spring-modulith/reference/events.html
// https://docs.spring.io/spring-framework/docs/4.2.x/spring-framework-reference/html/integration-testing.html

@SpringBootTest
@Testcontainers
/*@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)*/
abstract class IntegrationTestBase {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val oracle: OracleContainer = OracleContainer("gvenzl/oracle-free:23-slim-faststart")
    }
}
