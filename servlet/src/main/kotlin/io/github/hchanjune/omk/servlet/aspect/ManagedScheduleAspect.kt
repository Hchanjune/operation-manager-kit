package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedSchedule
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.support.Results
import io.github.hchanjune.omk.servlet.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedScheduleAspect(
    private val spanIdProvider: SpanIdProvider,
    private val runtime: OperationRuntime? = null,
) {

    @Around("@annotation(managedSchedule)")
    fun aroundSchedule(
        joinPoint: ProceedingJoinPoint,
        managedSchedule: ManagedSchedule
    ): Any? {
        val contextOwner = !Operations.hasContext

        if (contextOwner) {
            Operations.initializeForSchedule(runtime)
        }

        val context = Operations.context
        // Only the owner decides — a shared outer context's spans belong to the enclosing operation.
        if (contextOwner && managedSchedule.quietWhenEmpty) context.holdBridgedSpans()
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        context.injectEntryPoint(className)

        val span = SpanSupport.pushEntrySpan(context, className, methodName, spanIdProvider)

        return try {
            val result = joinPoint.proceed()
            // Quiet only when this aspect owns the context — silencing a shared outer
            // context would suppress the enclosing operation's log as well.
            val quiet = contextOwner && managedSchedule.quietWhenEmpty && Results.isEmpty(result)
            if (quiet) context.defaultLogging = false
            span.end()
            context.pop()
            if (contextOwner) {
                if (quiet) context.discardBridgedSpans() else context.releaseBridgedSpans()
                Operations.complete()
                Operations.hook?.onSuccess(context)
            }
            result
        } catch (e: Throwable) {
            span.end(e)
            context.pop()
            if (contextOwner) {
                context.releaseBridgedSpans()
                Operations.complete()
                Operations.hook?.onFailure(context, e)
            }
            throw e
        } finally {
            if (contextOwner) Operations.clear()
        }
    }

}
