package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.webflux.ReactiveOperations
import kotlinx.coroutines.reactor.ReactorContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import reactor.core.publisher.Mono
import kotlin.coroutines.Continuation

abstract class ReactiveAspectSupport {

    protected fun isMono(joinPoint: ProceedingJoinPoint): Boolean =
        Mono::class.java.isAssignableFrom((joinPoint.signature as MethodSignature).method.returnType)

    protected fun getManagedContext(joinPoint: ProceedingJoinPoint): ManagedContext? {
        val continuation = joinPoint.args.lastOrNull() as? Continuation<*> ?: return null
        val reactorCtx = continuation.context[ReactorContext]?.context ?: return null
        return reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)
    }
}
