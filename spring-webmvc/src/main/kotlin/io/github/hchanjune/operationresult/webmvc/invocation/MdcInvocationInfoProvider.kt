package io.github.hchanjune.operationresult.webmvc.invocation

import io.github.hchanjune.operationresult.core.models.InvocationInfo
import io.github.hchanjune.operationresult.core.providers.InvocationInfoProvider
import io.github.hchanjune.operationresult.webmvc.constants.OperationMdcKeys
import org.slf4j.MDC

/**
 * MDC-based implementation of [InvocationInfoProvider].
 *
 * ## Purpose
 * This provider builds an [InvocationInfo] snapshot by reading invocation metadata
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
 * [InvocationInfoProvider] bean (e.g. for custom MDC keys or richer attributes).
 *
 * ## Notes
 * - This provider is read-only and does not mutate MDC.
 * - MDC is thread-local; async execution requires MDC propagation if consistent values are needed.
 */
class MdcInvocationInfoProvider: InvocationInfoProvider {
    /**
     * Returns the current invocation metadata derived from MDC.
     *
     * @return an [InvocationInfo] populated from MDC keys with safe fallbacks.
     */
    override fun current(): InvocationInfo =
        InvocationInfo(
            entrypoint = MDC.get(OperationMdcKeys.ENTRYPOINT)?: "UnknownEntry",
            service = MDC.get(OperationMdcKeys.SERVICE)?: "UnknownService",
            function = MDC.get(OperationMdcKeys.FUNCTION)?: "UnknownFunction",
            event = MDC.get(OperationMdcKeys.EVENT)?: "UnknownEvent",
            attributes = emptyMap(),
        )
}