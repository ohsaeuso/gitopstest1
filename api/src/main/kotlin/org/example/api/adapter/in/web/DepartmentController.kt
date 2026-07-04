package org.example.api.adapter.`in`.web

import org.example.api.application.port.`in`.DepartmentLookupUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

@RestController
class DepartmentController(
    private val departmentLookupUseCase: DepartmentLookupUseCase,
) {
    @GetMapping("/users/{username}/departments")
    fun departments(
        @PathVariable username: String,
    ): List<String> = departmentLookupUseCase.getDepartments(username)

    @GetMapping("/users/{username}/departments/async")
    fun departmentsAsync(
        @PathVariable username: String,
    ): CompletableFuture<List<String>> = departmentLookupUseCase.getDepartmentsAsync(username)
}
