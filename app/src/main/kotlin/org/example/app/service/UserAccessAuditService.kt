package org.example.app.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UserAccessAuditService {

    private val log = LoggerFactory.getLogger(javaClass)

    fun audit(username: String) {
        log.info("User accessed: {}", username)
    }
}