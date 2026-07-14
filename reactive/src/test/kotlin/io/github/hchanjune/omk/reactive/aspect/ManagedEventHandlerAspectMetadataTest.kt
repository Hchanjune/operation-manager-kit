package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.annotations.ManagedEventCausationId
import io.github.hchanjune.omk.core.annotations.ManagedEventIssuer
import io.github.hchanjune.omk.core.annotations.ManagedEventTraceId
import io.github.hchanjune.omk.core.annotations.ManagedEventType
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.event.EventMetadataExtractor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.messaging.support.GenericMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// ── Test fixtures ─────────────────────────────────────────────────────────────

private class FullyAnnotatedEvent(
    @field:ManagedEventTraceId     val traceId: String     = "tid-123",
    @field:ManagedEventCausationId val causationId: String = "cid-456",
    @field:ManagedEventIssuer      val issuer: String      = "payment-service",
    @field:ManagedEventType        val eventType: String   = "OrderCreated"
)

private class OnlyTraceIdAnnotated(
    @field:ManagedEventTraceId val traceId: String = "tid-only"
)

private class NotAnnotated(val traceId: String = "plain-field")

private open class AnnotatedBase(
    @field:ManagedEventTraceId val traceId: String = "base-tid"
)
private class AnnotatedChild(
    traceId: String = "child-tid",
    @field:ManagedEventCausationId val causationId: String = "child-cid"
) : AnnotatedBase(traceId)

private class DuckTyped(
    val traceId: String     = "duck-tid",
    val causationId: String = "duck-cid",
    val issuer: String      = "duck-svc",
    val eventType: String   = "DuckEvent"
)

private class GetterOnly {
    fun getTraceId()     = "getter-tid"
    fun getCausationId() = "getter-cid"
    fun getIssuer()      = "getter-svc"
    fun getEventType()   = "getter-ev"
}

private class NoTraceFields(val issuer: String = "x")

// ── Test class ────────────────────────────────────────────────────────────────

class ManagedEventHandlerAspectMetadataTest {

    private fun extractMetadata(vararg args: Any?): EventMetadata =
        EventMetadataExtractor.extract(arrayOf(*args))

    private fun invokeExtractor(name: String, arg: Any): EventMetadata? {
        val method = EventMetadataExtractor::class.java.getDeclaredMethod(name, Any::class.java)
        method.isAccessible = true
        return method.invoke(EventMetadataExtractor, arg) as? EventMetadata
    }

    private fun tryAnnotations(arg: Any): EventMetadata? = invokeExtractor("tryExtractFromAnnotations", arg)

    private fun tryReflection(arg: Any): EventMetadata? = invokeExtractor("tryExtractFromReflection", arg)

    private fun tryMessage(arg: Any): EventMetadata? = invokeExtractor("tryExtractFromMessage", arg)

    private fun tryConsumerRecord(arg: Any): EventMetadata? = invokeExtractor("tryExtractFromConsumerRecord", arg)

    // ── Annotation extraction ─────────────────────────────────────────────

    @Test
    fun `tryExtractFromAnnotations reads all four field annotations`() {
        val result = tryAnnotations(FullyAnnotatedEvent())
        assertEquals(EventMetadata("tid-123", "cid-456", "payment-service", "OrderCreated"), result)
    }

    @Test
    fun `tryExtractFromAnnotations partial annotations fill matching fields only`() {
        val result = tryAnnotations(OnlyTraceIdAnnotated())
        assertEquals(EventMetadata("tid-only", null, null, null), result)
    }

    @Test
    fun `tryExtractFromAnnotations returns null when no annotations present`() {
        assertNull(tryAnnotations(NotAnnotated()))
    }

    @Test
    fun `tryExtractFromAnnotations traverses superclass fields`() {
        val result = tryAnnotations(AnnotatedChild())
        assertEquals(EventMetadata("child-tid", "child-cid", null, null), result)
    }

    @Test
    fun `annotation cache returns consistent result on repeated calls`() {
        val event = FullyAnnotatedEvent(traceId = "repeat-tid")
        val first  = tryAnnotations(event)
        val second = tryAnnotations(event)
        assertEquals(first, second)
    }

    // ── Reflection duck-typing ────────────────────────────────────────────

    @Test
    fun `tryExtractFromReflection reads public fields by name`() {
        val result = tryReflection(DuckTyped())
        assertEquals(EventMetadata("duck-tid", "duck-cid", "duck-svc", "DuckEvent"), result)
    }

