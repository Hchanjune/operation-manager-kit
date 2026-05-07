package io.github.hchanjune.omk.webmvc.hooks

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.webmvc.config.properties.DefaultOperationLoggingProperties
import org.slf4j.Logger
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.text.iterator

class DefaultOperationLoggingHook(
    private val prettyLogger: Logger,
    private val jsonLogger: Logger,
    private val props: DefaultOperationLoggingProperties
) : OperationLoggingHook {

    override fun onSuccess(context: ManagedContext) {
        if (props.pretty) log(logger = prettyLogger, level = props.successLevel, args = prettyContext(context, null))
        if (props.json) log(logger = jsonLogger, level = props.successLevel, args = jsonContext(context, null))
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        if (props.pretty) log(logger = prettyLogger, level = props.failureLevel, args = prettyContext(context, exception))
        if (props.json) log(logger = jsonLogger, level = props.failureLevel, args = jsonContext(context, exception))
    }

    private fun log(
        logger: Logger,
        level: LogLevel,
        args: String,
    ) {
        if (level == LogLevel.NONE) return

        when (level) {
            LogLevel.TRACE -> if (logger.isTraceEnabled) logger.trace(args)
            LogLevel.DEBUG -> if (logger.isDebugEnabled) logger.debug(args)
            LogLevel.INFO  -> if (logger.isInfoEnabled)  logger.info(args)
            LogLevel.WARN  -> if (logger.isWarnEnabled)  logger.warn(args)
            LogLevel.ERROR -> if (logger.isErrorEnabled) logger.error(args)
            else -> Unit
        }
    }

    private fun prettyContext(context: ManagedContext, exception: Throwable?): String {
        return buildString {
            appendLine(" ")
            appendLine("┌───────────────────────────────────────────────────────────────────────────────────")
            appendLine(if (exception != null) "│ ❌ Failed" else "│ ✅ Success")
            appendLine("├─ Status      : ${if (exception != null) "FAILED" else "SUCCESS"}")
            appendLine("├─ TraceId     : ${context.traceId}")
            appendLine("├─ CausationId : ${context.causationId}")
            appendLine("├─ Issuer      : ${context.issuer}")
            appendLine("├─ Protocol    : ${context.protocol}")
            appendLine("├─ Type        : ${context.type}")
            appendLine("├─ HTTP_URI    : ${context.uri}")
            appendLine("├─ HTTP_METHOD : ${context.method}")
            appendLine("├─ Entry Point : ${context.entrypoint}")
            appendLine("├─ Service     : ${context.service}")
            //appendLine("├─ Function    : ${context.function}")
            appendLine("├─ Operation   : ${context.operation}")
            appendLine("├─ UseCase     : ${context.useCase}")
            //appendLine("├─ Event       : ${context.event}")
            //appendLine("├─ Attributes  : ${context.attributes}")
            appendLine("├─ Message     : ${context.message}")
            appendLine("├─ Response    : ${context.response}")
            appendLine("├─ Performance : ${context.durationMs}Ms")
            appendLine("├─ Timestamp   : ${context.timestamp}")

            exception?.let {
            appendLine("├─ Exception   : ${it::class.simpleName}: ${it.message}")
            appendLine("├─ Stacktrace  : ${it.stackTrace.take(20).joinToString("\n") { e -> e.toString() }}")
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
            for (ch in s) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"'  -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code in 0x00..0x1F) append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                        else append(ch)
                    }
                }
            }
        }

        fun trunc(s: String, max: Int = 2000): String =
            if (s.length <= max) s else s.take(max) + "…(truncated)"

        return buildString {
            append("{")
            var first = true

            fun field(key: String, value: Any?) {
                if (value == null) return
                val s = trunc(value.toString())
                if (s.isBlank()) return
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
            field("response", context.response)

            exception?.let {
                val rc = rootCause(it)
                field("exception.type", it::class.simpleName)
                field("exception.detail", it.message)
                field("exception.rootCause", rc::class.simpleName)
                field("exception.message", rc.message)
                field("exception.rootCauseTopFrames", rc.stackTrace.take(8).joinToString("\\n") { e -> e.toString() })
            }

            if (context.hookRecords.isNotEmpty()) {
                field("hooks", context.hookRecords.joinToString(",", "[", "]") { r ->
                    "{\"hook\":\"${r.hookName}\",\"result\":\"${if (r.success) "OK" else "FAIL"}\"}"
                })
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

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
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


}