package io.github.hchanjune.omk.reactive.hooks

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import org.springframework.core.annotation.Order

@Order(60)
class MetricsOperationHook(
    private val metricsRecorder: MetricsRecorder
) : OperationHook {

    override fun onSuccess(context: ManagedContext) = recordSpans(context)
    override fun onFailure(context: ManagedContext, exception: Throwable) = recordSpans(context)

    private fun recordSpans(context: ManagedContext) {
        if (context.isMetricsRecorded()) return
        val root = context.rootSpan ?: return
        traverse(root)
        context.markMetricsRecorded()
    }

    private fun traverse(span: MetricSpan) {
        metricsRecorder.record(span)
        span.children.forEach { traverse(it) }
    }
}
