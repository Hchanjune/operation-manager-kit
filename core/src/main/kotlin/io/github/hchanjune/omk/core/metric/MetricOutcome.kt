package io.github.hchanjune.omk.core.metric

import java.util.concurrent.CancellationException

data class MetricOutcome(
    val status: MetricStatus,
    val errorType: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success() = MetricOutcome(MetricStatus.SUCCESS)
        fun fail(exception: Throwable): MetricOutcome {
            val status = when {
                exception is CancellationException -> MetricStatus.CANCELLED
                exception is IllegalArgumentException || exception is IllegalStateException -> MetricStatus.FAILURE_CLIENT
                else -> MetricStatus.FAILURE_SERVER
            }
            return MetricOutcome(
                status = status,
                errorType = exception::class.simpleName?: "Unknown Exception",
                errorMessage = exception.message
            )
        }
    }
}