package io.github.hchanjune.omk.testapp.webflux

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import org.springframework.stereotype.Service

@Service
class SampleService {

    @ManagedOperation(operation = "ProcessSample", useCase = "TestUseCase")
    suspend fun process(): String = "ok"

    @ManagedOperation(operation = "FailingSample", useCase = "TestUseCase")
    suspend fun failingProcess(): String = throw RuntimeException("service-error")
}
