package org.example.app.application.service

import org.example.app.application.port.`in`.AuditUserAccessUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserAccessAuditService : AuditUserAccessUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun audit(username: String) {
        log.info("User accessed: {}", username)
    }
}
