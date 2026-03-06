package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.invocation.InvocationInfo
import io.github.hchanjune.operationresult.core.providers.invocation.InvocationInfoProvider

object DefaultInvocationInfoProvider: InvocationInfoProvider {
    override fun current() = InvocationInfo()
}