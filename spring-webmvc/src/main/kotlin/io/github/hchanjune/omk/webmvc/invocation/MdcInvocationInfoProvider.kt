package io.github.hchanjune.omk.webmvc.invocation

import io.github.hchanjune.omk.core.models.invocation.InvocationInfo
import io.github.hchanjune.omk.core.providers.invocation.InvocationInfoProvider
import io.github.hchanjune.omk.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.omk.core.models.constants.OperationKeys
import org.slf4j.MDC

/**
 * MDC-based implementation of [io.github.hchanjune.omk.core.providers.invocation.InvocationInfoProvider].
 *
 * ## Purpose
 * This provider builds an [io.github.hchanjune.omk.core.models.invocation.InvocationInfo] snapshot by reading invocation metadata
 * from SLF4J MDC. It is primarily used in Spring MVC environments where interceptors
 * and aspects enrich MDC during request handling.
 *
 * ## Expected MDC keys
 * - `entrypoint`: controller entrypoint (set by [MdcEntrypointInterceptor])
 * - `service`: service class name (set by [OperationServiceAspect])
 * - `function`: method name (set by [OperationServiceAspect])
 * - `event`: optional event classification (may be absent)
 *
 * ## Fallback behavior
 * If a key is missing, a safe `"Unknown*"` default is used to keep invocation
 * metadata stable even when no MDC integration is present.
 *
 * ## Customization
 * Applications may override this provider by defining their own
 * [io.github.hchanjune.omk.core.providers.invocation.InvocationInfoProvider] bean (e.g. for custom MDC keys or richer attributes).
 *
 * ## Notes
 * - This provider is read-only and does not mutate MDC.
 * - MDC is thread-local; async execution requires MDC propagation if consistent values are needed.
 */
class MdcInvocationInfoProvider(
    private val telemetryProvider: TelemetryContextProvider
): InvocationInfoProvider {
    /**
     * Returns the current invocation metadata derived from MDC.
     *
     * @return an [io.github.hchanjune.omk.core.models.invocation.InvocationInfo] populated from MDC keys with safe fallbacks.
     */
    override fun current(): InvocationInfo {
        val telemetry = telemetryProvider.current()
        return InvocationInfo(
            entrypoint = MDC.get(OperationKeys.ENTRYPOINT) ?: "UnknownEntry",
            service = MDC.get(OperationKeys.SERVICE) ?: "UnknownService",
            function = MDC.get(OperationKeys.FUNCTION) ?: "UnknownFunction",
            operation = MDC.get(OperationKeys.OPERATION) ?: "UnknownOperation",
            useCase = MDC.get(OperationKeys.USE_CASE) ?: "UnknownCase",
            event = MDC.get(OperationKeys.EVENT) ?: "UnknownEvent",
            attributes = mapOf(
                "HTTP_METHOD" to (MDC.get(OperationKeys.HTTP_METHOD) ?: "UnknownMethod"),
                "HTTP_URI" to (MDC.get(OperationKeys.HTTP_URI) ?: "UnknownURI"),
                "TRACE_ID" to telemetry.traceId.ifBlank { "none" },
                "SPAN_ID" to telemetry.spanId.ifBlank { "none" },
                "CAUSATION_ID" to telemetry.causationId.ifBlank { "none" },
            )
        )
    }
}