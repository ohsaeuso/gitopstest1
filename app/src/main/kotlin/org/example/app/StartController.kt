package org.example.app


import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import kotlin.math.roundToInt


@RestController
class StartController(
    private val  clientService : OAuth2AuthorizedClientService
) {
    @GetMapping("/users/{username}")
    fun username(
        @PathVariable("username") username: String,
    ): String = "Hello, ${Math.random().roundToInt()}-$username!"

    @GetMapping("/hello")
    fun hello(
    ): String = "Hello"

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