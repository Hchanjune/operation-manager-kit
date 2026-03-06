package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.context.MetricsContext
import io.github.hchanjune.operationresult.core.providers.metric.MetricsRecorder

/**
 * A no-op implementation of [MetricsRecorder].
 *
 * ## Purpose
 * - Allows the core module to operate without any metrics backend.
 * - Useful for tests or environments where metrics recording is intentionally disabled.
 *
 * Integration modules (e.g., Micrometer) should provide a real implementation
 * that records the finalized [MetricsContext] into an external metrics system.
 */
object NoopMetricsRecorder: MetricsRecorder {
    override fun record(context: MetricsContext) {
        // Intentionally no-op
    }
}