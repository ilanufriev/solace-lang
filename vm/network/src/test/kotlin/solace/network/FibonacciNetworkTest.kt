package solace.network

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import solace.vm.internal.sim.asm.AsmParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

private const val SOLBC_HEADER_SIZE = 16

class FibonacciNetworkTest {
    @Test
    fun `fibonacci pipeline matches first 45 numbers`() = runBlocking {
        val tick = LoadedNode(
            name = "TickSource",
            type = NodeType.HARDWARE,
            ports = PortSignature(inputs = emptyList(), outputs = listOf("tick"), self = emptyList()),
            bytecode = solbc(
                NodeType.HARDWARE,
                initCode = encodeAsm(
                    """
                    .new %Fifo ${'$'}tick
                    .immcon ${'$'}tick@in #1
                    """
                ),
                runCode = encodeAsm(
                    """
                    .new %Fifo ${'$'}tick
                    .immcon ${'$'}tick@in #1
                    """
                )
            )
        )

        val fib = LoadedNode(
            name = "FibStepper",
            type = NodeType.HARDWARE,
            ports = PortSignature(inputs = listOf("step"), outputs = listOf("fib"), self = listOf("prev", "curr")),
            bytecode = solbc(
                NodeType.HARDWARE,
                initCode = encodeAsm(
                    """
                    .new %Fifo ${'$'}step ?
                    .new %Fifo ${'$'}fib ?
                    .new %Fifo ${'$'}prev ?
                    .new %Fifo ${'$'}curr ?
                    .immcon ${'$'}prev@in_prev0 #0 ?
                    .immcon ${'$'}curr@in_curr0 #1 ?
                    """
                ),
                runCode = encodeAsm(
                    """
                    .new %Fifo ${'$'}step
                    .new %Fifo ${'$'}fib
                    .new %Fifo ${'$'}prev
                    .new %Fifo ${'$'}curr
                    .new %Fifo ${'$'}tickSink
                    .new %Register ${'$'}a
                    .new %Register ${'$'}b
                    .new %Adder ${'$'}add
                    .new %Register ${'$'}next
                    .con ${'$'}step@out_step0 ${'$'}tickSink@in_drop0
                    .con ${'$'}prev@out_prev0 ${'$'}a@in
                    .con ${'$'}curr@out_curr0 ${'$'}b@in
                    .con ${'$'}a@out ${'$'}add@in1
                    .con ${'$'}b@out ${'$'}add@in2
                    .con ${'$'}add@out ${'$'}next@in
                    .con ${'$'}b@out ${'$'}prev@in_prev1
                    .con ${'$'}next@out ${'$'}curr@in_curr1
                    .con ${'$'}next@out ${'$'}fib@in_fib0
                    """
                )
            )
        )

        val sink = LoadedNode(
            name = "FibSink",
            type = NodeType.HARDWARE,
            ports = PortSignature(inputs = listOf("inFib"), outputs = listOf("last"), self = emptyList()),
            bytecode = solbc(
                NodeType.HARDWARE,
                initCode = encodeAsm(
                    """
                    .new %Fifo ${'$'}inFib ?
                    .new %Fifo ${'$'}last ?
                    .immcon ${'$'}last@in_last0 #0 ?
                    """
                ),
                runCode = encodeAsm(
                    """
                    .new %Fifo ${'$'}inFib
                    .new %Fifo ${'$'}last
                    .new %Register ${'$'}v
                    .con ${'$'}inFib@out_inFib0 ${'$'}v@in
                    .con ${'$'}v@out ${'$'}last@in_last1
                    """
                )
            )
        )

        val program = LoadedProgram(
            nodes = listOf(tick, fib, sink),
            connections = listOf(
                Connection(Endpoint("TickSource", "tick"), Endpoint("FibStepper", "step")),
                Connection(Endpoint("FibStepper", "fib"), Endpoint("FibSink", "inFib"))
            )
        )

        val sniffFile = Files.createTempFile("fib-sniff", ".csv")
        val expectedCount = 45

        val network = buildNetwork(
            program,
            sniffConnections = true,
            snifferScope = this,
            sniffLimit = expectedCount,
            sniffCsv = true,
            sniffCsvFile = sniffFile
        )
        val jobs = network.launch(this, SimNodeVmFactory())

        val lastChannel = network.nodes
            .single { it.descriptor.name == "FibSink" }
            .ports
            .outputs
            .getValue("last") as Channel<Any?>

        val observed = mutableListOf<Int>()
        // First value is the sink's initial seed; skip it.
        repeat(expectedCount + 1) {
            observed += (lastChannel.receive() as Number).toInt()
        }

        val filtered = observed.drop(1)
        // The hardware pipeline outputs from F2 onward (1, 2, 3, 5, ...).
        val expected = generateFibonacci(expectedCount + 2).drop(2)
        assertEquals(expected, filtered)

        jobs.forEach { it.cancelAndJoin() }
        network.sniffers.forEach { it.cancelAndJoin() }
        network.sniffWriter?.close()

        val sniffValues = Files.readAllLines(sniffFile)
            .mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size == 6 && parts[1] == "FibStepper" && parts[3] == "FibSink") {
                    parts[5].toIntOrNull()
                } else null
            }
            .take(expectedCount)
        assertEquals(expected, sniffValues)
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
    AsmParser.encodeInstructionsFromString(source.trimIndent()).joinToString("")

private fun generateFibonacci(count: Int): List<Int> {
    val result = mutableListOf<Int>()
    var a = 0
    var b = 1
    repeat(count) {
        result += a
        val next = a + b
        a = b
        b = next
    }
    return result
}
