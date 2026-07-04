package org.example.app.application.port.`in`

import org.springframework.modulith.NamedInterface

// port.in is the intended entry point for driving adapters; expose it so Spring Modulith's
// module-boundary check doesn't treat this nested package as internal to `application`
@NamedInterface
interface RecordUserAccessUseCase {
    fun recordAccess(username: String)
}
