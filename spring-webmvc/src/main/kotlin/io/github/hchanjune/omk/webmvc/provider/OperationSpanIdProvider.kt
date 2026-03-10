package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.SpanIdProvider
import java.util.UUID

class OperationSpanIdProvider: SpanIdProvider {
    override fun provideSpanId(): String {
        return UUID.randomUUID().toString()
    }
}