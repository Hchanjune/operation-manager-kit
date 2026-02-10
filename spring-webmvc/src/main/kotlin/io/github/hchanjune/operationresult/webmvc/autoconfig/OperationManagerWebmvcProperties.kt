package io.github.hchanjune.operationresult.webmvc.autoconfig

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation-manager.webmvc")
data class OperationManagerWebmvcProperties(
    var mdcEntrypointInterceptor: Toggle = Toggle(),
    var mdcServiceAspect: Toggle = Toggle(),
) {
    data class Toggle(
        var enabled: Boolean = true
    )
}