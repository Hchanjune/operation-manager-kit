package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.TraceIdProvider
import java.util.UUID

class OperationTraceIdProvider: TraceIdProvider {
    override fun provideTraceId(): String {
        return UUID.randomUUID().toString()
    }
}