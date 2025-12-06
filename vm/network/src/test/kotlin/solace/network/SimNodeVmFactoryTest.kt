package solace.network

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import solace.vm.internal.sim.asm.AsmParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

private const val SOLBC_HEADER_SIZE = 16

class SimNodeVmFactoryTest {
    @Test
    fun `sim factory runs hardware network end-to-end`() = runBlocking {
        val pulse = LoadedNode(
            name = "Pulse",
            type = NodeType.HARDWARE,
            ports = PortSignature(inputs = emptyList(), outputs = listOf("tick"), self = emptyList()),
            bytecode = solbc(NodeType.HARDWARE, runCode = pulseRun())
        )

        val accumulator = LoadedNode(
            name = "Accumulator",
            type = NodeType.HARDWARE,
            ports = PortSignature(inputs = listOf("inc"), outputs = listOf("total"), self = emptyList()),
            bytecode = solbc(NodeType.HARDWARE, initCode = accumulatorInit(), runCode = accumulatorRun())
        )

        val program = LoadedProgram(
            nodes = listOf(pulse, accumulator),
            connections = listOf(
                Connection(Endpoint("Pulse", "tick"), Endpoint("Accumulator", "inc"))
            )
        )

        val network = buildNetwork(program)
        val jobs = network.launch(this, SimNodeVmFactory())

        val totalChannel = network.nodes
            .single { it.descriptor.name == "Accumulator" }
            .ports
            .outputs
            .getValue("total") as Channel<Any?>

        val results = listOf(
            totalChannel.receive() as Int,
            totalChannel.receive() as Int,
            totalChannel.receive() as Int
        )

        assertEquals(listOf(1, 2, 3), results)
        jobs.forEach { it.cancelAndJoin() }
    }
}

private fun solbc(nodeType: NodeType, initCode: String = "", runCode: String): ByteArray {
    val initBytes = initCode.toByteArray(StandardCharsets.UTF_8)
    val runBytes = runCode.toByteArray(StandardCharsets.UTF_8)
    val buffer = ByteBuffer.allocate(SOLBC_HEADER_SIZE + initBytes.size + runBytes.size)
        .order(ByteOrder.LITTLE_ENDIAN)

    buffer.put("SOLB".toByteArray(StandardCharsets.US_ASCII))
    buffer.put(0x01) // container_version
    buffer.put(
        when (nodeType) {
            NodeType.HARDWARE -> 0
            NodeType.SOFTWARE -> 1
        }.toByte()
    )
    buffer.put(0x01) // isa_version placeholder
    buffer.put(0x00) // flags
    buffer.putInt(initBytes.size)
    buffer.putInt(runBytes.size)
    buffer.put(initBytes)
    buffer.put(runBytes)

    return buffer.array()
}

private fun encodeAsm(source: String): String =
    AsmParser.encodeInstructions(source.trimIndent()).joinToString("")

private fun pulseRun(): String = encodeAsm(
    """
    .new %Fifo ${'$'}tick
    .immcon ${'$'}tick@in #1
    """
)

private fun accumulatorInit(): String = encodeAsm(
    """
    .new %Fifo ${'$'}state ?
    .immcon ${'$'}state@in #0 ?
    """
)

private fun accumulatorRun(): String = encodeAsm(
    """
    .new %Fifo ${'$'}inc
    .new %Fifo ${'$'}state
    .new %Adder ${'$'}add
    .new %Fifo ${'$'}total
    .con ${'$'}state@out ${'$'}add@in1
    .con ${'$'}inc@out ${'$'}add@in2
    .con ${'$'}add@out ${'$'}state@in
    .con ${'$'}add@out ${'$'}total@in
    """
)
