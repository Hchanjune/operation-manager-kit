package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.reactive.ReactiveOperations
import io.github.hchanjune.omk.reactive.support.BridgedReactorContext
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

    companion object {
        // Carries ManagedContext across blocking entry-point boundaries (event handler,
        // scheduled task) so that inner reactive spans (e.g. @ManagedRepository inside
        // runBlocking) can find it.
        internal val eventHandlerContext = ThreadLocal<ManagedContext?>()
    }

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
    // Falls back to ThreadLocal so that inner reactive spans fired from a blocking event
    // handler (runBlocking inside handle()) can still find the owning ManagedContext.
    protected fun getManagedContext(joinPoint: ProceedingJoinPoint): ManagedContext? {
        val continuation = joinPoint.args.lastOrNull() as? Continuation<*>
        val fromReactor = continuation?.context?.get(ReactorContext)?.context
            ?.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY)?.orElse(null)
        return fromReactor ?: eventHandlerContext.get()
    }

    protected fun getManagedContext(reactorCtx: ContextView): ManagedContext? =
        reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)

    /**
     * Publishes the span's bridged live context (if any) into the Reactor context, so OTel-
     * instrumented reactive clients running inside this pipeline nest under the OMK span.
     * No bridge active or no instrumentation library on the classpath → no-op.
     */
    protected fun <T : Any> Mono<T>.propagateBridgedContext(span: MetricSpan): Mono<T> =
        BridgedReactorContext.propagate(this, span)
}
