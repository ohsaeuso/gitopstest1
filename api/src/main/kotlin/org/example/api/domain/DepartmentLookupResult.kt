package org.example.api.domain

data class DepartmentLookupResult(
    val departments: List<String>,
    val fromFallback: Boolean
)
