package io.github.hchanjune.omk.core.provider

interface TraceIdProvider {
    fun provideTraceId(): String
}