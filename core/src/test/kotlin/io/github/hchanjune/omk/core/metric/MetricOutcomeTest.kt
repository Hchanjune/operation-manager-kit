package io.github.hchanjune.omk.core.metric

import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricOutcomeTest {

    private class PolicyException(val status: Int) : RuntimeException("policy $status"), FaultAttributable {
        override val isClientFault: Boolean get() = status in 400..499
    }

    private class FaultAttributableCancellation : CancellationException("cancelled"), FaultAttributable {
        override val isClientFault: Boolean = true
    }

    private class FaultAttributableIllegalState(override val isClientFault: Boolean)
        : IllegalStateException("state"), FaultAttributable

    @Test
    fun `client-fault exception counts as FAILURE_CLIENT`() {
        assertEquals(MetricStatus.FAILURE_CLIENT, MetricOutcome.fail(PolicyException(404)).status)
        assertEquals(MetricStatus.FAILURE_CLIENT, MetricOutcome.fail(PolicyException(400)).status)
    }

    @Test
    fun `server-fault exception counts as FAILURE_SERVER`() {
        assertEquals(MetricStatus.FAILURE_SERVER, MetricOutcome.fail(PolicyException(500)).status)
        assertEquals(MetricStatus.FAILURE_SERVER, MetricOutcome.fail(PolicyException(502)).status)
    }

    @Test
    fun `cancellation wins over the exception's own fault`() {
        assertEquals(MetricStatus.CANCELLED, MetricOutcome.fail(FaultAttributableCancellation()).status)
    }

    @Test
    fun `the exception's own fault wins over the type heuristic`() {
        assertEquals(MetricStatus.FAILURE_SERVER, MetricOutcome.fail(FaultAttributableIllegalState(isClientFault = false)).status)
    }

    @Test
    fun `plain exceptions keep the type heuristic`() {
        assertEquals(MetricStatus.FAILURE_CLIENT, MetricOutcome.fail(IllegalArgumentException()).status)
        assertEquals(MetricStatus.FAILURE_SERVER, MetricOutcome.fail(RuntimeException()).status)
    }

    @Test
    fun `error type and message are kept`() {
        val outcome = MetricOutcome.fail(PolicyException(404))
        assertEquals("PolicyException", outcome.errorType)
        assertEquals("policy 404", outcome.errorMessage)
    }
}
