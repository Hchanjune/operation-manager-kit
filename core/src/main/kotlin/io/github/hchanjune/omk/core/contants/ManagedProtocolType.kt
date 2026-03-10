package io.github.hchanjune.omk.core.contants

enum class ManagedProtocolType {
    HTTP,
    RPC,
    MESSAGING,
    FAAS,
    DB,
    UNSUPPORTED;

    companion object {
        fun from(value: String): ManagedProtocolType {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: UNSUPPORTED
        }
    }
}