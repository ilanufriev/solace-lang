package solace.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val PACKAGE_MAGIC = "SOLP"
private const val PACKAGE_HEADER_SIZE = 16
private const val CONTAINER_MAGIC = "SOLB"
private const val CONTAINER_HEADER_SIZE = 16

private data class BytecodeBlock(
    val nodeName: String,
    val offset: Int,
    val bytes: ByteArray
) {
    val size: Int
        get() = bytes.size
}

/**
 * Writes a single program package (*.solpkg) with placeholder bytecode blocks for every node.
 */
fun writeProgramPackage(topology: NetworkTopology, outputDir: Path, packageName: String = "program.solpkg"): Path {
    val strings = buildStringTable(topology)
    validateStringTable(strings)
    val stringIds = strings.withIndex().associate { (idx, value) -> value to idx }

    val stringTableBytes = buildStringTableBytes(strings)
    val instructionsLength = computeInstructionLength(topology)
    val metaSize = stringTableBytes.size + instructionsLength

    val baseOffset = PACKAGE_HEADER_SIZE + metaSize
    val blocks = buildBytecodeBlocks(topology, baseOffset)

    val instructions = buildInstructionStream(topology, stringIds, blocks)
    check(instructions.size == instructionsLength) {
        "Instruction stream length mismatch: expected $instructionsLength, got ${instructions.size}"
    }

    Files.createDirectories(outputDir)
    val outputPath = outputDir.resolve(packageName)

    Files.newOutputStream(outputPath).use { out ->
        out.write(buildPackageHeader(metaSize, topology.nodes.size))
        out.write(stringTableBytes)
        out.write(instructions)
        blocks.forEach { out.write(it.bytes) }
    }

    return outputPath
}

private fun buildPackageHeader(metaSize: Int, nodeCount: Int): ByteArray {
    val header = ByteBuffer.allocate(PACKAGE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    header.put(PACKAGE_MAGIC.toByteArray(StandardCharsets.US_ASCII))
    header.put(0x01) // container_version
    header.put(0x00) // flags
    header.putShort(0) // reserved
    header.putInt(metaSize)
    header.putInt(nodeCount)
    return header.array()
}

private fun buildStringTable(topology: NetworkTopology): List<String> {
    val unique = linkedSetOf<String>()
    topology.nodes.forEach { node ->
        unique += node.name
        unique.addAll(node.ports.inputs)
        unique.addAll(node.ports.outputs)
        unique.addAll(node.ports.self)
    }
    topology.connections.forEach { connection ->
        unique += connection.from.node
        unique += connection.from.port
        unique += connection.to.node
        unique += connection.to.port
    }
    return unique.toList()
}

private fun validateStringTable(strings: List<String>) {
    require(strings.size <= 0xFFFF) { "Too many strings for string table (${strings.size} > 65535)." }
    strings.forEach { str ->
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 0xFFFF) { "String is too long for u16 length: '$str'" }
    }
}

private fun buildStringTableBytes(strings: List<String>): ByteArray {
    val out = ByteArrayOutputStream()
    writeU32(out, strings.size)
    strings.forEach { str ->
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        writeU16(out, bytes.size)
        out.write(bytes)
    }
    return out.toByteArray()
}

private fun computeInstructionLength(topology: NetworkTopology): Int {
    var total = 0
    topology.nodes.forEach { node ->
        total += 1 // opcode
        total += 2 // node name id
        total += 1 // node type
        total += 1 + node.ports.inputs.size * 2
        total += 1 + node.ports.outputs.size * 2
        total += 1 + node.ports.self.size * 2
        total += 4 // bc offset
        total += 4 // bc size
        total += 1 // bc format
    }
    total += topology.connections.size * 9 // opcode + 4*u16
    total += 1 // END
    return total
}

private fun buildInstructionStream(
    topology: NetworkTopology,
    stringIds: Map<String, Int>,
    blocks: List<BytecodeBlock>
): ByteArray {
    val blockByNode = blocks.associateBy { it.nodeName }
    val out = ByteArrayOutputStream()

    topology.nodes.forEach { node ->
        val block = blockByNode.getValue(node.name)
        writeU8(out, 0x01) // NODE_DEF
        writeU16(out, idOf(node.name, stringIds))
        writeU8(out, when (node.type) {
            NodeType.HARDWARE -> 0
            NodeType.SOFTWARE -> 1
        })
        require(node.ports.inputs.size <= 0xFF && node.ports.outputs.size <= 0xFF && node.ports.self.size <= 0xFF) {
            "Port count exceeds 255 for node ${node.name}"
        }
        writeU8(out, node.ports.inputs.size)
        node.ports.inputs.forEach { port -> writeU16(out, idOf(port, stringIds)) }
        writeU8(out, node.ports.outputs.size)
        node.ports.outputs.forEach { port -> writeU16(out, idOf(port, stringIds)) }
        writeU8(out, node.ports.self.size)
        node.ports.self.forEach { port -> writeU16(out, idOf(port, stringIds)) }
        writeU32(out, block.offset)
        writeU32(out, block.size)
        writeU8(out, 0x01) // bc_format = solbc
    }

    topology.connections.forEach { connection ->
        writeU8(out, 0x02) // CONNECT
        writeU16(out, idOf(connection.from.node, stringIds))
        writeU16(out, idOf(connection.from.port, stringIds))
        writeU16(out, idOf(connection.to.node, stringIds))
        writeU16(out, idOf(connection.to.port, stringIds))
    }

    writeU8(out, 0xFF) // END
    return out.toByteArray()
}

private fun idOf(value: String, stringIds: Map<String, Int>): Int =
    stringIds[value] ?: error("Missing string id for '$value'")

private fun buildBytecodeBlocks(topology: NetworkTopology, baseOffset: Int): List<BytecodeBlock> {
    val blocks = mutableListOf<BytecodeBlock>()
    var offset = baseOffset
    topology.nodes.forEach { node ->
        val bytes = buildEmptySolbc(node.type)
        blocks += BytecodeBlock(node.name, offset, bytes)
        offset += bytes.size
    }
    return blocks
}

private fun buildEmptySolbc(type: NodeType): ByteArray {
    val buffer = ByteBuffer.allocate(CONTAINER_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(CONTAINER_MAGIC.toByteArray(StandardCharsets.US_ASCII))
    buffer.put(0x01) // container_version
    buffer.put(
        when (type) {
            NodeType.HARDWARE -> 0
            NodeType.SOFTWARE -> 1
        }
    )
    buffer.put(0x01) // isa_version (placeholder)
    buffer.put(0x00) // flags
    buffer.putInt(0) // init_size
    buffer.putInt(0) // run_size
    return buffer.array()
}

private fun writeU8(out: ByteArrayOutputStream, value: Int) {
    out.write(value and 0xFF)
}

private fun writeU16(out: ByteArrayOutputStream, value: Int) {
    require(value in 0..0xFFFF) { "u16 overflow: $value" }
    out.write(value and 0xFF)
    out.write((value ushr 8) and 0xFF)
}

private fun writeU32(out: ByteArrayOutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 24) and 0xFF)
}
