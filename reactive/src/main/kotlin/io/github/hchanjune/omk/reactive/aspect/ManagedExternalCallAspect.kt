package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.annotations.ManagedExternalCall
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import reactor.core.publisher.Mono

@Aspect
class ManagedExternalCallAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@annotation(managedExternalCall)")
    fun aroundExternalCall(
        joinPoint: ProceedingJoinPoint,
        managedExternalCall: ManagedExternalCall
    ): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')
        val destination = managedExternalCall.destination.ifBlank { className }
        val route = managedExternalCall.route

        // A deferred call must be timed on completion, not on method return, or the span reports
        // assembly time instead of the round trip.
        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return instrumentMono(result, className, methodName, destination, route)
        }

        if (isNullContinuation(joinPoint)) {
            return instrumentMono(proceedAsMono(joinPoint), className, methodName, destination, route)
        }

        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()
        val span = buildSpan(ctx, className, methodName, destination, route)

        // proceed() itself can throw synchronously (before any Mono even exists) — the span must
        // be pushed before this call so a sync throw here still ends/pops it instead of leaking.
        val result = try {
            joinPoint.proceed()
        } catch (e: Throwable) {
            span.end(e); ctx.pop(); throw e
        }

        return if (result is Mono<*>) {
            result.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
                .propagateBridgedContext(span)
        } else {
            span.end(); ctx.pop(); result
        }
    }

    private fun instrumentMono(
        source: Mono<*>,
        className: String,
        methodName: String,
        destination: String,
        route: String
    ): Mono<*> =
        source.transformDeferredContextual { mono, reactorCtx ->
            val ctx = getManagedContext(reactorCtx) ?: return@transformDeferredContextual mono
            val span = buildSpan(ctx, className, methodName, destination, route)
            mono.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
                .propagateBridgedContext(span)
        }

    private fun buildSpan(
        ctx: ManagedContext,
        className: String,
        methodName: String,
        destination: String,
        route: String
    ) = SpanSupport.pushExternalSpan(ctx, className, methodName, destination, route, spanIdProvider)
}
