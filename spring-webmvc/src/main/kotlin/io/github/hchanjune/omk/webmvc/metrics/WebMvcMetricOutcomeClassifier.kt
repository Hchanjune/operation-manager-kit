package io.github.hchanjune.omk.webmvc.metrics

import io.github.hchanjune.omk.core.models.metric.MetricOutcome
import io.github.hchanjune.omk.core.providers.metric.MetricOutcomeClassifier
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

/**
 * WebMVC-oriented outcome classifier.
 *
 * If statusCode is not provided (core executor passes null),
 * we still classify common MVC validation/binding issues as REJECT (4xx bucket).
 */
class WebMvcMetricOutcomeClassifier : MetricOutcomeClassifier {

    override fun classify(statusCode: Int?, error: Throwable?): MetricOutcome {
        val code = statusCode ?: (error as? ResponseStatusException)?.statusCode?.value()
        val group = code?.let { toStatusGroup(it) }

        val result = when {
            group == MetricOutcome.StatusGroup.S4XX || isBindingError(error) -> MetricOutcome.Result.REJECT
            group == MetricOutcome.StatusGroup.S5XX -> MetricOutcome.Result.FAILURE
            error != null && group != MetricOutcome.StatusGroup.S2XX && group != MetricOutcome.StatusGroup.S3XX -> MetricOutcome.Result.FAILURE
            else -> MetricOutcome.Result.SUCCESS
        }

        return MetricOutcome(
            result = result,
            statusGroup = group,
            exception = error?.javaClass?.simpleName ?: "none"
        )
    }

    private fun isBindingError(error: Throwable?): Boolean =
        error is MethodArgumentNotValidException ||
                error is MissingServletRequestParameterException ||
                error is MethodArgumentTypeMismatchException

    private fun toStatusGroup(code: Int): MetricOutcome.StatusGroup? =
        when (code) {
            in 200..299 -> MetricOutcome.StatusGroup.S2XX
            in 300..399 -> MetricOutcome.StatusGroup.S3XX
            in 400..499 -> MetricOutcome.StatusGroup.S4XX
            in 500..599 -> MetricOutcome.StatusGroup.S5XX
            else -> null
        }
}