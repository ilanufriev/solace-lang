package solace.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val PACKAGE_MAGIC = "SOLP"
private const val PACKAGE_HEADER_SIZE = 16
private const val CONTAINER_MAGIC = "SOLB"

enum class NodeType { HARDWARE, SOFTWARE }

data class PortSignature(
    val inputs: List<String>,
    val outputs: List<String>,
    val self: List<String>
)

data class Endpoint(val node: String, val port: String)

data class Connection(val from: Endpoint, val to: Endpoint)

data class LoadedNode(
    val name: String,
    val type: NodeType,
    val ports: PortSignature,
    val bytecode: ByteArray
)

data class LoadedProgram(
    val nodes: List<LoadedNode>,
    val connections: List<Connection>
)

// Load a solace package from disk, parse header/meta, validate bytecode containers, and return nodes + connections.
fun loadProgramPackage(path: Path): LoadedProgram {
    val bytes = Files.readAllBytes(resolvePackagePath(path))
    require(bytes.size >= PACKAGE_HEADER_SIZE) { "File too small to be a solace package: ${bytes.size} bytes" }

    val header = ByteBuffer.wrap(bytes, 0, PACKAGE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    val magic = ByteArray(4).also { header.get(it) }.toString(StandardCharsets.US_ASCII)
    require(magic == PACKAGE_MAGIC) { "Invalid package magic: '$magic'" }
    header.get() // container_version, currently ignored
    header.get() // flags
    header.short // reserved
    val metaSize = header.int
    val nodeCount = header.int
    require(metaSize >= 0) { "Negative meta size: $metaSize" }
    val metaStart = PACKAGE_HEADER_SIZE
    val metaEnd = metaStart + metaSize
    require(metaEnd <= bytes.size) { "Meta section exceeds file size: end=$metaEnd, size=${bytes.size}" }

    val metaBuffer = ByteBuffer.wrap(bytes, metaStart, metaSize).order(ByteOrder.LITTLE_ENDIAN)
    val strings = readStringTable(metaBuffer)
    val instructions = readInstructions(metaBuffer, strings)

    require(instructions.nodes.size == nodeCount) {
        "Node count mismatch: header=$nodeCount, meta=${instructions.nodes.size}"
    }

    val loadedNodes = instructions.nodes.map { spec ->
        require(spec.bcOffset + spec.bcSize <= bytes.size) {
            "Bytecode for node '${spec.name}' exceeds file size (offset=${spec.bcOffset}, size=${spec.bcSize}, file=${bytes.size})"
        }
        val bc = bytes.copyOfRange(spec.bcOffset, spec.bcOffset + spec.bcSize)
        validateSolbc(spec, bc)
        LoadedNode(spec.name, spec.type, spec.ports, bc)
    }

    return LoadedProgram(loadedNodes, instructions.connections)
}

private data class NodeSpec(
    val name: String,
    val type: NodeType,
    val ports: PortSignature,
    val bcOffset: Int,
    val bcSize: Int
)

private data class InstructionResult(
    val nodes: List<NodeSpec>,
    val connections: List<Connection>
)

// Read UTF-8 string table (count + length-prefixed entries) from the meta buffer.
private fun readStringTable(buffer: ByteBuffer): List<String> {
    val count = buffer.int
    require(count >= 0) { "Negative string count: $count" }
    val strings = mutableListOf<String>()
    repeat(count) {
        val len = buffer.short.toInt() and 0xFFFF
        require(len >= 0 && buffer.remaining() >= len) { "Invalid string length $len at index $it" }
        val data = ByteArray(len)
        buffer.get(data)
        strings += data.toString(StandardCharsets.UTF_8)
    }
    return strings
}

// Interpret meta-section opcodes into node specs and connection list.
private fun readInstructions(buffer: ByteBuffer, strings: List<String>): InstructionResult {
    val nodes = mutableListOf<NodeSpec>()
    val connections = mutableListOf<Connection>()
    while (buffer.hasRemaining()) {
        val opcode = buffer.get().toInt() and 0xFF
        when (opcode) {
            0x01 -> nodes += readNodeDef(buffer, strings)
            0x02 -> connections += readConnection(buffer, strings)
            0xFF -> break
            else -> error("Unknown opcode 0x${opcode.toString(16)} in meta-section")
        }
    }
    return InstructionResult(nodes, connections)
}

// Decode a single node definition: name, type, port signatures, bytecode offsets, and format.
private fun readNodeDef(buffer: ByteBuffer, strings: List<String>): NodeSpec {
    val name = strings[u16(buffer)]
    val type = when (buffer.get().toInt() and 0xFF) {
        0 -> NodeType.HARDWARE
        1 -> NodeType.SOFTWARE
        else -> error("Unknown node type")
    }

    val inputs = readPorts(buffer, strings)
    val outputs = readPorts(buffer, strings)
    val self = readPorts(buffer, strings)

    val bcOffset = u32(buffer).also { require(it >= 0) { "Negative bc_offset for '$name'" } }
    val bcSize = u32(buffer).also { require(it >= 0) { "Negative bc_size for '$name'" } }
    val bcFormat = buffer.get().toInt() and 0xFF
    require(bcFormat == 0x01) { "Unsupported bytecode format $bcFormat for node '$name'" }

    return NodeSpec(name, type, PortSignature(inputs, outputs, self), bcOffset, bcSize)
}

// Read a list of port names using string indices.
private fun readPorts(buffer: ByteBuffer, strings: List<String>): List<String> {
    val count = buffer.get().toInt() and 0xFF
    val result = ArrayList<String>(count)
    repeat(count) {
        result += strings[u16(buffer)]
    }
    return result
}

// Decode a single connection between node ports.
private fun readConnection(buffer: ByteBuffer, strings: List<String>): Connection {
    val fromNode = strings[u16(buffer)]
    val fromPort = strings[u16(buffer)]
    val toNode = strings[u16(buffer)]
    val toPort = strings[u16(buffer)]
    return Connection(Endpoint(fromNode, fromPort), Endpoint(toNode, toPort))
}

// Basic validation for solbc container: magic, sizes, and node type consistency.
private fun validateSolbc(spec: NodeSpec, bytes: ByteArray) {
    require(bytes.size >= 16) { "Bytecode for '${spec.name}' is too small: ${bytes.size} bytes" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = ByteArray(4).also { buffer.get(it) }.toString(StandardCharsets.US_ASCII)
    require(magic == CONTAINER_MAGIC) { "Node '${spec.name}': invalid solbc magic '$magic'" }
    buffer.get() // container_version
    val nodeType = buffer.get().toInt() and 0xFF
    val isaVersion = buffer.get() // isa_version
    buffer.get() // flags
    val initSize = buffer.int
    val runSize = buffer.int
    require(initSize >= 0 && runSize >= 0) { "Negative section size in '${spec.name}' (init=$initSize, run=$runSize)" }
    val expected = 16 + initSize + runSize
    require(expected <= bytes.size) { "Node '${spec.name}': header sizes exceed container (expected >= $expected, got ${bytes.size})" }
    require(
        (nodeType == 0 && spec.type == NodeType.HARDWARE) ||
            (nodeType == 1 && spec.type == NodeType.SOFTWARE)
    ) { "Node '${spec.name}': type mismatch between meta and solbc (meta=${spec.type}, solbc=$nodeType)" }
    // isaVersion kept for future checks
}

// Unsigned 16-bit read.
private fun u16(buffer: ByteBuffer): Int = buffer.short.toInt() and 0xFFFF
// Unsigned 32-bit read (stored in signed int).
private fun u32(buffer: ByteBuffer): Int = buffer.int

// Locate package file by absolute path or searching current dir upwards for the relative path.
private fun resolvePackagePath(path: Path): Path {
    if (Files.exists(path)) return path
    if (path.isAbsolute) {
        throw IllegalArgumentException("Package file not found: ${path.toAbsolutePath()}")
    }
    var current: Path? = Path.of("").toAbsolutePath()
    while (current != null) {
        val candidate = current.resolve(path).normalize()
        if (Files.exists(candidate)) return candidate
        current = current.parent
    }
    throw IllegalArgumentException("Package file not found (searched current dir and parents): ${path.toAbsolutePath()}")
}
