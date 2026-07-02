package io.github.hchanjune.omk.servlet.provider

import io.github.hchanjune.omk.servlet.config.properties.TelemetryConfigureProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryProviderTest {

    // ── W3CTelemetryPropagationProvider ───────────────────────────────────────

    private val w3c = W3CTelemetryPropagationProvider()

    @Test
    fun `w3c extractTraceId returns null when header absent`() {
        assertNull(w3c.extractTraceId { null })
    }

    @Test
    fun `w3c extractParentId returns null when header absent`() {
        assertNull(w3c.extractParentId { null })
    }

    @Test
    fun `w3c extractTraceId returns null when parts less than 4`() {
        assertNull(w3c.extractTraceId { "00-abc" })
    }

    @Test
    fun `w3c extractParentId returns null when parts less than 4`() {
        assertNull(w3c.extractParentId { "00-abc" })
    }

    @Test
    fun `w3c extractTraceId returns null when parts more than 4`() {
        assertNull(w3c.extractTraceId { "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra" })
    }

    @Test
    fun `w3c extractTraceId accepts future version if format is valid`() {
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", w3c.extractTraceId { "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" })
    }

    @Test
    fun `w3c extractTraceId returns null when version is ff`() {
        assertNull(w3c.extractTraceId { "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" })
    }

    @Test
    fun `w3c extractTraceId returns null when traceId is wrong length`() {
        assertNull(w3c.extractTraceId { "00-4bf92f3577b34da6-00f067aa0ba902b7-01" })
    }

    @Test
    fun `w3c extractTraceId returns null when traceId contains uppercase hex`() {
        assertNull(w3c.extractTraceId { "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01" })
    }

    @Test
    fun `w3c extractTraceId returns null when traceId contains non-hex characters`() {
        assertNull(w3c.extractTraceId { "00-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-00f067aa0ba902b7-01" })
    }

    @Test
    fun `w3c extractTraceId returns null when traceId is all zeros`() {
        assertNull(w3c.extractTraceId { "00-00000000000000000000000000000000-00f067aa0ba902b7-01" })
    }

    @Test
    fun `w3c extractParentId returns null when parentId is wrong length`() {
        assertNull(w3c.extractParentId { "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067-01" })
    }

    @Test
    fun `w3c extractParentId returns null when parentId contains uppercase hex`() {
        assertNull(w3c.extractParentId { "00-4bf92f3577b34da6a3ce929d0e0e4736-00F067AA0BA902B7-01" })
    }

    @Test
    fun `w3c extractParentId returns null when parentId is all zeros`() {
        assertNull(w3c.extractParentId { "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01" })
    }

    @Test
    fun `w3c extractTraceId returns null when flags field is invalid`() {
        assertNull(w3c.extractTraceId { "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-ZZ" })
    }

    @Test
    fun `w3c extractTraceId returns second segment of traceparent`() {
        val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", w3c.extractTraceId { traceparent })
    }

    @Test
    fun `w3c extractParentId returns third segment of traceparent`() {
        val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        assertEquals("00f067aa0ba902b7", w3c.extractParentId { traceparent })
    }

    @Test
    fun `w3c inject sets traceparent header in W3C format`() {
        val headers = mutableMapOf<String, String>()
        w3c.inject("traceid1234567890abcdef1234567890", "spanid1234567890") { k, v -> headers[k] = v }
        assertEquals("00-traceid1234567890abcdef1234567890-spanid1234567890-01", headers["traceparent"])
    }

    // ── CustomTelemetryPropagationProvider ────────────────────────────────────

    private val custom = CustomTelemetryPropagationProvider(
        traceIdHeader = "X-Trace-Id",
        causationIdHeader = "X-Causation-Id"
    )

    @Test
    fun `custom extractTraceId reads configured header`() {
        assertEquals("my-trace", custom.extractTraceId { h -> if (h == "X-Trace-Id") "my-trace" else null })
    }

    @Test
    fun `custom extractTraceId returns null when header absent`() {
        assertNull(custom.extractTraceId { null })
    }

    @Test
    fun `custom extractParentId reads configured header`() {
        assertEquals("my-cause", custom.extractParentId { h -> if (h == "X-Causation-Id") "my-cause" else null })
    }

    @Test
    fun `custom extractParentId returns null when header absent`() {
        assertNull(custom.extractParentId { null })
    }

    @Test
    fun `custom inject sets both configured headers`() {
        val headers = mutableMapOf<String, String>()
        custom.inject("trace-abc", "span-xyz") { k, v -> headers[k] = v }
        assertEquals("trace-abc", headers["X-Trace-Id"])
        assertEquals("span-xyz", headers["X-Causation-Id"])
    }

    // ── OperationTraceIdProvider ──────────────────────────────────────────────

    @Test
    fun `trace id provider produces 32-char hex string in W3C mode`() {
        val provider = OperationTraceIdProvider(TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
        val id = provider.provideTraceId()
        assertEquals(32, id.length)
        assertTrue(id.all { it.isLetterOrDigit() })
    }

    @Test
    fun `trace id provider produces UUID format in CUSTOM mode`() {
        val provider = OperationTraceIdProvider(TelemetryConfigureProperties.PropagationMode.CUSTOM)
        val id = provider.provideTraceId()
        assertTrue(id.contains("-"))
    }

    // ── OperationCausationIdProvider ──────────────────────────────────────────

    @Test
    fun `causation id provider produces 16-char hex string in W3C mode`() {
        val provider = OperationCausationIdProvider(TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
        val id = provider.provideCausationId()
        assertEquals(16, id.length)
        assertTrue(id.all { it.isLetterOrDigit() })
    }

    @Test
    fun `causation id provider produces UUID format in CUSTOM mode`() {
        val provider = OperationCausationIdProvider(TelemetryConfigureProperties.PropagationMode.CUSTOM)
        val id = provider.provideCausationId()
        assertTrue(id.contains("-"))
    }

    // ── OperationSpanIdProvider ───────────────────────────────────────────────

    @Test
    fun `span id provider produces 16-char hex string in W3C mode`() {
        val provider = OperationSpanIdProvider(TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
        val id = provider.provideSpanId()
        assertEquals(16, id.length)
        assertTrue(id.all { it.isLetterOrDigit() })
    }

    @Test
    fun `span id provider produces UUID format in CUSTOM mode`() {
        val provider = OperationSpanIdProvider(TelemetryConfigureProperties.PropagationMode.CUSTOM)
        val id = provider.provideSpanId()
        assertTrue(id.contains("-"))
    }
}
