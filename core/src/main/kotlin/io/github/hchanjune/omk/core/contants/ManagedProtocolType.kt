package io.github.hchanjune.omk.core.contants

enum class ManagedProtocolType {
    HTTP,
    RPC,
    MESSAGING,
    FAAS,
    DB,
    UNSUPPORTED;

    companion object {
        private val INDEX = entries.associateBy { it.name.uppercase() }

        fun from(value: String): ManagedProtocolType =
            INDEX[value.uppercase()] ?: UNSUPPORTED
    }
}