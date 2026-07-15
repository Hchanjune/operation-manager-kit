package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedMetric
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class ManagedMetricAspect (
    private val spanIdProvider: SpanIdProvider,
) {

    @Around("@annotation(managedMetric)")
    fun aroundManagedMetric(
        joinPoint: ProceedingJoinPoint,
        managedMetric: ManagedMetric
    ): Any? {
        if (!Operations.hasContext) return joinPoint.proceed()

        val context = Operations.context
        val spanName = managedMetric.name.ifBlank {
            "${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name.substringBefore('-')}"
        }

        val span = SpanSupport.pushMetricSpan(context, spanName, spanIdProvider)

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