package io.github.hchanjune.omk.webflux.hooks

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.webflux.config.properties.DefaultOperationLoggingProperties
import org.slf4j.Logger
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DefaultOperationLoggingHook(
    private val prettyLogger: Logger,
    private val jsonLogger: Logger,
    private val props: DefaultOperationLoggingProperties
) : OperationLoggingHook {

    override fun onSuccess(context: ManagedContext) {
        if (props.pretty) log(prettyLogger, props.successLevel) { prettyContext(context, null) }
        if (props.json)   log(jsonLogger,   props.successLevel) { jsonContext(context, null) }
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        if (props.pretty) log(prettyLogger, props.failureLevel) { prettyContext(context, exception) }
        if (props.json)   log(jsonLogger,   props.failureLevel) { jsonContext(context, exception) }
    }

    private fun log(logger: Logger, level: LogLevel, message: () -> String) {
        when (level) {
            LogLevel.TRACE -> if (logger.isTraceEnabled) logger.trace(message())
            LogLevel.DEBUG -> if (logger.isDebugEnabled) logger.debug(message())
            LogLevel.INFO  -> if (logger.isInfoEnabled)  logger.info(message())
            LogLevel.WARN  -> if (logger.isWarnEnabled)  logger.warn(message())
            LogLevel.ERROR -> if (logger.isErrorEnabled) logger.error(message())
            LogLevel.NONE  -> Unit
        }
    }

    private fun prettyContext(context: ManagedContext, exception: Throwable?): String {
        fun StringBuilder.row(label: String, value: String) {
            if (value.isNotBlank()) appendLine("├─ $label : $value")
        }
        return buildString {
            appendLine(" ")
            appendLine("┌───────────────────────────────────────────────────────────────────────────────────")
            appendLine(if (exception != null) "│ ❌ Failed" else "│ ✅ Success")
            appendLine("├─ Status      : ${if (exception != null) "FAILED" else "SUCCESS"}")
            appendLine("├─ TraceId     : ${context.traceId}")
            appendLine("├─ CausationId : ${context.causationId}")
            appendLine("├─ Issuer      : ${context.issuer}")
            appendLine("├─ Protocol    : ${context.protocol}")
            row("Type        ", context.type)
            row("HTTP_URI    ", context.uri)
            row("HTTP_METHOD ", context.method)
            row("Entry Point ", context.entrypoint)
            row("Service     ", context.service)
            row("Operation   ", context.operation)
            row("UseCase     ", context.useCase)
            appendLine("├─ Message     : ${context.message}")
            if (props.response) row("Response    ", context.response)
            appendLine("├─ Performance : ${context.durationMs}Ms")
            appendLine("├─ Timestamp   : ${context.timestamp}")
            exception?.let {
                appendLine("├─ Exception   : ${it::class.simpleName}: ${it.message}")
                val frames = it.stackTrace.take(20)
                if (frames.isNotEmpty()) {
                    appendLine("├─ Stacktrace  : ${frames[0]}")
                    frames.drop(1).forEach { e -> appendLine("│               $e") }
                }
            }
            if (context.hookRecords.isNotEmpty()) {
                appendLine("├─ Hooks       : ${context.hookRecords.joinToString(", ") { r -> "${r.hookName}=${if (r.success) "OK" else "FAIL"}" }}")
            }
            if (props.spans) {
                context.rootSpan?.let { root ->
                    appendLine("├─ Spans      :")
                    appendSpanTree(root, 0)
                }
            }
            appendLine("└───────────────────────────────────────────────────────────────────────────────────")
            appendLine(" ")
        }
    }

    private fun jsonContext(context: ManagedContext, exception: Throwable?): String {
        fun esc(s: String): String = buildString(s.length + 16) {
            for (ch in s) when (ch) {
                '\\'     -> append("\\\\")
                '"'      -> append("\\\"")
                '\b'     -> append("\\b")
                '' -> append("\\f")
                '\n'     -> append("\\n")
                '\r'     -> append("\\r")
                '\t'     -> append("\\t")
                else -> if (ch.code in 0x00..0x1F) append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
            }
        }
        fun trunc(s: String, max: Int = 2000) = if (s.length <= max) s else s.take(max) + "…(truncated)"
        return buildString {
            append("{")
            var first = true
            fun field(key: String, value: Any?) {
                if (value == null) return
                val s = trunc(value.toString())
                if (!first) append(",")
                append("\"$key\":\"${esc(s)}\"")
                first = false
            }
            fun fieldNum(key: String, value: Number?) {
                if (value == null) return
                if (!first) append(",")
                append("\"$key\":$value")
                first = false
            }
            field("status", if (exception != null) "FAILED" else "SUCCESS")
            field("traceId", context.traceId)
            field("causationId", context.causationId)
            field("issuer", context.issuer)
            field("protocol", context.protocol)
            field("method", context.method)
            field("uri", context.uri)
            field("entrypoint", context.entrypoint)
            field("service", context.service)
            field("operation", context.operation)
            field("useCase", context.useCase)
            fieldNum("durationMs", context.durationMs)
            field("message", context.message)
            if (props.response && context.response.isNotEmpty()) {
                val responseStr = trunc(context.response, 5000)
                if (!first) append(",")
                val trimmed = responseStr.trimStart()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) append("\"response\":$responseStr")
                else append("\"response\":\"${esc(responseStr)}\"")
                first = false
            }
            exception?.let {
                val rc = rootCause(it)
                field("exception.type", it::class.simpleName)
                field("exception.detail", it.message)
                field("exception.rootCause", rc::class.simpleName)
                field("exception.message", rc.message)
                field("exception.rootCauseTopFrames", rc.stackTrace.take(8).joinToString("\n") { e -> e.toString() })
            }
            if (context.hookRecords.isNotEmpty()) {
                if (!first) append(",")
                append("\"hooks\":")
                append(context.hookRecords.joinToString(",", "[", "]") { r ->
                    "{\"hook\":\"${esc(r.hookName)}\",\"result\":\"${if (r.success) "OK" else "FAIL"}\"}"
                })
                first = false
            }
            field("timestamp", context.timestamp.toString())
            append("}")
        }
    }

    private fun StringBuilder.appendSpanTree(span: MetricSpan, depth: Int) {
        val outcome = span.outcome
        val duration = span.durationMs?.let { "${it}ms" } ?: "?"
        val status = outcome?.status?.name ?: "?"
        val error = outcome?.errorType?.let { " ($it)" } ?: ""
        val connector = if (depth == 0) "" else "└─ "
        val indent = "│    " + "     ".repeat(depth)
        val layer = "[${span.descriptor.layer.label}]"
        val timestamp = span.startTime?.let { TIME_FMT.format(Instant.ofEpochMilli(it)) } ?: "?        "
        val thread = "[${span.threadName}]"
        appendLine("$indent$connector$layer $timestamp  $thread  ${span.name.value}  [$duration]  $status$error")
        span.children.forEach { child -> appendSpanTree(child, depth + 1) }
    }

    private fun rootCause(t: Throwable): Throwable {
        var cur: Throwable = t
        val seen = HashSet<Throwable>(8)
        while (true) {
            val next = cur.cause ?: break
            if (!seen.add(next)) break
            cur = next
        }
        return cur
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
    }
}
