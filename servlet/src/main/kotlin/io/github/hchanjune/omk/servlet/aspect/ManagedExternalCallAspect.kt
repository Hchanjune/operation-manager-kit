package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedExternalCall
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class ManagedExternalCallAspect(
    private val spanIdProvider: SpanIdProvider,
) {

    @Around("@annotation(managedExternalCall)")
    fun aroundExternalCall(
        joinPoint: ProceedingJoinPoint,
        managedExternalCall: ManagedExternalCall
    ): Any? {
        if (!Operations.hasContext) return joinPoint.proceed()

        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')
        val destination = managedExternalCall.destination.ifBlank { className }
        val route = managedExternalCall.route

        val span = SpanSupport.pushExternalSpan(context, className, methodName, destination, route, spanIdProvider)

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
