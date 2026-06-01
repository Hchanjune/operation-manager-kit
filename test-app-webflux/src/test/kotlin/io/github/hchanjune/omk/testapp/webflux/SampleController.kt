package io.github.hchanjune.omk.testapp.webflux

import io.github.hchanjune.omk.core.annotations.ManagedController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ManagedController
class SampleController(private val sampleService: SampleService) {

    @GetMapping("/test/ok")
    suspend fun ok(): ResponseEntity<String> = ResponseEntity.ok(sampleService.process())

    @GetMapping("/test/error")
    suspend fun error(): ResponseEntity<String> {
        sampleService.failingProcess()
        return ResponseEntity.ok("unreachable")
    }
}
