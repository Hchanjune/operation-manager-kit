package io.github.hchanjune.operationresult.core.providers

import io.github.hchanjune.operationresult.core.models.MetricsContext

/**
 * Records a finalized MetricsContext into an external metrics backend.
 *
 * Core module does not depend on Micrometer or any monitoring system.
 * Implementations live in integration modules, such as:
 *
 * - operationresult-micrometer (MeterRegistry)
 * - custom enterprise monitoring backends
 */
fun interface MetricsRecorder {
    /**
     * Records the given metrics context.
     *
     * This should be called only after the context has been ended
     * and the outcome has been finalized.
     */
    fun record(context: MetricsContext)
}