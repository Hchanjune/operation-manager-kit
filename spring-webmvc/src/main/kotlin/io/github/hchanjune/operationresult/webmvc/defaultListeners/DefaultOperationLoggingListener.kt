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
            throwable = null,
            message = "operation success: {}",
            args = arrayOf(formatContext(context)),
        )
    }

    override fun onFailure(context: OperationContext, exception: Throwable) {
        log(
            level = props.failureLevel,
            throwable = exception,
            message = "operation failure: {}",
            args = arrayOf(formatContext(context)),
        )
    }

    private fun log(
        level: LogLevel,
        throwable: Throwable?,
        message: String,
        args: Array<Any>,
    ) {
        if (level == LogLevel.NONE) return

        // throwable이 있으면 마지막 인자로 붙여서 호출 (SLF4J 규칙)
        val fullArgs: Array<Any> =
            if (throwable == null) args else args + throwable

        when (level) {
            LogLevel.TRACE -> if (logger.isTraceEnabled) logger.trace(message, *fullArgs)
            LogLevel.DEBUG -> if (logger.isDebugEnabled) logger.debug(message, *fullArgs)
            LogLevel.INFO  -> if (logger.isInfoEnabled)  logger.info(message, *fullArgs)
            LogLevel.WARN  -> if (logger.isWarnEnabled)  logger.warn(message, *fullArgs)
            LogLevel.ERROR -> if (logger.isErrorEnabled) logger.error(message, *fullArgs)
            LogLevel.NONE  -> Unit
        }
    }

    private fun formatContext(ctx: OperationContext): String =
        if (props.pretty) {
            "operation=${ctx.operation}, useCase=${ctx.useCase}, event=${ctx.event}, issuer=${ctx.issuer}"
        } else {
            // 기본은 짧게 (toString은 피하는 편이 안전)
            "operation=${ctx.operation}, useCase=${ctx.useCase}, event=${ctx.event}"
        }
}
