package solace.network

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PACKAGE_HEADER_SIZE = 16

class DotOutTest {
    @Test
    fun `dot-out writes Graphviz file for loaded program`() {
        val packageFile = Files.createTempFile("dot-out", ".solpkg")
        Files.write(packageFile, samplePackageBytes())

        val dotFile = Files.createTempFile("dot-out", ".dot")

        main(arrayOf(packageFile.toString(), "--dot-out", dotFile.toString()))

        assertTrue(Files.exists(dotFile), "DOT file must be created")
        val dot = Files.readString(dotFile)

        val expected = buildNetwork(loadProgramPackage(packageFile)).toDOTNetwork().toString()
        assertEquals(expected.trim(), dot.trim(), "DOT output should match the network topology")
    }
}

private fun samplePackageBytes(): ByteArray {
    val strings = listOf("A", "B", "out", "in")
    val stringTable = stringTable(strings)

    val bcA = solbcContainer(NodeType.HARDWARE)
    val bcB = solbcContainer(NodeType.HARDWARE)

    // Meta size depends on string table + instruction bytes; instruction length is fixed regardless of offsets,
    // so we can calculate it using placeholder offsets.
    val instructionsLength = instructions(strings, bcOffsetA = 0, bcOffsetB = 0, bcSizeA = bcA.size, bcSizeB = bcB.size).size
    val metaSize = stringTable.size + instructionsLength

    val bcOffsetA = PACKAGE_HEADER_SIZE + metaSize
    val bcOffsetB = bcOffsetA + bcA.size
    val instructions = instructions(strings, bcOffsetA, bcOffsetB, bcA.size, bcB.size)

    val header = ByteBuffer.allocate(PACKAGE_HEADER_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("SOLP".toByteArray(StandardCharsets.US_ASCII))
            put(0x01) // container_version
            put(0x00) // flags
            putShort(0) // reserved
            putInt(metaSize)
            putInt(2) // node_count
        }
        .array()

    return header + stringTable + instructions + bcA + bcB
}

private fun stringTable(strings: List<String>): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(intToLe(strings.size))
    strings.forEach { s ->
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        out.write(shortToLe(bytes.size))
        out.write(bytes)
    }
    return out.toByteArray()
}

private fun instructions(
    strings: List<String>,
    bcOffsetA: Int,
    bcOffsetB: Int,
    bcSizeA: Int,
    bcSizeB: Int
): ByteArray {
    val idx = strings.withIndex().associate { (i, v) -> v to i }
    val out = ByteArrayOutputStream()

    // Node A: outputs "out"
    out.write(0x01)
    out.write(shortToLe(idx.getValue("A")))
    out.write(0x00) // hardware
    out.write(0x00) // in_count
    out.write(0x01) // out_count
    out.write(shortToLe(idx.getValue("out")))
    out.write(0x00) // self_count
    out.write(intToLe(bcOffsetA))
    out.write(intToLe(bcSizeA))
    out.write(0x01) // bc_format

    // Node B: inputs "in"
    out.write(0x01)
    out.write(shortToLe(idx.getValue("B")))
    out.write(0x00) // hardware
    out.write(0x01) // in_count
    out.write(shortToLe(idx.getValue("in")))
    out.write(0x00) // out_count
    out.write(0x00) // self_count
    out.write(intToLe(bcOffsetB))
    out.write(intToLe(bcSizeB))
    out.write(0x01) // bc_format

    // Connection: A.out -> B.in
    out.write(0x02)
    out.write(shortToLe(idx.getValue("A")))
    out.write(shortToLe(idx.getValue("out")))
    out.write(shortToLe(idx.getValue("B")))
    out.write(shortToLe(idx.getValue("in")))

    out.write(0xFF) // END
    return out.toByteArray()
}

private fun solbcContainer(nodeType: NodeType): ByteArray {
    return ByteBuffer.allocate(16)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("SOLB".toByteArray(StandardCharsets.US_ASCII))
            put(0x01) // container_version
            put(
                when (nodeType) {
                    NodeType.HARDWARE -> 0
                    NodeType.SOFTWARE -> 1
                }.toByte()
            )
            put(0x01) // isa_version placeholder
            put(0x00) // flags
            putInt(0) // init_size
            putInt(0) // run_size
        }
        .array()
}

private fun shortToLe(value: Int): ByteArray =
    byteArrayOf((value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte())

private fun intToLe(value: Int): ByteArray =
    byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )
