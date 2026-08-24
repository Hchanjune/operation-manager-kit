package io.github.hchanjune.omk.testapp.reactive

import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.annotations.ManagedExternalCall
import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.reactive.async.managedContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/**
 * The shape that motivated `ManagedContextElement`: a client adapter that is a plain blocking
 * function — no continuation for the aspect to read a Reactor context from — called after the
 * execution has moved off its original thread with `withContext(Dispatchers.IO)`.
 */
@Component
class SampleExternalClient {

    @ManagedExternalCall(destination = "SampleRemote", route = "/remote/{id}")
    fun fetch(id: String): String = "remote-$id"
}

data class SampleAsyncEvent(val id: String)

@Component
class SampleAsyncEventHandler(private val client: SampleExternalClient) {

    /** Blocking entry point (Kafka-listener shaped) that fans out to a dispatcher of its own. */
    @ManagedEventHandler
    fun handle(event: SampleAsyncEvent): String = runBlocking {
        withContext(Dispatchers.IO + managedContextElement()) { client.fetch(event.id) }
    }

    /** Same handler without the element — the context does not follow the thread switch. */
    @ManagedEventHandler
    fun handleUnpropagated(event: SampleAsyncEvent): String = runBlocking {
        withContext(Dispatchers.IO) { client.fetch(event.id) }
    }
}

@Service
class SampleAsyncService(private val client: SampleExternalClient) {

    @ManagedOperation(operation = "AsyncFanOut", useCase = "TestUseCase")
    suspend fun fanOut(): String =
        withContext(Dispatchers.IO + managedContextElement()) { client.fetch("1") }
}
