package io.github.hchanjune.omk.testapp.reactive

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import org.springframework.stereotype.Service

@Service
class SampleService {

    @ManagedOperation(operation = "ProcessSample", useCase = "TestUseCase")
    suspend fun process(): String = "ok"

    @ManagedOperation(operation = "FailingSample", useCase = "TestUseCase")
    suspend fun failingProcess(): String = throw RuntimeException("service-error")

    @ManagedOperation(operation = "FailingClientSample", useCase = "TestUseCase")
    suspend fun failingWithHandledClientError(): String = throw SampleClientException("client-error")

    @ManagedOperation(operation = "FailingServerSample", useCase = "TestUseCase")
    suspend fun failingWithHandledServerError(): String = throw SampleServerException("server-error")
}
