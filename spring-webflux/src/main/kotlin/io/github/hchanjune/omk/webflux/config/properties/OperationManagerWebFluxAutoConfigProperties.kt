package io.github.hchanjune.omk.webflux.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation-manager.webflux")
data class OperationManagerWebFluxAutoConfigProperties(
    var contextFilter: Toggle = Toggle(),
    var contextAspect: Toggle = Toggle(),
    var micrometer: Toggle = Toggle(),
) {
    data class Toggle(
        var enabled: Boolean = true
    )
}
