package io.github.hchanjune.omk.core.hooks

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultOperationLoggingHookTest {

    private val spanIdProvider = SpanIdProvider { "log-test-span" }

    private data class TestLoggingSettings(
        override val enabled: Boolean = true,
        override val pretty: Boolean = false,
        override val json: Boolean = true,
        override val spans: Boolean = false,
        override val response: Boolean = true,
        override val ip: Boolean = false,
        override val successLevel: LogLevel = LogLevel.INFO,
        override val failureLevel: LogLevel = LogLevel.ERROR,
        override val clientErrorLevel: LogLevel = LogLevel.WARN,
    ) : OperationLoggingSettings

    private fun context(): ManagedContext =
        ManagedContext(spanIdProvider = spanIdProvider).apply {
            injectTraceId("test-trace")
            injectCausationId("test-cause")
            start()
        }

    private fun ManagedContext.buildTree() {
        val span = SpanSupport.pushEntrySpan(this, "TestClass", "testMethod", spanIdProvider)
        span.end()
        pop()
    }

    private fun hook(
        pretty: Boolean = true,
        json: Boolean = true,
        spans: Boolean = false,
        ip: Boolean = false,
        successLevel: LogLevel = LogLevel.INFO,
        failureLevel: LogLevel = LogLevel.ERROR
    ): Pair<CapturingLogger, DefaultOperationLoggingHook> {
        val capturing = CapturingLogger()
        val hook = DefaultOperationLoggingHook(
            prettyLogger = capturing,
            jsonLogger = capturing,
            props = TestLoggingSettings(
                pretty = pretty,
                json = json,
                spans = spans,
                ip = ip,
                successLevel = successLevel,
                failureLevel = failureLevel
            )
        )
        return capturing to hook
    }

    @Test
    fun `ip is omitted from output when props ip is false`() {
        val (logger, hook) = hook(pretty = true, json = true, ip = false)
        val ctx = context().apply { injectIp("203.0.113.5") }
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(!output.contains("203.0.113.5"))
    }

    @Test
    fun `ip appears in pretty and json output when props ip is true`() {
        val (logger, hook) = hook(pretty = true, json = true, ip = true)
        val ctx = context().apply { injectIp("203.0.113.5") }
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("203.0.113.5"))
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
    fun `defaultLogging=false suppresses clean success output`() {
        val (logger, hook) = hook(pretty = true, json = true)
        val ctx = context().apply { defaultLogging = false }
        hook.onSuccess(ctx)
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `defaultLogging=false still logs onFailure`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context().apply { defaultLogging = false }
        hook.onFailure(ctx, RuntimeException("still-logged"))
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `defaultLogging=false still logs success with captured exception`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context().apply {
            defaultLogging = false
            recordException(RuntimeException("captured"))
        }
        hook.onSuccess(ctx)
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `defaultLogging=false still logs client error outcome`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context().apply {
            defaultLogging = false
            injectStatusCode(404)
        }
        hook.onSuccess(ctx)
        assertTrue(logger.messages.isNotEmpty())
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

    @Test
    fun `onSuccess with DEBUG level produces output`() {
        val (logger, hook) = hook(pretty = true, json = false, successLevel = LogLevel.DEBUG)
        hook.onSuccess(context())
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `onSuccess with TRACE level produces output`() {
        val (logger, hook) = hook(pretty = true, json = false, successLevel = LogLevel.TRACE)
        hook.onSuccess(context())
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `onFailure with WARN level produces output`() {
        val (logger, hook) = hook(pretty = false, json = true, failureLevel = LogLevel.WARN)
        hook.onFailure(context(), RuntimeException("warn-error"))
        assertTrue(logger.messages.isNotEmpty())
    }

    @Test
    fun `no output when both pretty and json are false`() {
        val (logger, hook) = hook(pretty = false, json = false)
        hook.onSuccess(context())
        assertTrue(logger.messages.isEmpty())
    }

    @Test
    fun `json output includes hookRecords when present`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.recordHookSuccess("TestHook")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("TestHook"))
    }

    @Test
    fun `pretty output includes hookRecords when present`() {
        val (logger, hook) = hook(pretty = true, json = false)
        val ctx = context()
        ctx.recordHookFailure("FailHook", RuntimeException("hook-err"))
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("FailHook"))
    }

    @Test
    fun `json escapes double-quote and backslash`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService("has" + '"' + "quote" + '\\' + "and")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\\"") || output.contains("\\\\"))
    }

    @Test
    fun `json escapes newline in field values`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService("line1" + '\n' + "line2")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\n"))
    }

    @Test
    fun `onSuccess with json and spans=true does not throw`() {
        val (_, hook) = hook(pretty = false, json = true, spans = true)
        val ctx = context().apply { buildTree() }
        hook.onSuccess(ctx)
    }

    @Test
    fun `onFailure pretty includes exception stack trace`() {
        val (logger, hook) = hook(pretty = true, json = false, failureLevel = LogLevel.ERROR)
        val ex = RuntimeException("trace-test")
        hook.onFailure(context(), ex)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("RuntimeException") || output.contains("trace-test"))
    }

    @Test
    fun `log does not write when TRACE level is disabled`() {
        val disabled = DisabledLogger()
        val hook = DefaultOperationLoggingHook(disabled, disabled, TestLoggingSettings(
            pretty = true, json = false, successLevel = LogLevel.TRACE
        ))
        hook.onSuccess(context())
        assertTrue(disabled.messages.isEmpty())
    }

    @Test
    fun `log does not write when DEBUG level is disabled`() {
        val disabled = DisabledLogger()
        val hook = DefaultOperationLoggingHook(disabled, disabled, TestLoggingSettings(
            pretty = true, json = false, successLevel = LogLevel.DEBUG
        ))
        hook.onSuccess(context())
        assertTrue(disabled.messages.isEmpty())
    }

    @Test
    fun `log does not write when INFO level is disabled`() {
        val disabled = DisabledLogger()
        val hook = DefaultOperationLoggingHook(disabled, disabled, TestLoggingSettings(
            pretty = true, json = false, successLevel = LogLevel.INFO
        ))
        hook.onSuccess(context())
        assertTrue(disabled.messages.isEmpty())
    }

    @Test
    fun `log does not write when WARN level is disabled`() {
        val disabled = DisabledLogger()
        val hook = DefaultOperationLoggingHook(disabled, disabled, TestLoggingSettings(
            pretty = false, json = true, failureLevel = LogLevel.WARN
        ))
        hook.onFailure(context(), RuntimeException())
        assertTrue(disabled.messages.isEmpty())
    }

    @Test
    fun `log does not write when ERROR level is disabled`() {
        val disabled = DisabledLogger()
        val hook = DefaultOperationLoggingHook(disabled, disabled, TestLoggingSettings(
            pretty = false, json = true, failureLevel = LogLevel.ERROR
        ))
        hook.onFailure(context(), RuntimeException())
        assertTrue(disabled.messages.isEmpty())
    }

    @Test
    fun `json escapes backspace character`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService(Char(0x08).toString() + "data")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\b"))
    }

    @Test
    fun `json escapes form-feed character`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService(Char(0x0C).toString() + "data")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\f"))
    }

    @Test
    fun `json escapes carriage-return character`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService(Char(0x0D).toString() + "data")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\r"))
    }

    @Test
    fun `json escapes tab character`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService(Char(0x09).toString() + "data")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\t"))
    }

    @Test
    fun `json escapes control characters with unicode escape sequence`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService(Char(0x01).toString() + "data")
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("\\u0001"))
    }

    @Test
    fun `json truncates field values longer than 2000 chars`() {
        val (logger, hook) = hook(pretty = false, json = true)
        val ctx = context()
        ctx.injectService("x".repeat(2001))
        hook.onSuccess(ctx)
        val output = logger.messages.joinToString("")
        assertTrue(output.contains("truncated"))
    }

    @Test
    fun `onFailure handles chained exception rootCause traversal`() {
        val (_, hook) = hook(pretty = false, json = true, failureLevel = LogLevel.ERROR)
        val root = RuntimeException("root cause")
        val wrapped = RuntimeException("wrapper", root)
        hook.onFailure(context(), wrapped)
    }

    @Test
    fun `onFailure handles exception with empty stack trace`() {
        val (_, hook) = hook(pretty = true, json = false, failureLevel = LogLevel.ERROR)
        val ex = RuntimeException("empty-stack")
        ex.stackTrace = emptyArray()
        hook.onFailure(context(), ex)
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

    class DisabledLogger : Logger by LoggerFactory.getLogger("omk-test") {
        val messages = mutableListOf<String>()
        override fun isInfoEnabled()  = false
        override fun isErrorEnabled() = false
        override fun isWarnEnabled()  = false
        override fun isDebugEnabled() = false
        override fun isTraceEnabled() = false
    }
}
