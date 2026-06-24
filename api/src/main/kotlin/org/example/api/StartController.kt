package org.example.api

import org.example.api.client.ExternalDepartmentClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
class StartController(
    private val externalDepartmentClient: ExternalDepartmentClient,
) {
    @GetMapping("/users/{username}/departments")
    fun departments(
        @PathVariable username: String,
    ): List<String> = externalDepartmentClient.fetchDepartments(username)

    @GetMapping("/users/{username}/departments/async")
    fun departmentsAsync(
        @PathVariable username: String,
    ): CompletableFuture<List<String>> = externalDepartmentClient.fetchDepartmentsAsync(username)

}