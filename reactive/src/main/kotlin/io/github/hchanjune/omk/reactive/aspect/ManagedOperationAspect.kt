package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.annotations.ManagedOperation
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
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ManagedOperationAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@annotation(operationAnnotation)")
    fun aroundOperation(
        joinPoint: ProceedingJoinPoint,
        operationAnnotation: ManagedOperation
    ): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val spanName = operationAnnotation.operation.ifBlank {
            "$className.${joinPoint.signature.name.substringBefore('-')}"
        }

        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return instrumentMono(result, operationAnnotation, spanName)
        }

        if (isNullContinuation(joinPoint)) {
            return instrumentMono(proceedAsMono(joinPoint), operationAnnotation, spanName)
        }

        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()
        return instrumentSuspend(joinPoint, ctx, operationAnnotation, spanName)
    }

    private fun instrumentMono(source: Mono<*>, op: ManagedOperation, spanName: String): Mono<*> =
        source.transformDeferredContextual { mono, reactorCtx ->
            val ctx = getManagedContext(reactorCtx)
            if (ctx == null) {
                mono
            } else {
                ctx.injectAnnotationInfo(op.operation, op.useCase)
                val span = buildSpan(ctx, op, spanName)
                mono.doOnSuccess { span.end(); ctx.pop() }.doOnError { e -> span.end(e); ctx.pop() }
            }
        }

    private fun buildSpan(ctx: ManagedContext, op: ManagedOperation, spanName: String) =
        ctx.push(
            name = MetricName(spanName),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder()
                .put("service", ctx.service)
                .put("operation", op.operation)
                .put("use_case", op.useCase)
                .build(),
            descriptor = MetricDescriptor(operation = op.operation, useCase = op.useCase, layer = MetricLayer.APPLICATION),
            idProvider = spanIdProvider
        )

    private fun instrumentSuspend(joinPoint: ProceedingJoinPoint, ctx: ManagedContext, op: ManagedOperation, spanName: String): Any? {
        ctx.injectAnnotationInfo(op.operation, op.useCase)
        val span = buildSpan(ctx, op, spanName)

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
}
