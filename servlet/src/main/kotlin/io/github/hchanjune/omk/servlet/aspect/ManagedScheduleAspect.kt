package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedSchedule
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
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        context.injectEntryPoint(className)

        val span = context.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder()
                .put("entrypoint", className)
                .put("method", methodName)
                .build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )

        return try {
            val result = joinPoint.proceed()
            // Quiet only when this aspect owns the context — silencing a shared outer
            // context would suppress the enclosing operation's log as well.
            if (contextOwner && managedSchedule.quietWhenEmpty && isEmptyResult(result)) {
                context.defaultLogging = false
            }
            span.end()
            context.pop()
            if (contextOwner) {
                Operations.complete()
                Operations.hook?.onSuccess(context)
            }
            result
        } catch (e: Throwable) {
            span.end(e)
            context.pop()
            if (contextOwner) {
                Operations.complete()
                Operations.hook?.onFailure(context, e)
            }
            throw e
        } finally {
            if (contextOwner) Operations.clear()
        }
    }

    private fun isEmptyResult(result: Any?): Boolean = when (result) {
        null, Unit -> true
        is Number -> result.toLong() == 0L
        is Boolean -> !result
        is Collection<*> -> result.isEmpty()
        is Map<*, *> -> result.isEmpty()
        is Array<*> -> result.isEmpty()
        is CharSequence -> result.isEmpty()
        else -> false
    }

}
