package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedCacheRepository
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
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class ManagedCacheRepositoryAspect(
    private val spanIdProvider: SpanIdProvider
) {

    @Around("@within(managedCacheRepository)")
    fun aroundCacheRepositoryMethod(
        joinPoint: ProceedingJoinPoint,
        managedCacheRepository: ManagedCacheRepository
    ): Any? {
        if (!Operations.hasContext) return joinPoint.proceed()

        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')
        val spanName = "$className.$methodName"

        val tags = MetricTags.Builder()
            .put("cache", className)
            .put("method", methodName)
            .put("operation", context.operation)
            .build()

        val span = context.push(
            name = MetricName(spanName),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = tags,
            descriptor = MetricDescriptor(
                operation = context.operation,
                useCase = context.useCase,
                layer = MetricLayer.CACHE
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
