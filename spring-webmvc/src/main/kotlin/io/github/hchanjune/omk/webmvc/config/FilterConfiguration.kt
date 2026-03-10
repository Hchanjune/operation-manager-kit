package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.webmvc.filter.ManagedContextPersistenceFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilterConfiguration {

    @Bean
    fun managedContextPersistenceFilter(
        contextProvider: ManagedContextProvider,
        metricsRecorder: MetricsRecorder,
    ): FilterRegistrationBean<ManagedContextPersistenceFilter> =
        FilterRegistrationBean(ManagedContextPersistenceFilter(contextProvider, metricsRecorder)).apply {
        addUrlPatterns("/*")
        order = 1
    }

}