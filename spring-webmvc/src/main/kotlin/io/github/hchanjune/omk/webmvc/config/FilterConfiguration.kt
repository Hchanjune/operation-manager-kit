package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.webmvc.filter.ManagedContextPersistenceFilter
import jakarta.servlet.DispatcherType
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
class FilterConfiguration {

    @Bean
    fun managedContextPersistenceFilter(
        contextProvider: ManagedContextProvider,
        metricsRecorder: MetricsRecorder,
        compositeHook: OperationHook
    ): FilterRegistrationBean<ManagedContextPersistenceFilter> =
        FilterRegistrationBean(
            ManagedContextPersistenceFilter(
                contextProvider = contextProvider,
                metricsRecorder = metricsRecorder,
                compositeHook = compositeHook
            )
        ).apply {
            setName("managedContextPersistenceFilter")
            addUrlPatterns("/*")
            setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR)
            order = -90
    }

}