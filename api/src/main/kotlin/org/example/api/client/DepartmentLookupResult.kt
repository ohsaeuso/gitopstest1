package org.example.api.client

data class DepartmentLookupResult(
    val departments: List<String>,
    val fromFallback: Boolean
)