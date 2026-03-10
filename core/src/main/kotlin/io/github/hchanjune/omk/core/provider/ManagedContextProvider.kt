package io.github.hchanjune.omk.core.provider

import io.github.hchanjune.omk.core.context.ManagedContext

interface ManagedContextProvider {
    fun provide(): ManagedContext
}