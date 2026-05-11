package io.github.hchanjune.omk.core.metric

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetricTimingTest {

    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)

    @Test
    fun `durationMillis is null before end`() {
        val timing = MetricTiming.started()
        assertNull(timing.durationMillis())
    }

    @Test
    fun `durationMillis returns elapsed millis after end`() {
        val timing = MetricTiming.started(clockAt("2025-01-01T00:00:00Z"))
        timing.end(clockAt("2025-01-01T00:00:01Z"))
        assertEquals(1000L, timing.durationMillis())
    }

    @Test
    fun `end is idempotent - second call does not overwrite`() {
        val timing = MetricTiming.started(clockAt("2025-01-01T00:00:00Z"))
        timing.end(clockAt("2025-01-01T00:00:01Z"))
        timing.end(clockAt("2025-01-01T00:00:05Z"))
        assertEquals(1000L, timing.durationMillis())
    }

    @Test
    fun `startedAtEpochMilli reflects the clock at construction`() {
        val clock = clockAt("2025-06-01T12:00:00Z")
        val timing = MetricTiming.started(clock)
        assertEquals(clock.millis(), timing.startedAtEpochMilli)
    }
}
