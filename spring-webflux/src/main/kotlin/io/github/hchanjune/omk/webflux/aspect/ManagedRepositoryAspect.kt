package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.context.ManagedContext
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
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class ManagedRepositoryAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@within(managedRepository)")
    fun aroundRepository(
        joinPoint: ProceedingJoinPoint,
        managedRepository: ManagedRepository
    ): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return instrumentMono(result, className, methodName)
        }

        if (isNullContinuation(joinPoint)) {
            return instrumentMono(proceedAsMono(joinPoint), className, methodName)
        }

        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()
        val span = buildSpan(ctx, className, methodName)

        // proceed() itself can throw synchronously (before any Mono even exists) — the span must
        // be pushed before this call so a sync throw here still ends/pops it instead of leaking.
        val result = try {
            joinPoint.proceed()
        } catch (e: Throwable) {
            span.end(e); ctx.pop(); throw e
        }

        return if (result is Mono<*>) {
            result.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
        } else {
            span.end(); ctx.pop(); result
        }
    }

    private fun instrumentMono(source: Mono<*>, className: String, methodName: String): Mono<*> =
        source.transformDeferredContextual { mono, reactorCtx ->
            val ctx = getManagedContext(reactorCtx) ?: return@transformDeferredContextual mono
            val span = buildSpan(ctx, className, methodName)
            mono.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
        }

    private fun buildSpan(ctx: ManagedContext, className: String, methodName: String) =
        ctx.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder()
                .put("repository", className)
                .put("method", methodName)
                .put("operation", ctx.operation)
                .build(),
            descriptor = MetricDescriptor(operation = ctx.operation, useCase = ctx.useCase, layer = MetricLayer.DB),
            idProvider = spanIdProvider
        )
}
