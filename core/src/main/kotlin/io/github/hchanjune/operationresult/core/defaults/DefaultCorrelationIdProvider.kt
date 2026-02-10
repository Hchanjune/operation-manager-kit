package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.providers.CorrelationIdProvider
import java.util.UUID

object DefaultCorrelationIdProvider: CorrelationIdProvider {
    override fun newCorrelationId() = UUID.randomUUID().toString()
}