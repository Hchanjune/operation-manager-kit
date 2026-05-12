package io.github.hchanjune.omk.testapp.webmvc.integration

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedEventHandlerAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedOperationAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedRepositoryAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedServiceAspect
import io.github.hchanjune.omk.webmvc.async.ManagedContextTaskDecorator
import io.github.hchanjune.omk.webmvc.filter.ManagedContextPersistenceFilter
import io.github.hchanjune.omk.webmvc.hooks.CompositeOperationHook
import io.github.hchanjune.omk.webmvc.provider.W3CTelemetryPropagationProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class AutoConfigurationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    // ── Filter ────────────────────────────────────────────────────────────────

    @Test
    fun `ManagedContextPersistenceFilter bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedContextPersistenceFilter::class.java))
    }

    // ── Hooks ─────────────────────────────────────────────────────────────────

    @Test
    fun `primary OperationHook bean is CompositeOperationHook`() {
        val hook = applicationContext.getBean(OperationHook::class.java)
        assertTrue(hook is CompositeOperationHook)
    }

    // ── Aspects ───────────────────────────────────────────────────────────────

    @Test
    fun `ManagedControllerAspect bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedControllerAspect::class.java))
    }

    @Test
    fun `ManagedOperationAspect bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedOperationAspect::class.java))
    }

    @Test
    fun `ManagedServiceAspect bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedServiceAspect::class.java))
    }

    @Test
    fun `ManagedRepositoryAspect bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedRepositoryAspect::class.java))
    }

    @Test
    fun `ManagedEventHandlerAspect bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedEventHandlerAspect::class.java))
    }

    // ── Providers ─────────────────────────────────────────────────────────────

    @Test
    fun `TraceIdProvider bean is registered`() {
        assertNotNull(applicationContext.getBean(TraceIdProvider::class.java))
    }

    @Test
    fun `CausationIdProvider bean is registered`() {
        assertNotNull(applicationContext.getBean(CausationIdProvider::class.java))
    }

    @Test
    fun `SpanIdProvider bean is registered`() {
        assertNotNull(applicationContext.getBean(SpanIdProvider::class.java))
    }

    @Test
    fun `IssuerProvider bean is registered`() {
        assertNotNull(applicationContext.getBean(IssuerProvider::class.java))
    }

    @Test
    fun `ManagedContextProvider bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedContextProvider::class.java))
    }

    @Test
    fun `TelemetryPropagationProvider is W3CTelemetryPropagationProvider by default`() {
        val provider = applicationContext.getBean(TelemetryPropagationProvider::class.java)
        assertTrue(provider is W3CTelemetryPropagationProvider)
    }

    // ── Async ─────────────────────────────────────────────────────────────────

    @Test
    fun `ManagedContextTaskDecorator bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedContextTaskDecorator::class.java))
    }

    @Test
    fun `ThreadPoolTaskExecutorCustomizer bean is registered`() {
        assertNotNull(applicationContext.getBean(ThreadPoolTaskExecutorCustomizer::class.java))
    }
}
