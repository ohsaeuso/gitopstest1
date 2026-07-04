package org.example.api.adapter.`in`.scheduler

import org.example.api.application.port.`in`.ReprocessPendingDepartmentsUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["department.outbox.processor.enabled"], havingValue = "true", matchIfMissing = true)
class DepartmentOutboxScheduler(
    private val reprocessPendingDepartmentsUseCase: ReprocessPendingDepartmentsUseCase
) {

    @Scheduled(fixedDelayString = "\${department.outbox.processor.delay-ms:5000}")
    fun run() {
        reprocessPendingDepartmentsUseCase.reprocess()
    }
}
