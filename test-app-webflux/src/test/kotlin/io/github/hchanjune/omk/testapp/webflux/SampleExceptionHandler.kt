package io.github.hchanjune.omk.testapp.webflux

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Mirrors a real-world @ControllerAdvice: it converts a domain exception into a normal response,
 * which is exactly the scenario where the original exception never reaches the OMK filter directly.
 */
@RestControllerAdvice
class SampleExceptionHandler {

    @ExceptionHandler(SampleClientException::class)
    fun handleClientError(ex: SampleClientException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body("handled: ${ex.message}")

    @ExceptionHandler(SampleServerException::class)
    fun handleServerError(ex: SampleServerException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("handled: ${ex.message}")

}
