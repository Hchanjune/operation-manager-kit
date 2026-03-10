package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import java.util.UUID

class OperationCausationIdProvider: CausationIdProvider {
    override fun provideCausationId(): String {
        return UUID.randomUUID().toString()
    }
}