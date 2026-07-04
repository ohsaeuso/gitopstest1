package org.example.api.application.port.`in`

import java.util.concurrent.CompletableFuture

interface DepartmentLookupUseCase {
    fun getDepartments(username: String): List<String>
    fun getDepartmentsAsync(username: String): CompletableFuture<List<String>>
}
