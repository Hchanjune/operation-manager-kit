package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.reactive.ReactiveOperations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import reactor.core.publisher.Mono

@Aspect
class ManagedServiceAspect : ReactiveAspectSupport() {

    @Around("@within(managedService)")
    fun aroundService(
        joinPoint: ProceedingJoinPoint,
        managedService: ManagedService
    ): Any? {
        val className = joinPoint.signature.declaringType.simpleName

        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val ctx = reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)
                    ?: return@transformDeferredContextual mono
                if (ctx.service != className) ctx.injectService(className)
                mono
            }
        }

        if (isNullContinuation(joinPoint)) {
            return proceedAsMono(joinPoint).transformDeferredContextual { mono, reactorCtx ->
                val ctx = getManagedContext(reactorCtx) ?: return@transformDeferredContextual mono
                if (ctx.service != className) ctx.injectService(className)
                mono
            }
        }

        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()
        if (ctx.service != className) ctx.injectService(className)
        val result = joinPoint.proceed()
        return if (result is Mono<*>) {
            result.transformDeferredContextual { mono, reactorCtx ->
                val c = getManagedContext(reactorCtx) ?: return@transformDeferredContextual mono
                if (c.service != className) c.injectService(className)
                mono
            }
        } else {
            result
        }
    }
}
