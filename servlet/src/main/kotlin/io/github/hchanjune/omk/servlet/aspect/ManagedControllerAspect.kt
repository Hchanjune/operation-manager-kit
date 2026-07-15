package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
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

        val span = SpanSupport.pushEntrySpan(context, className, methodName, spanIdProvider)

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
