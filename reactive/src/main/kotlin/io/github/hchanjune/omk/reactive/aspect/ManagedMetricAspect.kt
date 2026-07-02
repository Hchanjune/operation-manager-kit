package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.annotations.ManagedMetric
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
import reactor.core.publisher.Mono

@Aspect
class ManagedMetricAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@annotation(managedMetric)")
    fun aroundMetric(
        joinPoint: ProceedingJoinPoint,
        managedMetric: ManagedMetric
    ): Any? {
        val spanName = managedMetric.name.ifBlank {
            "${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name.substringBefore('-')}"
        }

        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return instrumentMono(result, spanName)
        }

        if (isNullContinuation(joinPoint)) {
            return instrumentMono(proceedAsMono(joinPoint), spanName)
        }

        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()
        val span = buildSpan(ctx, spanName)

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

    private fun instrumentMono(source: Mono<*>, spanName: String): Mono<*> =
        source.transformDeferredContextual { mono, reactorCtx ->
            val ctx = getManagedContext(reactorCtx) ?: return@transformDeferredContextual mono
            val span = buildSpan(ctx, spanName)
            mono.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
        }

    private fun buildSpan(ctx: ManagedContext, spanName: String) =
        ctx.push(
            name = MetricName(spanName),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder()
                .put("service", ctx.service)
                .put("operation", ctx.operation)
                .put("span", spanName)
                .build(),
            descriptor = MetricDescriptor(operation = ctx.operation, useCase = ctx.useCase, layer = MetricLayer.APPLICATION),
            idProvider = spanIdProvider
        )
}
