package io.github.hchanjune.operationresult.webmvc.defaultListeners

import io.github.hchanjune.operationresult.core.models.context.MetricsContext
import io.github.hchanjune.operationresult.core.models.context.OperationContext
import io.github.hchanjune.operationresult.core.models.context.TelemetryContext
import io.github.hchanjune.operationresult.webmvc.config.properties.DefaultOperationLoggingProperties
import org.slf4j.Logger

class DefaultOperationLoggingListener(
    private val prettyLogger: Logger,
    private val jsonLogger: Logger,
    private val props: DefaultOperationLoggingProperties
) : OperationLoggingListener {

    override fun onSuccess(operation: OperationContext, metrics: MetricsContext, telemetry: TelemetryContext) {
        if (props.pretty) log(logger = prettyLogger, level = props.successLevel, args = prettyContext(operation, telemetry, null))
        if (props.json) log(logger = jsonLogger, level = props.successLevel, args = jsonContext(operation, telemetry, null))
    }

    override fun onFailure(operation: OperationContext, metrics: MetricsContext, telemetry: TelemetryContext, exception: Throwable) {
        if (props.pretty) log(logger = prettyLogger, level = props.failureLevel, args = prettyContext(operation, telemetry, exception))
        if (props.json) log(logger = jsonLogger, level = props.failureLevel, args = jsonContext(operation, telemetry, exception))
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

    private fun prettyContext(operation: OperationContext, telemetry: TelemetryContext, exception: Throwable?): String {
        return buildString {
            appendLine(" ")
            appendLine("┌───────────────────────────────────────────────────────────────────────────────────")
            appendLine(if (exception != null) "│ ❌ Failed" else "│ ✅ Success")
            appendLine("├─ TraceId     : ${telemetry.traceId}")
            appendLine("├─ SpanId      : ${telemetry.spanId}")
            appendLine("├─ Correlation : ${telemetry.traceId}")
            appendLine("├─ Issuer      : ${operation.issuer}")
            appendLine("├─ Entry Point : ${operation.entrypoint}")
            appendLine("├─ Service     : ${operation.service}")
            appendLine("├─ Function    : ${operation.function}")
            appendLine("├─ Operation   : ${operation.operation}")
            appendLine("├─ UseCase     : ${operation.useCase}")
            appendLine("├─ Event       : ${operation.event}")
            appendLine("├─ Attributes  : ${operation.attributes}")
            appendLine("├─ Message     : ${operation.message}")
            appendLine("├─ Response    : ${operation.response}")
            appendLine("├─ Performance : ${operation.durationMs}Ms")
            appendLine("├─ Timestamp   : ${operation.timestamp}")
            exception?.let {
            appendLine("├─ Exception   : ${it::class.simpleName}: ${it.message}")
            appendLine("├─ Stacktrace  : ${it.stackTrace.take(20).joinToString("\n") { e -> e.toString() }}")
            }
            appendLine("└───────────────────────────────────────────────────────────────────────────────────")
            appendLine(" ")
        }
    }

    private fun jsonContext(operation: OperationContext, telemetry: TelemetryContext, exception: Throwable?): String {
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

        fun add(key: String, value: Any?): String {
            if (value == null) return ""
            val s = trunc(value.toString())
            if (s.isBlank()) return ""
            return "\"$key\":\"${esc(s)}\""
        }

        fun addNum(key: String, value: Number?): String {
            if (value == null) return ""
            return "\"$key\":$value"
        }

        return buildString {
            append("{")
            val fields = mutableListOf<String>()

            fields += add("traceId", telemetry.traceId)
            fields += add("spanId", telemetry.spanId)
            fields += add("correlationId", telemetry.traceId)
            fields += add("issuer", operation.issuer)
            fields += add("entrypoint", operation.entrypoint)
            fields += add("service", operation.service)
            fields += add("function", operation.function)
            fields += add("operation", operation.operation)
            fields += add("useCase", operation.useCase)
            fields += add("event", operation.event)
            fields += add("message", operation.message)
            fields += add("response", operation.response)
            fields += addNum("durationMs", operation.durationMs)
            fields += add("timestamp", operation.timestamp)

            exception?.let {
                val rc = rootCause(it)
                fields += add("exception", "${it::class.simpleName}:${it.message}")
                fields += add("rootCause", "${rc::class.simpleName}:${rc.message}")
                fields += add(
                    "rootCauseTopFrames",
                    rc.stackTrace.take(8).joinToString("\\n") { e -> e.toString() }
                )
            }

            append(fields.filter { it.isNotBlank() }.joinToString(","))
            append("}")
        }
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










