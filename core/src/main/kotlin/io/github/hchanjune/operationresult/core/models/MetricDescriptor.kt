package io.github.hchanjune.operationresult.core.models

/**
 * MetricDescriptor represents the observable descriptions of invocations.
 *
 * ## Purpose
 * Minimal Invocation infos to support classify matrics
 *
 * ## Limitations
 * These elements are added as tags in metric, so must avoid the high cardinality value
 *
 * DO NOT store high-cardinality exception messages or stack traces here.
 */
data class MetricDescriptor(
    val operation: String = "",
    val useCase: String = "",
    val event: String = "",
)