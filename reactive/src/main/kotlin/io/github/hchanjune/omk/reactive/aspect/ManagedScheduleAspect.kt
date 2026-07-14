package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
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
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@annotation(io.github.hchanjune.omk.core.annotations.ManagedSchedule)")
    fun aroundSchedule(joinPoint: ProceedingJoinPoint): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        // ── Mono return type ──────────────────────────────────────────────────────
        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val contextOwner = !reactorCtx.hasKey(ReactiveOperations.CONTEXT_KEY)
                if (contextOwner) {
                    val newContext = ReactiveOperations.initializeForSchedule()
                    newContext.injectEntryPoint(className)
                    val span = newContext.push(
                        name = MetricName("$className.$methodName"),
                        kind = MetricKind.TIMER,
                        policy = MetricPolicy.defaults(),
                        tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                        descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                        idProvider = spanIdProvider
                    )
                    mono
                        .doOnSuccess { span.end(); newContext.pop(); newContext.end(); ReactiveOperations.hook?.onSuccess(newContext) }
                        .doOnError { e -> span.end(e); newContext.pop(); newContext.end(); ReactiveOperations.hook?.onFailure(newContext, e) }
                        .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, newContext))
                } else {
                    val ctx = reactorCtx.get<ManagedContext>(ReactiveOperations.CONTEXT_KEY)
                    ctx.injectEntryPoint(className)
                    val span = ctx.push(
                        name = MetricName("$className.$methodName"),
                        kind = MetricKind.TIMER,
                        policy = MetricPolicy.defaults(),
                        tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                        descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                        idProvider = spanIdProvider
                    )
                    mono
                        .doOnSuccess { span.end(); ctx.pop() }
                        .doOnError { e -> span.end(e); ctx.pop() }
                }
            }
        }

        // ── synchronous / blocking target (typical @Scheduled fun) ────────────────
        val newContext = ReactiveOperations.initializeForSchedule()
        newContext.injectEntryPoint(className)
        val span = newContext.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )
        eventHandlerContext.set(newContext)
        return try {
            val result = joinPoint.proceed()
            span.end(); newContext.pop(); newContext.end()
            ReactiveOperations.hook?.onSuccess(newContext)
            result
        } catch (e: Throwable) {
            span.end(e); newContext.pop(); newContext.end()
            ReactiveOperations.hook?.onFailure(newContext, e)
            throw e
        } finally {
            eventHandlerContext.remove()
        }
    }
}
