package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.models.MetricOutcome
import io.github.hchanjune.operationresult.core.providers.MetricOutcomeClassifier
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
class WebMvcMetricOutcomeClassifier: MetricOutcomeClassifier {

    override fun classify(statusCode: Int?, error: Throwable?): MetricOutcome {
        // 1) If HTTP status code is explicitly provided, use it.
        if (statusCode != null) {
            val group = toStatusGroup(statusCode)
            val result = when (group) {
                MetricOutcome.StatusGroup.S4XX -> MetricOutcome.Result.REJECT
                MetricOutcome.StatusGroup.S5XX -> MetricOutcome.Result.FAILURE
                MetricOutcome.StatusGroup.S2XX,
                MetricOutcome.StatusGroup.S3XX -> MetricOutcome.Result.SUCCESS
                null -> if (error == null) MetricOutcome.Result.SUCCESS else MetricOutcome.Result.FAILURE
            }
            return MetricOutcome(result, group, error?.javaClass?.simpleName)
        }

        // 2) Otherwise, classify by exception type (WebMVC signal).
        val (result, group) = when (error) {
            null -> MetricOutcome.Result.SUCCESS to MetricOutcome.StatusGroup.S2XX

            is ResponseStatusException -> {
                val g = toStatusGroup(error.statusCode.value())
                val r = when (g) {
                    MetricOutcome.StatusGroup.S4XX -> MetricOutcome.Result.REJECT
                    MetricOutcome.StatusGroup.S5XX -> MetricOutcome.Result.FAILURE
                    else -> MetricOutcome.Result.FAILURE
                }
                r to g
            }

            // Typical client-side/binding/validation errors
            is MethodArgumentNotValidException,
            is MissingServletRequestParameterException,
            is MethodArgumentTypeMismatchException -> MetricOutcome.Result.REJECT to MetricOutcome.StatusGroup.S4XX

            else -> MetricOutcome.Result.FAILURE to null
        }

        return MetricOutcome(
            result = result,
            statusGroup = group,
            exception = error?.javaClass?.simpleName?: "Exception"
        )
    }

    private fun toStatusGroup(code: Int): MetricOutcome.StatusGroup? =
        when (code) {
            in 200..299 -> MetricOutcome.StatusGroup.S2XX
            in 300..399 -> MetricOutcome.StatusGroup.S3XX
            in 400..499 -> MetricOutcome.StatusGroup.S4XX
            in 500..599 -> MetricOutcome.StatusGroup.S5XX
            else -> null
        }
}