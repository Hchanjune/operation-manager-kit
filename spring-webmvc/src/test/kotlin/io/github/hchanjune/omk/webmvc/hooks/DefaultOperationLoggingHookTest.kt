package io.github.hchanjune.omk.webmvc.hooks

import io.github.hchanjune.omk.webmvc.TestSupport.buildTree
import io.github.hchanjune.omk.webmvc.TestSupport.context
import io.github.hchanjune.omk.webmvc.config.properties.DefaultOperationLoggingProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultOperationLoggingHookTest {

    private fun hook(
        pretty: Boolean = true,
        json: Boolean = true,
        spans: Boolean = false,
        successLevel: LogLevel = LogLevel.INFO,
        failureLevel: LogLevel = LogLevel.ERROR
    ): Pair<CapturingLogger, DefaultOperationLoggingHook> {
        val capturing = CapturingLogger()
        val hook = DefaultOperationLoggingHook(
            prettyLogger = capturing,
            jsonLogger = capturing,
            props = DefaultOperationLoggingProperties(
                pretty = pretty,
                json = json,
                spans = spans,
                successLevel = successLevel,
                failureLevel = failureLevel
            )
        )
        return capturing to hook
    }

    @Test
    fun `onSuccess with json format produces output`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context().apply { buildTree() }
        hook.onSuccess(ctx)
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `onSuccess with pretty format produces output`() {
        val (logger, hook) = hook(pretty = true, json = false)
        val ctx = context().apply { buildTree() }
        hook.onSuccess(ctx)
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `onSuccess with spans=true includes span tree in pretty output`() {
        val (logger, hook) = hook(pretty = true, json = false, spans = true)
        val ctx = context().apply { buildTree() }
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("ENT") || output.contains("APP") || output.contains("DB"))
    }

    @Test
    fun `onFailure with json format includes exception info`() {
        val (logger, hook) = hook(pretty = false, json = true, failureLevel = LogLevel.ERROR)
        val ctx = context().apply { buildTree() }
        hook.onFailure(ctx, RuntimeException("test-error"))
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("RuntimeException") || output.contains("test-error"))
    }

    @Test
    fun `onSuccess with NONE level produces no output`() {
        val (logger, hook) = hook(pretty = true, json = true, successLevel = LogLevel.NONE)
        val ctx = context().apply { buildTree() }
        hook.onSuccess(ctx)
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `onSuccess does not throw when context has no spans`() {
        val (_, hook) = hook()
        hook.onSuccess(context())
    }

    @Test
    fun `onFailure does not throw when context has no spans`() {
        val (_, hook) = hook()
        hook.onFailure(context(), RuntimeException())
    }

    class CapturingLogger : Logger by LoggerFactory.getLogger("omk-test") {
        val messages = mutableListOf<String>()
        override fun info(msg: String)  { messages.add(msg) }
        override fun error(msg: String) { messages.add(msg) }
        override fun warn(msg: String)  { messages.add(msg) }
        override fun debug(msg: String) { messages.add(msg) }
        override fun trace(msg: String) { messages.add(msg) }
        override fun isInfoEnabled()  = true
        override fun isErrorEnabled() = true
        override fun isWarnEnabled()  = true
        override fun isDebugEnabled() = true
        override fun isTraceEnabled() = true
    }
}
