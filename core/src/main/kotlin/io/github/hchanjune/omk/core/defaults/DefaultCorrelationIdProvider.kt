package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.providers.invocation.CorrelationIdProvider
import java.util.UUID

object DefaultCorrelationIdProvider: CorrelationIdProvider {
    override fun newCorrelationId() = UUID.randomUUID().toString()
}