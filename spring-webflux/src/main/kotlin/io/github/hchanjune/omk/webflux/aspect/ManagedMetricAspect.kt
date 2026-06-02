package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.annotations.ManagedMetric
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webflux.ReactiveOperations
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
        val result = joinPoint.proceed()
        return if (result is Mono<*>) {
            val span = buildSpan(ctx, spanName)
            result.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
        } else {
            val span = buildSpan(ctx, spanName)
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
