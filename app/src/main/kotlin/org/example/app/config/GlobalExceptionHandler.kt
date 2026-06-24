package org.example.app.config

import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CallNotPermittedException::class)
    fun handleCircuitBreakerOpen(ex: CallNotPermittedException): ResponseEntity<ProblemDetail> {
        log.warn("Circuit breaker OPEN: {}", ex.message)
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service temporarily unavailable. Please try again later."
        )
        problem.title = "Circuit Breaker Open"
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem)
    }

    @ExceptionHandler(RequestNotPermitted::class)
    fun handleRateLimitExceeded(ex: RequestNotPermitted): ResponseEntity<ProblemDetail> {
        log.warn("Rate limit exceeded: {}", ex.message)
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded. Please try again in a few seconds."
        )
        problem.title = "Rate Limit Exceeded"
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem)
    }

    @ExceptionHandler(BulkheadFullException::class)
    fun handleBulkheadFull(ex: BulkheadFullException): ResponseEntity<ProblemDetail> {
        log.warn("Bulkhead full: {}", ex.message)
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Too many concurrent requests. Please try again later."
        )
        problem.title = "Service Overloaded"
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem)
    }
}