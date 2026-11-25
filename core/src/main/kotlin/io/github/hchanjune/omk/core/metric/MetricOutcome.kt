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
                // Cancellation first — a cancelled call is neither side's fault.
                // 취소가 먼저 — 취소된 호출은 어느 쪽 귀책도 아니다.
                exception is CancellationException -> MetricStatus.CANCELLED
                // The exception's own answer beats the type heuristic below.
                // 예외가 스스로 밝힌 귀책이 아래 타입 추측보다 우선한다.
                exception is FaultAttributable ->
                    if (exception.isClientFault) MetricStatus.FAILURE_CLIENT else MetricStatus.FAILURE_SERVER
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