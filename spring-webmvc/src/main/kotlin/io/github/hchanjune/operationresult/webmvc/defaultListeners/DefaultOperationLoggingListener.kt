package io.github.hchanjune.operationresult.webmvc.defaultListeners

import io.github.hchanjune.operationresult.core.models.OperationContext
import org.slf4j.Logger

class DefaultOperationLoggingListener(
    private val logger: Logger,
    private val props: DefaultOperationLoggingProperties
) : OperationLoggingListener {

    override fun onSuccess(context: OperationContext) {
        log(
            level = props.successLevel,
            args = formatContext(context),
        )
    }

    override fun onFailure(context: OperationContext, exception: Throwable) {
        log(
            level = props.failureLevel,
            args = formatContext(context, exception),
        )
    }

    private fun log(
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

    private fun formatContext(ctx: OperationContext, exception: Throwable? = null): String =
        if (props.pretty) {
            prettyContext(ctx, exception)
        } else {
            "operation=${ctx.operation}, useCase=${ctx.useCase}, event=${ctx.event}"
        }

    private fun prettyContext(context: OperationContext, exception: Throwable?): String {
        return buildString {
            appendLine(" ")
            appendLine("┌───────────────────────────────────────────────────────")
            appendLine(if (exception != null) "│ ❌ Failed" else "│ ✅ Success")
            appendLine("├─ Correlation : ${context.correlationId}")
            appendLine("├─ Issuer      : ${context.issuer}")
            appendLine("├─ Entry Point : ${context.entrypoint}")
            appendLine("├─ Service     : ${context.service}")
            appendLine("├─ Function    : ${context.function}")
            appendLine("├─ Operation   : ${context.operation}")
            appendLine("├─ UseCase     : ${context.useCase}")
            appendLine("├─ Event       : ${context.event}")
            appendLine("├─ Attributes  : ${context.attributes}")
            appendLine("├─ Message     : ${context.message}")
            appendLine("├─ Response    : ${context.response}")
            appendLine("├─ Performance : ${context.durationMs}Ms")
            appendLine("├─ Timestamp   : ${context.timestamp}")
            exception?.let {
            appendLine("├─ Exception   : ${exception::class.simpleName}: ${it.message}")
            appendLine("├─ Stacktrace  : ${exception.stackTrace.joinToString("\n")}")
            }
            appendLine("└───────────────────────────────────────────────────────")
            appendLine(" ")
        }




    }
}










