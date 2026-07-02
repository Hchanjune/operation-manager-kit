package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.reactive.ReactiveOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.mono
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import reactor.core.publisher.Mono
import reactor.util.context.ContextView
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

abstract class ReactiveAspectSupport {

    protected fun isMono(joinPoint: ProceedingJoinPoint): Boolean =
        Mono::class.java.isAssignableFrom((joinPoint.signature as MethodSignature).method.returnType)

    // Returns true when Spring called the proxy via method.invoke with null continuation
    // (non-Kotlin path). In this case joinPoint.proceed() fails; we must return a Mono instead.
    protected fun isNullContinuation(joinPoint: ProceedingJoinPoint): Boolean {
        val paramTypes = (joinPoint.signature as MethodSignature).method.parameterTypes
        if (paramTypes.isEmpty()) return false
        return joinPoint.args.lastOrNull() == null &&
            Continuation::class.java.isAssignableFrom(paramTypes.last())
    }

    // When continuation is null, build a real Mono by injecting a real continuation via
    // suspendCoroutineUninterceptedOrReturn so that joinPoint.proceed() gets a valid cont.
    // This avoids CoroutinesUtils.invokeSuspendingFunction which requires getKotlinFunction
    // to succeed on the proxy method (which fails in some ClassLoader configurations).
    protected fun proceedAsMono(joinPoint: ProceedingJoinPoint): Mono<*> {
        val originalArgs = joinPoint.args
        val argsWithoutCont = originalArgs.copyOf(originalArgs.size - 1)
        @Suppress("UNCHECKED_CAST")
        return mono(Dispatchers.Unconfined) {
            suspendCoroutineUninterceptedOrReturn<Any?> { cont ->
                val newArgs = argsWithoutCont.copyOf(originalArgs.size)
                newArgs[argsWithoutCont.size] = cont
                joinPoint.proceed(newArgs)
            }
        } as Mono<*>
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
