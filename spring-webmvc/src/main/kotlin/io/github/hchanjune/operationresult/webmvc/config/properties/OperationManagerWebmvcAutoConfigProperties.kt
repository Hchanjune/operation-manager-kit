package io.github.hchanjune.operationresult.webmvc.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation-manager.webmvc")
data class OperationManagerWebmvcAutoConfigProperties(
    var mdcEntrypointInterceptor: Toggle = Toggle(),
    var mdcServiceAspect: Toggle = Toggle(),
    var micrometer: Toggle = Toggle(),
) {
    data class Toggle(
        var enabled: Boolean = true
    )
}