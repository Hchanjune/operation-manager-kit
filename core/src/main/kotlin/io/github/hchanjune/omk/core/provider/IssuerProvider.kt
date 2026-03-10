package io.github.hchanjune.omk.core.provider

fun interface IssuerProvider {
    fun currentIssuer(): String
}