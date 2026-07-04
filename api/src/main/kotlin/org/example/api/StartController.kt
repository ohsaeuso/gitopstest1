package org.example.api

import org.example.api.service.DepartmentLookupService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
class StartController(
    private val departmentLookupService: DepartmentLookupService,
) {
    @GetMapping("/users/{username}/departments")
    fun departments(
        @PathVariable username: String,
    ): List<String> = departmentLookupService.getDepartments(username)

    @GetMapping("/users/{username}/departments/async")
    fun departmentsAsync(
        @PathVariable username: String,
    ): CompletableFuture<List<String>> = departmentLookupService.getDepartmentsAsync(username)

}