    @Test
    fun `tryExtractFromReflection falls back to getters when fields are absent`() {
        val result = tryReflection(GetterOnly())
        assertEquals(EventMetadata("getter-tid", "getter-cid", "getter-svc", "getter-ev"), result)
    }

    @Test
    fun `tryExtractFromReflection returns null when neither traceId nor causationId found`() {
        assertNull(tryReflection(NoTraceFields()))
    }

    // ── Spring Message extraction ─────────────────────────────────────────

    @Test
    fun `tryExtractFromMessage parses W3C traceparent header`() {
        val msg = GenericMessage<String>(
            "payload", mapOf(
                "traceparent" to "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "X-Issuer"    to "msg-svc",
                "eventType"   to "PaymentProcessed"
            )
        )
        val result = tryMessage(msg)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", result?.traceId)
        assertEquals("00f067aa0ba902b7",                  result?.causationId)
        assertEquals("msg-svc",                           result?.issuer)
        assertEquals("PaymentProcessed",                  result?.eventType)
    }

    @Test
    fun `tryExtractFromMessage falls back to custom headers when no traceparent`() {
        val msg = GenericMessage<String>(
            "payload", mapOf(
                "X-Trace-Id"     to "custom-tid",
                "X-Causation-Id" to "custom-cid",
                "X-Issuer"       to "custom-svc"
            )
        )
        val result = tryMessage(msg)
        assertEquals("custom-tid", result?.traceId)
        assertEquals("custom-cid", result?.causationId)
        assertEquals("custom-svc", result?.issuer)
    }

    @Test
    fun `tryExtractFromMessage returns null for non-Message objects`() {
        assertNull(tryMessage("plain-string"))
    }

    // ── ConsumerRecord extraction ─────────────────────────────────────────

    @Test
    fun `tryExtractFromConsumerRecord returns null for non-ConsumerRecord`() {
        assertNull(tryConsumerRecord("not-a-consumer-record"))
    }

    @Test
    fun `tryExtractFromConsumerRecord parses W3C traceparent header`() {
        val record = ConsumerRecord<String, String>("order-created", 0, 0L, null, "payload")
        record.headers().add("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".toByteArray())
        record.headers().add("X-Issuer", "kafka-svc".toByteArray())

        val result = tryConsumerRecord(record)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", result?.traceId)
        assertEquals("00f067aa0ba902b7",                  result?.causationId)
        assertEquals("kafka-svc",                         result?.issuer)
        assertEquals("order-created",                     result?.eventType)
    }

    @Test
    fun `tryExtractFromConsumerRecord falls back to custom headers when no traceparent`() {
        val record = ConsumerRecord<String, String>("payment-processed", 0, 0L, null, "payload")
        record.headers().add("X-Trace-Id",     "custom-tid".toByteArray())
        record.headers().add("X-Causation-Id", "custom-cid".toByteArray())

        val result = tryConsumerRecord(record)
        assertEquals("custom-tid",        result?.traceId)
        assertEquals("custom-cid",        result?.causationId)
        assertEquals("payment-processed", result?.eventType)
    }

    @Test
    fun `tryExtractFromConsumerRecord with no headers returns metadata with null trace fields`() {
        val record = ConsumerRecord<String, String>("bare-topic", 0, 0L, null, "payload")
        val result = tryConsumerRecord(record)
        assertNotNull(result)
        assertNull(result.traceId)
        assertEquals("bare-topic", result.eventType)
    }

    // ── extractMetadata priority chain ────────────────────────────────────

    @Test
    fun `extractMetadata returns empty metadata for empty args`() {
        val result = extractMetadata()
        assertEquals(EventMetadata(null, null, null, null), result)
    }

    @Test
    fun `extractMetadata skips null args and returns empty`() {
        val result = extractMetadata(null, null)
        assertEquals(EventMetadata(null, null, null, null), result)
    }

    @Test
    fun `extractMetadata prefers annotations over duck-typing`() {
        val annotated = FullyAnnotatedEvent(traceId = "ann-tid", causationId = "ann-cid")
        val result = extractMetadata(annotated)
        assertEquals("ann-tid", result.traceId)
    }

    @Test
    fun `extractMetadata falls through to reflection when no annotations present`() {
        val result = extractMetadata(DuckTyped(traceId = "fall-tid"))
        assertEquals("fall-tid", result.traceId)
    }

    @Test
    fun `extractMetadata uses first matching arg only`() {
        val first  = FullyAnnotatedEvent(traceId = "first")
        val second = FullyAnnotatedEvent(traceId = "second")
        val result = extractMetadata(first, second)
        assertEquals("first", result.traceId)
    }
}
