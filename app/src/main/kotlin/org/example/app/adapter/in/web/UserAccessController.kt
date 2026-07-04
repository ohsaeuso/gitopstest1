package org.example.app.adapter.`in`.web

import org.example.app.application.port.`in`.RecordUserAccessUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class UserAccessController(
    private val recordUserAccessUseCase: RecordUserAccessUseCase,
) {

    @GetMapping("/users/{username}/access")
    fun recordAccess(
        @PathVariable username: String,
    ): String {
        recordUserAccessUseCase.recordAccess(username)
        return "access event published for $username"
    }

    @GetMapping("/users/{username}/groups")
    fun usernames(
        @PathVariable username: String,
    ) = listOf("123", "456")
}
