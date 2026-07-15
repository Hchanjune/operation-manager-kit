package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ManagedOperationAspect(
    private val spanIdProvider: SpanIdProvider
) {

    @Around("@annotation(operationAnnotation)")
    fun aroundOperation(
        joinPoint: ProceedingJoinPoint,
        operationAnnotation: ManagedOperation
    ): Any? {
        if (!Operations.hasContext) return joinPoint.proceed()

        val context = Operations.context

        context.injectAnnotationInfo(
            operation = operationAnnotation.operation,
            useCase = operationAnnotation.useCase
        )

        val className = joinPoint.signature.declaringType.simpleName
        val spanName = operationAnnotation.operation.ifBlank {
            "$className.${joinPoint.signature.name.substringBefore('-')}"
        }

        val span = SpanSupport.pushOperationSpan(
            context = context,
            operation = operationAnnotation.operation,
            useCase = operationAnnotation.useCase,
            spanName = spanName,
            idProvider = spanIdProvider
        )

        return try {
            val result = joinPoint.proceed()
            span.end()
            context.pop()
            result
        } catch (e: Throwable) {
            span.end(e)
            context.pop()
            throw e
        }
    }

}