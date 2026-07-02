package io.github.hchanjune.omk.reactive.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation-manager.reactive")
data class OperationManagerReactiveConfigProperties(
    var contextFilter: ContextFilter = ContextFilter(),
    var contextAspect: Toggle = Toggle(),
    var micrometer: Toggle = Toggle(),
) {
    data class Toggle(
        var enabled: Boolean = true
    )

    data class ContextFilter(
        var enabled: Boolean = true,
        var excludeOptions: Boolean = true
    )
}
