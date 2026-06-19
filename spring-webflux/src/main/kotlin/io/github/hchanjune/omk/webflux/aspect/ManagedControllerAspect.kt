package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import reactor.core.publisher.Mono

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedControllerAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@within(managedController)")
    fun aroundController(
        joinPoint: ProceedingJoinPoint,
        managedController: ManagedController
    ): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')
        // Path 1: Mono-returning method
        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return instrumentMono(result, className, methodName)
        }

        // Path 2: suspend fun called via WebFlux non-Kotlin path (null continuation)
        // Spring invokes proxy.ok(null) → CGLIB args=[null] → joinPoint.proceed() would fail.
        // Return a properly-instrumented Mono; Spring's processReturnType converts it back.
        if (isNullContinuation(joinPoint)) {
            return instrumentMono(proceedAsMono(joinPoint), className, methodName)
        }

        // Path 3: suspend fun called directly with valid continuation (e.g., from another coroutine)
        // joinPoint.proceed() may return a Mono (when Spring's invokeSuspendingFunction is called
        // internally), so we check the result type and apply deferred instrumentation accordingly.
        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()

        if (ctx.entrypoint != className) ctx.injectEntryPoint(className)
        val span = ctx.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )

        // proceed() itself can throw synchronously (before any Mono even exists) — the span must
        // be pushed before this call so a sync throw here still ends/pops it and is recorded.
        val result = try {
            joinPoint.proceed()
        } catch (e: Throwable) {
            span.end(e); ctx.pop(); ctx.recordException(e); throw e
        }

        return if (result is Mono<*>) {
            // proceed() returned a Mono — the span must be ended inside the reactive chain
            result.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop(); ctx.recordException(e) }
        } else {
            span.end(); ctx.pop(); result
        }
    }

    /**
     * Records the exception here because this is the last point where the real one is still
     * visible: a @RestControllerAdvice further downstream in the same reactive chain will recover
     * from it via onErrorResume before it ever reaches ManagedContextWebFilter.
     */
    private fun instrumentMono(source: Mono<*>, className: String, methodName: String): Mono<*> =
        source.transformDeferredContextual { mono, reactorCtx ->
            val ctx = getManagedContext(reactorCtx)
                ?: return@transformDeferredContextual mono
            if (ctx.entrypoint != className) ctx.injectEntryPoint(className)
            val span = ctx.push(
                name = MetricName("$className.$methodName"),
                kind = MetricKind.TIMER,
                policy = MetricPolicy.defaults(),
                tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                idProvider = spanIdProvider
            )
            mono.doOnSuccess { span.end(); ctx.pop() }
                .doOnError { e -> span.end(e); ctx.pop(); ctx.recordException(e) }
        }
}
