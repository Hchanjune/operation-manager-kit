package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.models.invocation.InvocationInfo
import io.github.hchanjune.omk.core.providers.invocation.InvocationInfoProvider

object DefaultInvocationInfoProvider: InvocationInfoProvider {
    override fun current() = InvocationInfo()
}