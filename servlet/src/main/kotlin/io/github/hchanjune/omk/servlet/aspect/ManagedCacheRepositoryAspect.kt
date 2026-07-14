package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedCacheRepository
import io.github.hchanjune.omk.core.metric.SpanSupport
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

        val span = SpanSupport.pushCacheSpan(context, className, methodName, spanIdProvider)

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
