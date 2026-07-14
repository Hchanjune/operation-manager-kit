package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedSchedule
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.SpanSupport
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.support.Results
import io.github.hchanjune.omk.reactive.ReactiveOperations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import reactor.core.publisher.Mono
import reactor.util.context.Context

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedScheduleAspect(
    private val spanIdProvider: SpanIdProvider,
    private val runtime: OperationRuntime? = null,
) : ReactiveAspectSupport() {

    @Around("@annotation(managedSchedule)")
    fun aroundSchedule(joinPoint: ProceedingJoinPoint, managedSchedule: ManagedSchedule): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        // ── Mono return type ──────────────────────────────────────────────────────
        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val contextOwner = !reactorCtx.hasKey(ReactiveOperations.CONTEXT_KEY)
                if (contextOwner) {
                    val newContext = ReactiveOperations.initializeForSchedule(runtime)
                    newContext.injectEntryPoint(className)
                    val span = SpanSupport.pushEntrySpan(newContext, className, methodName, spanIdProvider)
                    mono
                        .doOnSuccess { value ->
                            if (managedSchedule.quietWhenEmpty && Results.isEmpty(value)) newContext.defaultLogging = false
                            span.end(); newContext.pop(); newContext.end(); ReactiveOperations.hookFor(newContext)?.onSuccess(newContext)
                        }
                        .doOnError { e -> span.end(e); newContext.pop(); newContext.end(); ReactiveOperations.hookFor(newContext)?.onFailure(newContext, e) }
                        .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, newContext))
                } else {
                    val ctx = reactorCtx.get<ManagedContext>(ReactiveOperations.CONTEXT_KEY)
                    ctx.injectEntryPoint(className)
                    val span = SpanSupport.pushEntrySpan(ctx, className, methodName, spanIdProvider)
                    mono
                        .doOnSuccess { span.end(); ctx.pop() }
                        .doOnError { e -> span.end(e); ctx.pop() }
                }
            }
        }

        // ── synchronous / blocking target (typical @Scheduled fun) ────────────────
        val newContext = ReactiveOperations.initializeForSchedule(runtime)
        newContext.injectEntryPoint(className)
        val span = SpanSupport.pushEntrySpan(newContext, className, methodName, spanIdProvider)
        eventHandlerContext.set(newContext)
        return try {
            val result = joinPoint.proceed()
            if (managedSchedule.quietWhenEmpty && Results.isEmpty(result)) newContext.defaultLogging = false
            span.end(); newContext.pop(); newContext.end()
            ReactiveOperations.hookFor(newContext)?.onSuccess(newContext)
            result
        } catch (e: Throwable) {
            span.end(e); newContext.pop(); newContext.end()
            ReactiveOperations.hookFor(newContext)?.onFailure(newContext, e)
            throw e
        } finally {
            eventHandlerContext.remove()
        }
    }

}
