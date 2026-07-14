package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.event.EventMetadataExtractor
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
class ManagedEventHandlerAspect(
    private val spanIdProvider: SpanIdProvider,
    private val runtime: OperationRuntime? = null,
) {

    @Around("@annotation(managedEventHandler)")
    fun aroundEventHandler(
        joinPoint: ProceedingJoinPoint,
        managedEventHandler: ManagedEventHandler
    ): Any? {
        val contextOwner = !Operations.hasContext

        if (contextOwner) {
            Operations.initializeForEvent(EventMetadataExtractor.extract(joinPoint.args), runtime)
        }

        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        context.injectEntryPoint(className)

        val span = SpanSupport.pushEntrySpan(context, className, methodName, spanIdProvider)

        return try {
            val result = joinPoint.proceed()
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

}
