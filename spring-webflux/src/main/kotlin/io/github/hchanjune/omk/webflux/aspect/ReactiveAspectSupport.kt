package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.webflux.ReactiveOperations
import kotlinx.coroutines.reactor.ReactorContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.CoroutinesUtils
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import kotlin.coroutines.Continuation

abstract class ReactiveAspectSupport {

    protected fun isMono(joinPoint: ProceedingJoinPoint): Boolean =
        Mono::class.java.isAssignableFrom((joinPoint.signature as MethodSignature).method.returnType)

    // Returns true when Spring called the proxy via method.invoke with null continuation
    // (non-Kotlin path). In this case joinPoint.proceed() fails; we must return a Mono instead.
    protected fun isNullContinuation(joinPoint: ProceedingJoinPoint): Boolean =
        joinPoint.args.lastOrNull() == null &&
            Continuation::class.java.isAssignableFrom(
                (joinPoint.signature as MethodSignature).method.parameterTypes.last()
            )

    // When continuation is null, invoke the target as a Mono via CoroutinesUtils so the
    // suspension chain remains intact. Callers wrap the returned Mono with instrumentation.
    protected fun proceedAsMono(joinPoint: ProceedingJoinPoint): Mono<*> {
        val sig = joinPoint.signature as MethodSignature
        val method = sig.method
        val target = joinPoint.target
        // Business args are all args except the last (null continuation placeholder)
        val businessArgs = joinPoint.args.dropLast(1).toTypedArray()
        @Suppress("UNCHECKED_CAST")
        return CoroutinesUtils.invokeSuspendingFunction(method, target, *businessArgs) as Mono<*>
    }

    // Read context from target's original continuation when called in normal coroutine path.
    protected fun getManagedContext(joinPoint: ProceedingJoinPoint): ManagedContext? {
        val continuation = joinPoint.args.lastOrNull() as? Continuation<*> ?: return null
        val reactorCtx = continuation.context[ReactorContext]?.context ?: return null
        return reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)
    }

    protected fun getManagedContext(reactorCtx: ContextView): ManagedContext? =
        reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)
}
