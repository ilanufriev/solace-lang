package solace.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class RuntimeTest {

    @Test
    fun `buildNetwork wires self port to same channel`() {
        val node = LoadedNode(
            name = "Counter",
            type = NodeType.HARDWARE,
            ports = PortSignature(
                inputs = listOf("in"),
                outputs = listOf("out"),
                self = listOf("loop")
            ),
            bytecode = byteArrayOf() // not used by runtime wiring
        )
        val program = LoadedProgram(nodes = listOf(node), connections = emptyList())

        val built = buildNetwork(program)
        val ports = built.nodes.single().ports

        val selfCh = ports.self["loop"]
        assertNotNull(selfCh, "Self channel must exist")
        assertSame(selfCh, ports.inputs["loop"], "Self port must be present in inputs and share the same channel")
        assertSame(selfCh, ports.outputs["loop"], "Self port must be present in outputs and share the same channel")
        assertEquals(setOf("in", "loop"), ports.inputs.keys)
        assertEquals(setOf("out", "loop"), ports.outputs.keys)
    }

    @Test
    fun `stub factory rejects miswired self channel`() {
        val descriptor = LoadedNode(
            name = "Broken",
            type = NodeType.HARDWARE,
            ports = PortSignature(
                inputs = listOf("in"),
                outputs = listOf("out"),
                self = listOf("loop")
            ),
            bytecode = byteArrayOf()
        )

        val chIn = Channel<Any?>(Channel.UNLIMITED)
        val chOut = Channel<Any?>(Channel.UNLIMITED)
        val chSelf = Channel<Any?>(Channel.UNLIMITED)

        val node = NetworkNode(
            descriptor = descriptor,
            ports = NodePorts(
                inputs = mapOf("in" to chIn, "loop" to chIn),
                outputs = mapOf("out" to chOut, "loop" to chOut), // uses different channel
                self = mapOf("loop" to chSelf)
            )
        )

        assertFailsWith<IllegalArgumentException> {
            StubNodeVmFactory().create(node)
        }
    }

    @Test
    fun `stub factory accepts valid wiring`() {
        val descriptor = LoadedNode(
            name = "Ok",
            type = NodeType.SOFTWARE,
            ports = PortSignature(
                inputs = listOf("in"),
                outputs = listOf("out"),
                self = listOf("loop")
            ),
            bytecode = byteArrayOf()
        )

        val sharedLoop = Channel<Any?>(Channel.UNLIMITED)
        val node = NetworkNode(
            descriptor = descriptor,
            ports = NodePorts(
                inputs = mapOf("in" to Channel(Channel.UNLIMITED), "loop" to sharedLoop),
                outputs = mapOf("out" to Channel(Channel.UNLIMITED), "loop" to sharedLoop),
                self = mapOf("loop" to sharedLoop)
            )
        )

        // Should not throw
        StubNodeVmFactory().create(node)
    }

    @Test
    fun `sniffer forwards traffic between nodes`() = runBlocking {
        val nodeA = LoadedNode(
            name = "A",
            type = NodeType.SOFTWARE,
            ports = PortSignature(
                inputs = emptyList(),
                outputs = listOf("out"),
                self = emptyList()
            ),
            bytecode = byteArrayOf()
        )
        val nodeB = LoadedNode(
            name = "B",
            type = NodeType.SOFTWARE,
            ports = PortSignature(
                inputs = listOf("in"),
                outputs = emptyList(),
                self = emptyList()
            ),
            bytecode = byteArrayOf()
        )
        val program = LoadedProgram(
            nodes = listOf(nodeA, nodeB),
            connections = listOf(
                Connection(
                    Endpoint("A", "out"),
                    Endpoint("B", "in")
                )
            )
        )

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val network = buildNetwork(program, sniffConnections = true, snifferScope = scope)
        val sender = network.nodes.first { it.descriptor.name == "A" }.ports.outputs.getValue("out")
        val receiver = network.nodes.first { it.descriptor.name == "B" }.ports.inputs.getValue("in")

        sender.send(123)
        assertEquals(123, receiver.receive())
    }
}
