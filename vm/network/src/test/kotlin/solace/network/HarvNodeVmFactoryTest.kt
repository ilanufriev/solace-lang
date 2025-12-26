package solace.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import solace.vm.internal.harv.asm.AsmParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SOLBC_HEADER_SIZE = 16

class HarvNodeVmFactoryTest {
    @Test
    fun `harv factory echoes input to output`() = runBlocking {
        val echoNode = LoadedNode(
            name = "Echo",
            type = NodeType.SOFTWARE,
            ports = PortSignature(inputs = listOf("in"), outputs = listOf("out"), self = emptyList()),
            bytecode = solbc(NodeType.SOFTWARE, initCode = echoInit(), runCode = echoRun())
        )

        val network = buildNetwork(LoadedProgram(nodes = listOf(echoNode), connections = emptyList()))
        val jobs = network.launch(this, HarvNodeVmFactory())

        val input = network.nodes.single { it.descriptor.name == "Echo" }.ports.inputs.getValue("in") as Channel<Any?>
        val output = network.nodes.single { it.descriptor.name == "Echo" }.ports.outputs.getValue("out") as Channel<Any?>

        input.send(10)
        input.send(20)

        assertEquals(10, output.receive())
        assertEquals(20, output.receive())

        jobs.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `harv factory computes fibonacci sequence with software nodes`() = runBlocking {
        val tick = LoadedNode(
            name = "TickSource",
            type = NodeType.SOFTWARE,
            ports = PortSignature(inputs = emptyList(), outputs = listOf("tick"), self = emptyList()),
            bytecode = solbc(NodeType.SOFTWARE, initCode = tickInit(), runCode = tickRun())
        )

        val fib = LoadedNode(
            name = "FibStepper",
            type = NodeType.SOFTWARE,
            ports = PortSignature(inputs = listOf("step"), outputs = listOf("fib"), self = emptyList()),
            bytecode = solbc(NodeType.SOFTWARE, initCode = fibInit(), runCode = fibRun())
        )

        val sink = LoadedNode(
            name = "FibSink",
            type = NodeType.SOFTWARE,
            ports = PortSignature(inputs = listOf("inFib"), outputs = listOf("last"), self = emptyList()),
            bytecode = solbc(NodeType.SOFTWARE, initCode = sinkInit(), runCode = sinkRun())
        )

        val expectedCount = 45
        val sniffFile = java.nio.file.Path.of("build/harv-fib-sniff.csv").also {
            Files.createDirectories(it.parent)
            Files.deleteIfExists(it)
        }

        val program = LoadedProgram(
            nodes = listOf(tick, fib, sink),
            connections = listOf(
                Connection(Endpoint("TickSource", "tick"), Endpoint("FibStepper", "step")),
                Connection(Endpoint("FibStepper", "fib"), Endpoint("FibSink", "inFib"))
            )
        )

        val harvFactory = HarvNodeVmFactory()
        val factory = object : NodeVmFactory {
            override fun create(node: NetworkNode): NodeVm =
                if (node.descriptor.name == "TickSource") {
                    object : NodeVm {
                        override fun launch(scope: kotlinx.coroutines.CoroutineScope): Job = scope.launch {
                            repeat(expectedCount) { tick ->
                                node.ports.outputs.getValue("tick").send(tick + 1)
                                yield()
                            }
                        }
                    }
                } else {
                    harvFactory.create(node)
                }
        }

        runNetwork(
            program,
            sniffConnections = true,
            vmFactory = factory,
            stopAfterMs = 15_000,
            sniffLimit = expectedCount,
            sniffCsv = true,
            sniffCsvFile = sniffFile
        )

        val expected = generateFibonacci(expectedCount + 2).drop(2)
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

private fun echoInit(): String = encodeAsm(
    """
    .define %fifo ${'$'}in ?
    .define %fifo ${'$'}out ?
    """
)

private fun echoRun(): String = encodeAsm(
    """
    .push ${'$'}in
    .put ${'$'}out
    """
)

private fun tickInit(): String = encodeAsm(
    """
    .define %fifo ${'$'}tick ?
    .define %int ${'$'}n ?
    .push #0 ?
    .put ${'$'}n ?
    """
)

private fun tickRun(): String = encodeAsm(
    """
    .push ${'$'}n
    .push #1
    .add
    .put ${'$'}n
    .push ${'$'}n
    .put ${'$'}tick
    """
)

private fun fibInit(): String = encodeAsm(
    """
    .define %fifo ${'$'}step ?
    .define %fifo ${'$'}fib ?
    .define %int ${'$'}t ?
    .define %int ${'$'}next ?
    .define %int ${'$'}prev ?
    .define %int ${'$'}curr ?
    .push #0 ?
    .put ${'$'}prev ?
    .push #1 ?
    .put ${'$'}curr ?
    """
)

private fun fibRun(): String = encodeAsm(
    """
    .push ${'$'}step
    .put ${'$'}t
    .push ${'$'}prev
    .push ${'$'}curr
    .add
    .put ${'$'}next
    .push ${'$'}next
    .put ${'$'}fib
    .push ${'$'}curr
    .put ${'$'}prev
    .push ${'$'}next
    .put ${'$'}curr
    """
)

private fun sinkInit(): String = encodeAsm(
    """
    .define %fifo ${'$'}inFib ?
    .define %fifo ${'$'}last ?
    """
)

private fun sinkRun(): String = encodeAsm(
    """
    .push ${'$'}inFib
    .put ${'$'}last
    """
)

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
