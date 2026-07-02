package io.github.hchanjune.omk.servlet

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperationsTest {

    private val spanIdProvider = SpanIdProvider { "ops-test-span" }

    private fun contextProvider() = object : ManagedContextProvider {
        override fun provide() = ManagedContext(spanIdProvider = spanIdProvider)
    }

    @BeforeTest
    fun setUp() {
        Operations.clear()
        Operations.configureDefaultAsyncHookEnabled(false)
    }

    @AfterTest
    fun tearDown() {
        Operations.clear()
        Operations.configureDefaultAsyncHookEnabled(false)
    }

    @Test
    fun `hasContext returns false when no context is set`() {
        assertTrue(!Operations.hasContext)
    }

    @Test
    fun `initialize enables async hook when defaultAsyncHookEnabled is true`() {
        Operations.configureDefaultAsyncHookEnabled(true)
        val ctx = ManagedContext(spanIdProvider = spanIdProvider)
        Operations.initialize(ctx)
        assertTrue(ctx.isAsyncHookEnabled)
    }

    @Test
    fun `initialize does not enable async hook when defaultAsyncHookEnabled is false`() {
        val ctx = ManagedContext(spanIdProvider = spanIdProvider)
        Operations.initialize(ctx)
        assertTrue(!ctx.isAsyncHookEnabled)
    }

    @Test
    fun `complete does not throw when no context is set`() {
        Operations.complete()
    }

    @Test
    fun `complete ends context when context is present`() {
        val ctx = ManagedContext(spanIdProvider = spanIdProvider)
        ctx.start()
        Operations.applyContext(ctx)
        Operations.complete()
        assertTrue(ctx.durationMs >= 0)
    }

    @Test
    fun `initializeForEvent creates context with explicit traceId`() {
        Operations.configureEventProviders(
            contextProvider = contextProvider(),
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "generated-trace" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "generated-cause" },
            generateWhenMissing = true
        )
        Operations.initializeForEvent(EventMetadata(
            traceId = "explicit-trace",
            causationId = "explicit-cause",
            issuer = "test-issuer",
            eventType = "TestEvent"
        ))
        assertTrue(Operations.hasContext)
        assertEquals("explicit-trace", Operations.context.traceId)
        assertEquals("explicit-cause", Operations.context.causationId)
    }

    @Test
    fun `initializeForEvent generates traceId when metadata traceId is null and generateWhenMissing is true`() {
        Operations.configureEventProviders(
            contextProvider = contextProvider(),
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "generated-trace" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "generated-cause" },
            generateWhenMissing = true
        )
        Operations.initializeForEvent(EventMetadata(null, null, null, null))
        assertEquals("generated-trace", Operations.context.traceId)
        assertEquals("generated-cause", Operations.context.causationId)
    }

    @Test
    fun `initializeForEvent uses empty string when generateWhenMissing is false and metadata is null`() {
        Operations.configureEventProviders(
            contextProvider = contextProvider(),
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "should-not-use" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "should-not-use" },
            generateWhenMissing = false
        )
        Operations.initializeForEvent(EventMetadata(null, null, null, null))
        assertEquals("", Operations.context.traceId)
        assertEquals("", Operations.context.causationId)
    }

    @Test
    fun `initializeForEvent injects issuer when present`() {
        Operations.configureEventProviders(
            contextProvider = contextProvider(),
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "t" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "c" },
            generateWhenMissing = true
        )
        Operations.initializeForEvent(EventMetadata(null, null, issuer = "my-service", eventType = null))
        assertEquals("my-service", Operations.context.issuer)
    }

    @Test
    fun `initializeForEvent marks context as event and injects eventType`() {
        Operations.configureEventProviders(
            contextProvider = contextProvider(),
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "t" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "c" },
            generateWhenMissing = true
        )
        Operations.initializeForEvent(EventMetadata(null, null, null, eventType = "OrderCreated"))
        assertTrue(Operations.context.isEvent)
    }
}
