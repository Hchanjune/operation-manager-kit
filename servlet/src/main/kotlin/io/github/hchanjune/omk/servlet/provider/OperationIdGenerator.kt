package io.github.hchanjune.omk.servlet.provider

import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

internal object OperationIdGenerator {
    private val HEX = HexFormat.of()

    fun hex(byteSize: Int): String {
        val bytes = ByteArray(byteSize)
        ThreadLocalRandom.current().nextBytes(bytes)
        return HEX.formatHex(bytes)
    }

    fun uuid(): String {
        val random = ThreadLocalRandom.current()
        return UUID(random.nextLong(), random.nextLong()).toString()
    }

}