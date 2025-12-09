package solace.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

internal const val SOLBC_MAGIC = "SOLB"
internal const val SOLBC_HEADER_SIZE = 16

internal data class SolbcProgram(
    val nodeType: NodeType,
    val isaVersion: Int,
    val initSection: ByteArray,
    val runSection: ByteArray
)

internal fun parseSolbc(bytes: ByteArray): SolbcProgram {
    require(bytes.size >= SOLBC_HEADER_SIZE) { "solbc container too small: ${bytes.size} bytes" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = ByteArray(4).also { buffer.get(it) }.toString(StandardCharsets.US_ASCII)
    require(magic == SOLBC_MAGIC) { "Invalid solbc magic '$magic'" }
    buffer.get() // container_version
    val nodeType = when (buffer.get().toInt() and 0xFF) {
        0 -> NodeType.HARDWARE
        1 -> NodeType.SOFTWARE
        else -> error("Unknown node type in solbc")
    }
    val isaVersion = buffer.get().toInt() and 0xFF
    buffer.get() // flags
    val initSize = buffer.int
    val runSize = buffer.int
    require(initSize >= 0 && runSize >= 0) { "Negative section size in solbc (init=$initSize, run=$runSize)" }
    val expected = SOLBC_HEADER_SIZE + initSize + runSize
    require(expected <= bytes.size) { "solbc size mismatch: expected at least $expected, got ${bytes.size}" }

    val initSection = bytes.copyOfRange(SOLBC_HEADER_SIZE, SOLBC_HEADER_SIZE + initSize)
    val runSection = bytes.copyOfRange(SOLBC_HEADER_SIZE + initSize, expected)

    return SolbcProgram(nodeType, isaVersion, initSection, runSection)
}
