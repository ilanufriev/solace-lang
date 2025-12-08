package solace.network

import solace.utils.dotgen.DNP
import solace.utils.dotgen.DOTConnection
import solace.utils.dotgen.DOTNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.map

data class NodePorts(
    val inputs: Map<String, ReceiveChannel<Any?>>,
    val outputs: Map<String, SendChannel<Any?>>,
    val self: Map<String, Channel<Any?>>
)

data class NetworkNode(
    val descriptor: LoadedNode,
    val ports: NodePorts
)

data class BuiltNetwork(
    val nodes: List<NetworkNode>,
    val connections: List<Connection>
) {
    // Launch VMs for all nodes using the provided factory within the given scope.
    fun launch(scope: CoroutineScope, factory: NodeVmFactory = StubNodeVmFactory()): List<Job> =
        nodes.map { node -> factory.create(node).launch(scope) }

    fun toDOTNetwork(): DOTNetwork =
        DOTNetwork(connections.map { DOTConnection(DNP(it.from.node, it.from.port), DNP(it.to.node, it.to.port)) })
}

// Construct channel wiring for the loaded program, validating ports and producing runnable nodes.
fun buildNetwork(program: LoadedProgram): BuiltNetwork {
    val nodeLookup = program.nodes.associateBy { it.name }
    val portMaps = program.nodes.associate { node ->
        val inputs = mutableMapOf<String, ReceiveChannel<Any?>>()
        val outputs = mutableMapOf<String, SendChannel<Any?>>()
        val self = mutableMapOf<String, Channel<Any?>>()

        node.ports.self.forEach { selfPort ->
            val ch = Channel<Any?>(Channel.UNLIMITED)
            inputs[selfPort] = ch
            outputs[selfPort] = ch
            self[selfPort] = ch
        }

        node.name to Triple(inputs, outputs, self)
    }.toMutableMap()

    program.connections.forEach { connection ->
        val fromNode = nodeLookup[connection.from.node]
            ?: error("Unknown source node '${connection.from.node}'")
        val toNode = nodeLookup[connection.to.node]
            ?: error("Unknown target node '${connection.to.node}'")

        require(fromNode.ports.outputs.contains(connection.from.port)) {
            "Node '${fromNode.name}' has no output port '${connection.from.port}'"
        }
        require(toNode.ports.inputs.contains(connection.to.port)) {
            "Node '${toNode.name}' has no input port '${connection.to.port}'"
        }

        val channel = Channel<Any?>(Channel.UNLIMITED)
        val fromPorts = portMaps.getValue(fromNode.name)
        val toPorts = portMaps.getValue(toNode.name)

        check(!fromPorts.second.contains(connection.from.port)) {
            "Output port '${connection.from.port}' of node '${fromNode.name}' is already connected"
        }
        check(!toPorts.first.contains(connection.to.port)) {
            "Input port '${connection.to.port}' of node '${toNode.name}' is already connected"
        }

        fromPorts.second[connection.from.port] = channel
        toPorts.first[connection.to.port] = channel
    }

    val networkNodes = program.nodes.map { node ->
        val (inputs, outputs, self) = portMaps.getValue(node.name)
        val inputNames = node.ports.inputs + node.ports.self
        val outputNames = node.ports.outputs + node.ports.self
        NetworkNode(
            node,
            NodePorts(
                inputs = inputNames.associateWith { inputs[it] ?: Channel<Any?>(Channel.UNLIMITED) },
                outputs = outputNames.associateWith { outputs[it] ?: Channel<Any?>(Channel.UNLIMITED) },
                self = self
            )
        )
    }

    return BuiltNetwork(networkNodes, program.connections)
}

interface NodeVm {
    // Start this VM in the provided coroutine scope.
    fun launch(scope: CoroutineScope): Job
}

interface NodeVmFactory {
    // Build a NodeVm instance for the given network node.
    fun create(node: NetworkNode): NodeVm
}

class StubNodeVmFactory(
    private val logIntervalMs: Long = 1_000L,
    private val logPorts: Boolean = true
) : NodeVmFactory {
    // Validate node ports are wired as declared, then produce a LoggingNodeVm that only prints liveness ticks.
    override fun create(node: NetworkNode): NodeVm {
        validatePorts(node)
        if (logPorts) logPorts(node)
        return LoggingNodeVm(node.descriptor.name, node.descriptor.type, logIntervalMs)
    }

    private fun validatePorts(node: NetworkNode) {
        val descriptor = node.descriptor
        val ports = node.ports
        val expectedInputs = descriptor.ports.inputs.toSet()
        val expectedOutputs = descriptor.ports.outputs.toSet()
        val expectedSelf = descriptor.ports.self.toSet()
        val allowedInputs = expectedInputs + expectedSelf
        val allowedOutputs = expectedOutputs + expectedSelf

        fun check(label: String, expected: Set<String>, actual: Map<String, *>) {
            expected.forEach { port ->
                require(actual.containsKey(port)) {
                    "Node '${descriptor.name}' is missing $label port '$port'"
                }
            }
            val unexpected = actual.keys - expected
            require(unexpected.isEmpty()) {
                "Node '${descriptor.name}' has unexpected $label ports: ${unexpected.joinToString()}"
            }
        }

        check("input", allowedInputs, ports.inputs)
        check("output", allowedOutputs, ports.outputs)
        check("self", expectedSelf, ports.self)

        descriptor.ports.self.forEach { port ->
            val selfChannel = ports.self[port] ?: error("Node '${descriptor.name}' missing self channel for '$port'")
            require(ports.inputs[port] === selfChannel && ports.outputs[port] === selfChannel) {
                "Self port '$port' of node '${descriptor.name}' must share the same channel for input/output"
            }
        }
    }

    private fun logPorts(node: NetworkNode) {
        fun id(obj: Any?) = obj?.let { System.identityHashCode(it) }?.toString(16) ?: "null"
        val descriptor = node.descriptor
        println("Node '${descriptor.name}' ports:")
        println("  inputs : " + node.ports.inputs.entries.joinToString { "${it.key}->${id(it.value)}" })
        println("  outputs: " + node.ports.outputs.entries.joinToString { "${it.key}->${id(it.value)}" })
        println("  self   : " + node.ports.self.entries.joinToString { "${it.key}->${id(it.value)}" })
    }
}

private class LoggingNodeVm(
    private val name: String,
    private val type: NodeType,
    private val intervalMs: Long
) : NodeVm {
    private val counter = AtomicInteger(0)

    // Periodically log a heartbeat message until the scope is cancelled.
    override fun launch(scope: CoroutineScope): Job = scope.launch(Dispatchers.Default) {
        while (isActive) {
            val tick = counter.incrementAndGet()
            println("[${type.name.lowercase()}] $name alive (tick $tick)")
            delay(intervalMs)
        }
    }
}

/**
 * Convenience entry point for launching a loaded program with stub VMs.
 */
fun runNetwork(program: LoadedProgram, logIntervalMs: Long = 1_000L, logPorts: Boolean = false) = runBlocking {
    val network = buildNetwork(program)
    println("Launching ${network.nodes.size} node(s), ${network.connections.size} connection(s)")
    val jobs = network.launch(this, StubNodeVmFactory(logIntervalMs, logPorts))
    println("Network is running; press Ctrl+C to stop.")
    jobs.joinAll()
}
