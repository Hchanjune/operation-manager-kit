package io.github.hchanjune.omk.testapp.servlet

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import org.springframework.stereotype.Service

@Service
class SampleService {

    @ManagedOperation(operation = "ProcessSample", useCase = "TestUseCase")
    fun process(): String = "ok"

    @ManagedOperation(operation = "FailingSample", useCase = "TestUseCase")
    fun failingProcess(): String = throw RuntimeException("service-error")

    @ManagedOperation(operation = "FailingClientSample", useCase = "TestUseCase")
    fun failingWithHandledClientError(): String = throw SampleClientException("client-error")

    @ManagedOperation(operation = "FailingServerSample", useCase = "TestUseCase")
    fun failingWithHandledServerError(): String = throw SampleServerException("server-error")
}
