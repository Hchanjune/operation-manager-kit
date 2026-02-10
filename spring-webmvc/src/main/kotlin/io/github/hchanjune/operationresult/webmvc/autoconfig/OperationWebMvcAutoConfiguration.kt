package io.github.hchanjune.operationresult.webmvc.autoconfig

import io.github.hchanjune.operationresult.core.Operations
import io.github.hchanjune.operationresult.core.OperationExecutor
import io.github.hchanjune.operationresult.core.defaults.DefaultCorrelationIdProvider
import io.github.hchanjune.operationresult.core.defaults.NoopOperationHooks
import io.github.hchanjune.operationresult.core.providers.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.OperationHooks
import io.github.hchanjune.operationresult.webmvc.aop.OperationServiceAspect
import io.github.hchanjune.operationresult.webmvc.interceptor.MdcEntrypointInterceptor
import io.github.hchanjune.operationresult.webmvc.invocation.MdcInvocationInfoProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring Boot auto-configuration for Spring MVC integration.
 *
 * ## Purpose
 * This auto-configuration integrates the core operation execution engine with
 * Spring MVC by providing:
 *
 * - MDC enrichment for controller entrypoints (via MVC interceptor)
 * - MDC enrichment for service/function (via Spring AOP aspect; opt-in by annotation)
 * - An MDC-backed [InvocationInfoProvider]
 * - Default hooks and issuer providers when missing
 * - An [OperationExecutor] wired with the resolved providers
 * - Global installation of the executor into [Operations]
 *
 * ## Activation
 * Enabled only when Spring MVC is present on the classpath
 * (`org.springframework.web.servlet.DispatcherServlet`).
 *
 * ## User overrides
 * The following beans can be overridden by the application:
 * - [InvocationInfoProvider]
 * - [OperationHooks]
 * - [IssuerProvider]
 *
 * For MVC integration components:
 * - Provide your own [MdcEntrypointInterceptor] bean to replace the default interceptor
 * - Provide your own bean named `operationWebMvcConfigurer` to fully control interceptor registration
 * - Provide your own [OperationServiceAspect] bean to replace the default aspect
 *
 * ## Configuration properties
 * - `operationresult.webmvc.mdc-entrypoint-interceptor.enabled` (default: true)
 *   Enables registration of the MVC interceptor that writes `entrypoint` into MDC.
 *
 * - `operationresult.webmvc.mdc-service-aspect.enabled` (default: true)
 *   Enables registration of the Spring AOP aspect that writes `service` and `function` into MDC
 *   for `@OperationManaged`-annotated execution points.
 *
 * ## Notes
 * - The security-backed [IssuerProvider] is resolved via reflection to avoid a hard dependency
 *   on Spring Security classes.
 * - MDC is thread-local; async execution may require MDC propagation if consistent context is needed.
 */
@AutoConfiguration
@EnableConfigurationProperties(OperationManagerWebmvcProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
class OperationWebMvcAutoConfiguration {

    /**
     * Default MVC interceptor that writes the resolved controller entrypoint into MDC.
     *
     * Controlled by:
     * - `operation-manager.webmvc.mdc-entrypoint-interceptor.enabled` (default: true)
     *
     * Applications can replace this by defining their own [MdcEntrypointInterceptor] bean.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-entrypoint-interceptor",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean
    fun operationEntryInterceptor(): MdcEntrypointInterceptor =
        MdcEntrypointInterceptor()

    /**
     * Registers the [MdcEntrypointInterceptor] into Spring MVC's interceptor chain.
     *
     * This bean is conditionally created only when the interceptor itself is enabled.
     *
     * Applications may take full control over interceptor registration by providing
     * a bean named `operationWebMvcConfigurer`.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-entrypoint-interceptor",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(name = ["operationWebMvcConfigurer"])
    fun operationWebMvcConfigurer(interceptor: MdcEntrypointInterceptor): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(interceptor)
            }
        }

    /**
     * Aspect that writes `service` and `function` into MDC for operation-managed execution points.
     *
     * Controlled by:
     * - `operation-manager.webmvc.mdc-service-aspect.enabled` (default: true)
     *
     * This bean is created only when Spring AOP infrastructure is present.
     * (Typically provided by adding `spring-boot-starter-aop`.)
     *
     * Applications can replace this by defining their own [OperationServiceAspect] bean.
     */
    @Bean
    @ConditionalOnClass(
        name = [
            "org.aspectj.lang.annotation.Aspect",
            "org.springframework.aop.framework.autoproxy.AutoProxyRegistrar",
            "org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator"
        ]
    )
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-service-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean
    fun operationServiceAspect(): OperationServiceAspect = OperationServiceAspect()


    /**
     * MDC-backed [InvocationInfoProvider].
     *
     * Reads keys such as `entrypoint`, `service`, and `function` from MDC and builds [InvocationInfo].
     *
     * Applications may override this provider by defining their own [InvocationInfoProvider] bean.
     */
    @Bean
    @ConditionalOnMissingBean(InvocationInfoProvider::class)
    fun invocationInfoProvider(): InvocationInfoProvider = MdcInvocationInfoProvider()

    /**
     * Default no-op [OperationHooks] implementation.
     *
     * Applications may override hooks by defining a custom [OperationHooks] bean.
     */
    @Bean
    @ConditionalOnMissingBean(OperationHooks::class)
    fun operationHooks(): OperationHooks = NoopOperationHooks

    /**
     * [IssuerProvider] implementation backed by Spring Security authentication context.
     *
     * This bean is only created when Spring Security is available on the classpath.
     * Reflection is used to avoid direct class references and keep Spring Security as an optional dependency.
     *
     * Applications may override issuer resolution by defining their own [IssuerProvider] bean.
     */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun securityIssuerProvider(): IssuerProvider = IssuerProvider {
        try {
            val holder = Class.forName("org.springframework.security.core.context.SecurityContextHolder")
            val context = holder.getMethod("getContext").invoke(null)
            val auth = context.javaClass.getMethod("getAuthentication").invoke(context)
            val name = auth?.javaClass?.getMethod("getName")?.invoke(auth) as? String
            name ?: "anonymous"
        } catch (_: Throwable) {
            "anonymous"
        }
    }

    /**
     * Fallback [IssuerProvider] used when no other issuer provider is configured.
     *
     * Returns a constant `"anonymous"` issuer.
     */
    @Bean
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun fallbackIssuerProvider(): IssuerProvider = IssuerProvider { "anonymous" }

    /**
     * Creates the core [OperationExecutor] used by this application.
     *
     * This executor is wired with the resolved providers and hooks:
     * - [InvocationInfoProvider]
     * - [IssuerProvider]
     * - [DefaultCorrelationIdProvider]
     * - [OperationHooks]
     *
     * Note: this method does not reference Spring Security types directly.
     */
    @Bean
    fun operationExecutor(
        invocationInfoProvider: InvocationInfoProvider,
        issuerProvider: IssuerProvider,
        hooks: OperationHooks,
    ): OperationExecutor =
        OperationExecutor(
            invocationInfoProvider = invocationInfoProvider,
            issuerProvider = issuerProvider,
            correlationIdProvider = DefaultCorrelationIdProvider,
            hooks = hooks,
        )

    /**
     * Installs the configured [OperationExecutor] into the global [Operations] entrypoint.
     *
     * This bean exists solely to trigger initialization during application startup.
     */
    @Bean
    fun operationInitializer(executor: OperationExecutor): Any {
        Operations.configure(executor)
        return Any()
    }
}
