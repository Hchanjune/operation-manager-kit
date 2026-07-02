package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
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

        val tags = MetricTags.Builder()
            .put("service", context.service)
            .put("operation", operationAnnotation.operation)
            .put("use_case", operationAnnotation.useCase)
            .build()

        val span = context.push(
            name = MetricName(spanName),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = tags,
            descriptor = MetricDescriptor(
                operation = operationAnnotation.operation,
                useCase = operationAnnotation.useCase,
                layer = MetricLayer.APPLICATION
            ),
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