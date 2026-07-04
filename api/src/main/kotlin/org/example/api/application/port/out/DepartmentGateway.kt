package org.example.api.application.port.out

import org.example.api.domain.DepartmentLookupResult
import java.util.concurrent.CompletableFuture

interface DepartmentGateway {
    fun fetchDepartments(username: String): DepartmentLookupResult
    fun fetchDepartmentsAsync(username: String): CompletableFuture<DepartmentLookupResult>
}
