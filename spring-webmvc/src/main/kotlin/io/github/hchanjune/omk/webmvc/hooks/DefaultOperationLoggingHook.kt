package io.github.hchanjune.omk.webmvc.hooks

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.webmvc.config.properties.DefaultOperationLoggingProperties
import org.slf4j.Logger
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

            fields += add("status", if (exception != null) "FAILED" else "SUCCESS")
            fields += add("traceId", context.traceId)
            fields += add("causationId", context.causationId)
            fields += add("issuer", context.issuer)
            fields += add("protocol", context.protocol)
            fields += add("method", context.method)
            fields += add("uri", context.uri)
            fields += add("entrypoint", context.entrypoint)
            fields += add("service", context.service)
            fields += add("operation", context.operation)
            fields += add("useCase", context.useCase)
            fields += add("status", if (exception != null) "FAILED" else "SUCCESS")
            fields += addNum("durationMs", context.durationMs)
            fields += add("message", context.message)
            fields += add("response", context.response)

            exception?.let {
                val rc = rootCause(it)
                fields += add("exception", "${it::class.simpleName}:${it.message}")
                fields += add("rootCause", "${rc::class.simpleName}:${rc.message}")
                fields += add(
                    "rootCauseTopFrames",
                    rc.stackTrace.take(8).joinToString("\\n") { e -> e.toString() }
                )
            }

            fields += add("timestamp", context.timestamp.toString())

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