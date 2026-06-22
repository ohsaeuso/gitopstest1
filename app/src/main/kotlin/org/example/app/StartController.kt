package org.example.app

import org.example.app.client.ExternalDepartmentClient
import org.example.app.service.UserAccessService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture


@RestController
class StartController(
    private val clientService: OAuth2AuthorizedClientService,
    private val userAccessService: UserAccessService,
    private val externalDepartmentClient: ExternalDepartmentClient,
) {
    // Circuit Breaker + Retry + Bulkhead 적용 (실패 시 fallback ["unknown"] 반환)
    @GetMapping("/users/{username}/departments")
    fun departments(
        @PathVariable username: String,
    ): List<String> = externalDepartmentClient.fetchDepartments(username)

    // TimeLimiter 적용 (1초 초과 시 fallback 반환)
    @GetMapping("/users/{username}/departments/async")
    fun departmentsAsync(
        @PathVariable username: String,
    ): CompletableFuture<List<String>> = externalDepartmentClient.fetchDepartmentsAsync(username)

    // Rate Limiter 적용 (10초 당 5회 초과 시 429 반환)
    @GetMapping("/users/{username}/access")
    fun recordAccess(
        @PathVariable username: String,
    ): String {
        userAccessService.recordAccess(username)
        return "access event published for $username"
    }

    @GetMapping("/users/{username}/groups")
    fun usernames(
        @PathVariable username: String,
    ) = listOf("123", "456")

    @GetMapping("/hello")
    fun hello(
    ): String = "Hello"

    @GetMapping("/login/oauth2/code/keycloak")
    fun redirect(
    ): String = "redirected"

    @GetMapping("/home")
    fun getToken(
        @AuthenticationPrincipal principal: OAuth2User?,
        authentication: OAuth2AuthenticationToken,
    ): String? {
        val client = clientService.loadAuthorizedClient<OAuth2AuthorizedClient?>(
            authentication.authorizedClientRegistrationId,
            authentication.name
        )
        return client?.accessToken?.tokenValue
    }

}