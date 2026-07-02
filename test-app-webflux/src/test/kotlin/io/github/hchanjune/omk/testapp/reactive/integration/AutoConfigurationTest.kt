package io.github.hchanjune.omk.testapp.reactive.integration

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedEventHandlerAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedOperationAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedRepositoryAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedServiceAspect
import io.github.hchanjune.omk.reactive.filter.ManagedContextWebFilter
import io.github.hchanjune.omk.reactive.hooks.CompositeOperationHook
import io.github.hchanjune.omk.reactive.provider.W3CTelemetryPropagationProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
    fun `ManagedContextWebFilter bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedContextWebFilter::class.java))
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
    fun `ManagedContextProvider bean is registered`() {
        assertNotNull(applicationContext.getBean(ManagedContextProvider::class.java))
    }

    @Test
    fun `TelemetryPropagationProvider is W3CTelemetryPropagationProvider by default`() {
        val provider = applicationContext.getBean(TelemetryPropagationProvider::class.java)
        assertTrue(provider is W3CTelemetryPropagationProvider)
    }
}
