package io.github.hchanjune.operationresult.webmvc.config

import io.github.hchanjune.operationresult.core.defaults.DefaultCorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.invocation.CorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.invocation.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.invocation.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.operationresult.webmvc.aop.OperationServiceAspect
import io.github.hchanjune.operationresult.webmvc.interceptor.MdcEntrypointInterceptor
import io.github.hchanjune.operationresult.webmvc.invocation.MdcInvocationInfoProvider
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class InvocationConfiguration {

    /**
     * ###### CorrelationIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(CorrelationIdProvider::class)
    fun correlationIdProvider(): CorrelationIdProvider =
        DefaultCorrelationIdProvider

    /**
     * ###### OperationServiceAspect
     */
    @Bean
    @ConditionalOnClass(Aspect::class)
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-service-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean
    fun operationServiceAspect(): OperationServiceAspect = OperationServiceAspect()

    /**
     * ###### IssuerProvider (SpringSecurity Enabled)
     */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun securityIssuerProvider(): IssuerProvider {
        return try {
            val holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder")
            val getContextMethod = holderClass.getMethod("getContext")

            IssuerProvider {
                try {
                    val context = getContextMethod.invoke(null)
                    val auth = context?.javaClass?.getMethod("getAuthentication")?.invoke(context)
                    val name = auth?.javaClass?.getMethod("getName")?.invoke(auth) as? String
                    name ?: "anonymous"
                } catch (_: Throwable) {
                    "anonymous"
                }
            }
        } catch (_: Throwable) {
            IssuerProvider { "anonymous" }
        }
    }

    /**
     * ###### IssuerProvider (Fallback)
     */
    @Bean
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun fallbackSecurityIssuerProvider(): IssuerProvider = IssuerProvider { "anonymous" }

    /**
     * ###### EntryPointInterceptor Register
     * Interceptor for capturing Http status (Using MDC)
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-entrypoint-interceptor",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(name = ["operationWebMvcConfigurer"])
    fun operationWebMvcConfigurer(
        telemetryContextProvider: TelemetryContextProvider,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(MdcEntrypointInterceptor(telemetryContextProvider))
        }
    }

    /**
     * ###### InvocationInfoProvider
     */
    @Bean
    @ConditionalOnMissingBean(InvocationInfoProvider::class)
    fun invocationInfoProvider(
        telemetryContextProvider: TelemetryContextProvider
    ): InvocationInfoProvider =
        MdcInvocationInfoProvider(telemetryContextProvider)



}