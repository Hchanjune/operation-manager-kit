package io.github.hchanjune.omk.core.metric

enum class MetricStatus {
    SUCCESS,
    FAILURE_CLIENT,
    FAILURE_SERVER,
    PARTIAL_SUCCESS,
    CANCELLED,
    UNKNOWN
}