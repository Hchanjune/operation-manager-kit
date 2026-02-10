package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.InvocationInfo
import io.github.hchanjune.operationresult.core.providers.InvocationInfoProvider

object DefaultInvocationInfoProvider: InvocationInfoProvider {
    override fun current() = InvocationInfo()
}