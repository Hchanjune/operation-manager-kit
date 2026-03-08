package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.providers.invocation.IssuerProvider

object DefaultIssuerProvider: IssuerProvider {
    override fun currentIssuer() = "anonymous"
}