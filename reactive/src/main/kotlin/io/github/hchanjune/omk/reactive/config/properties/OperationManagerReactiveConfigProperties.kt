package io.github.hchanjune.omk.reactive.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation-manager.reactive")
data class OperationManagerReactiveConfigProperties(
    var contextFilter: ContextFilter = ContextFilter(),
    var contextAspect: Toggle = Toggle(),
    var micrometer: Toggle = Toggle(),
    var asyncPropagation: AsyncPropagation = AsyncPropagation(),
) {
    data class Toggle(
        var enabled: Boolean = true
    )

    data class ContextFilter(
        var enabled: Boolean = true,
        var excludeOptions: Boolean = true
    )

    /**
     * Coroutine scopes opened with `ManagedContextElement` run on a forked context whose spans
     * belong to the fork, not to the parent's span tree. They are only reported when the async
     * hook is enabled — turn it off to keep the forked execution silent (context still propagates,
     * so traceId and entrypoint stay available to logs).
     */
    data class AsyncPropagation(
        var hookEnabled: Boolean = true
    )
}
