package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedControllerAspect(
    private val spanIdProvider: SpanIdProvider
) {

    @Around("@within(managedController)")
    fun aroundController(
        joinPoint: ProceedingJoinPoint,
        managedController: ManagedController
    ): Any? {
        if (!Operations.hasContext) return joinPoint.proceed()

        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        if (context.entrypoint != className) {
            context.injectEntryPoint(className)
        }

        val tags = MetricTags.Builder()
            .put("entrypoint", className)
            .put("method", methodName)
            .build()

        val span = context.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = tags,
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
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
