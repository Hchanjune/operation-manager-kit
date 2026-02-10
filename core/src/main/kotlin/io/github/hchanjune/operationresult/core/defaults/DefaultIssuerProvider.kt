package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.providers.IssuerProvider

object DefaultIssuerProvider: IssuerProvider {
    override fun currentIssuer() = "anonymous"
}