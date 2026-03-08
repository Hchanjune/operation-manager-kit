package io.github.hchanjune.omk.core.models.constants

object OperationKeys {
    private const val PREFIX = "OMK."
    const val ENTRYPOINT = "${PREFIX}entrypoint"
    const val SERVICE = "${PREFIX}service"
    const val FUNCTION = "${PREFIX}function"
    const val OPERATION = "${PREFIX}operation"
    const val USE_CASE = "${PREFIX}use_case"
    const val EVENT = "${PREFIX}event"
    const val HTTP_METHOD = "${PREFIX}http_method"
    const val HTTP_URI = "${PREFIX}http_url"
    const val TRACE_ID = "${PREFIX}trace_id"
    const val SPAN_ID = "${PREFIX}span_id"
    const val CAUSATION_ID = "${PREFIX}causation_id"
}