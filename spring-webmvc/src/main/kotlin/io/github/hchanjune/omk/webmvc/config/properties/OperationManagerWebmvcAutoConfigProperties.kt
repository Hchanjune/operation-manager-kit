package io.github.hchanjune.omk.webmvc.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation-manager.webmvc")
data class OperationManagerWebmvcAutoConfigProperties(
    var contextFilter: ContextFilter = ContextFilter(),
    var contextAspect: Toggle = Toggle(),
    var micrometer: Toggle = Toggle(),
    var asyncPropagation: AsyncPropagation = AsyncPropagation(),
    var exceptionCapture: Toggle = Toggle(),
) {
    data class Toggle(
        var enabled: Boolean = true
    )

    data class ContextFilter(
        var enabled: Boolean = true,
        var excludeOptions: Boolean = true
    )

    data class AsyncPropagation(
        var enabled: Boolean = true,
        var hookEnabled: Boolean = false
    )
}