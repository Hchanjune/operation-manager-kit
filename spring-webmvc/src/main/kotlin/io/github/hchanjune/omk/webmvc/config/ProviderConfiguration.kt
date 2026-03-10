package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.aspect.ManagedAnnotationAspect
import io.github.hchanjune.omk.webmvc.provider.OperationCausationIdProvider
import io.github.hchanjune.omk.webmvc.provider.OperationManagedContextProvider
import io.github.hchanjune.omk.webmvc.provider.OperationSpanIdProvider
import io.github.hchanjune.omk.webmvc.provider.OperationTraceIdProvider
import io.github.hchanjune.omk.webmvc.provider.SpringSecurityIssuerProvider
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
internal class ProviderConfiguration {

    /**
     * ###### TraceIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdProvider::class)
    fun traceIdProvider(): TraceIdProvider =
        OperationTraceIdProvider()

    /**
     * ###### CausationIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(CausationIdProvider::class)
    fun causationIdProvider(): CausationIdProvider =
        OperationCausationIdProvider()

    /**
     * ###### SpanIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(SpanIdProvider::class)
    fun spanIdProvider(): SpanIdProvider =
        OperationSpanIdProvider()

    /**
     * ###### IssuerProvider (SpringSecurity Enabled)
     */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun securityIssuerProvider(): IssuerProvider =
        SpringSecurityIssuerProvider()

    /**
     * ###### IssuerProvider (Fallback)
     */
    @Bean
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun fallbackSecurityIssuerProvider(): IssuerProvider =
        IssuerProvider { "anonymous" }

    /**
     * ###### ManagedContextProvider
     */
    @Bean
    fun managedContextProvider(
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        spanIdProvider: SpanIdProvider,
        issuerProvider: IssuerProvider,
        ): ManagedContextProvider =
        OperationManagedContextProvider(
            traceIdProvider = traceIdProvider,
            causationIdProvider = causationIdProvider,
            issuerProvider = issuerProvider,
            spanIdProvider = spanIdProvider,
        )

}