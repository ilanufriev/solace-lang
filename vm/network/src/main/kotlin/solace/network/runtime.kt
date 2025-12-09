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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Boolean
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
    val connections: List<Connection>,
    val sniffers: List<Job> = emptyList(),
    val sniffWriter: java.io.BufferedWriter? = null,
) {
    // Launch VMs for all nodes using the provided factory within the given scope.
    fun launch(scope: CoroutineScope, factory: NodeVmFactory = StubNodeVmFactory()): List<Job> =
        nodes.map { node -> factory.create(node).launch(scope) }

    fun toDOTNetwork(): DOTNetwork =
        DOTNetwork(connections.map { DOTConnection(DNP(it.from.node, it.from.port), DNP(it.to.node, it.to.port)) })
}

// Construct channel wiring for the loaded program, validating ports and producing runnable nodes.
fun buildNetwork(
    program: LoadedProgram,
    sniffConnections: Boolean = false,
    snifferScope: CoroutineScope? = null,
    sniffLimit: Int? = null,
    sniffCsv: Boolean = false,
    sniffCsvFile: java.nio.file.Path? = null,
): BuiltNetwork {
    require(!(sniffConnections && snifferScope == null)) {
        "Sniffing connections requires a coroutine scope"
    }

    val sniffStartTimeNs = System.nanoTime()
    val nodeLookup = program.nodes.associateBy { it.name }
    val snifferJobs = mutableListOf<Job>()
    val sniffLock = Any()
    val sniffWriter = sniffCsvFile?.let { path ->
        val parent = path.parent ?: path.toAbsolutePath().parent
        if (parent != null) {
            java.nio.file.Files.createDirectories(parent)
        }
        java.nio.file.Files.newBufferedWriter(path)
    }
    val portMaps = program.nodes.associate { node ->
        val inputs = mutableMapOf<String, ReceiveChannel<Any?>>()
        val outputs = mutableMapOf<String, SendChannel<Any?>>()
        val self = mutableMapOf<String, Channel<Any?>>()

        node.ports.self.forEach { selfPort ->
            // Bounded to prevent runaway growth on self ports
            val ch = Channel<Any?>(Channel.BUFFERED)
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

        val (outChannel, inChannel, snifferJob) = createChannel(
            connection,
            sniffConnections,
            snifferScope,
            sniffLimit,
            sniffCsv,
            sniffStartTimeNs,
            sniffWriter,
            sniffLock
        )
        if (snifferJob != null) {
            snifferJobs += snifferJob
        }
        val fromPorts = portMaps.getValue(fromNode.name)
        val toPorts = portMaps.getValue(toNode.name)

        check(!fromPorts.second.contains(connection.from.port)) {
            "Output port '${connection.from.port}' of node '${fromNode.name}' is already connected"
        }
        check(!toPorts.first.contains(connection.to.port)) {
            "Input port '${connection.to.port}' of node '${toNode.name}' is already connected"
        }

        fromPorts.second[connection.from.port] = outChannel
        toPorts.first[connection.to.port] = inChannel
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

    return BuiltNetwork(networkNodes, program.connections, snifferJobs, sniffWriter)
}

private fun createChannel(
    connection: Connection,
    sniff: Boolean,
    scope: CoroutineScope?,
    sniffLimit: Int?,
    sniffCsv: Boolean,
    sniffStartTimeNs: Long,
    sniffWriter: java.io.BufferedWriter?,
    sniffLock: Any
): Triple<SendChannel<Any?>, ReceiveChannel<Any?>, Job?> {
    if (!sniff) {
        // Bounded channels provide backpressure between nodes
        val ch = Channel<Any?>(Channel.BUFFERED)
        return Triple(ch, ch, null)
    }
    val snifferScope = scope ?: error("Sniffer scope is required when sniffing is enabled")
    val wire = Channel<Any?>(Channel.BUFFERED)
    val deliver = Channel<Any?>(Channel.BUFFERED)
    var remaining = sniffLimit ?: Int.MAX_VALUE
    val job = snifferScope.launch(Dispatchers.Default) {
        try {
            for (value in wire) {
                if (remaining > 0) {
                    val timestampNs = System.nanoTime() - sniffStartTimeNs
                    if (sniffCsv) {
                        val line = "$timestampNs,${connection.from.node},${connection.from.port},${connection.to.node},${connection.to.port},$value"
                        if (sniffWriter != null) {
                            synchronized(sniffLock) {
                                sniffWriter.write(line)
                                sniffWriter.newLine()
                                sniffWriter.flush()
                            }
                        } else {
                            println(line)
                        }
                    } else {
                        println("[sniff t=${timestampNs}ns] ${connection.from.node}.${connection.from.port} -> ${connection.to.node}.${connection.to.port}: $value")
                    }
                    remaining--
                    if (remaining == 0 && sniffLimit != null) {
                        System.err.println("[sniff] limit reached for ${connection.from.node}.${connection.from.port} -> ${connection.to.node}.${connection.to.port}, muting further output")
                    }
                }
                deliver.send(value)
            }
        } finally {
            deliver.close()
        }
    }
    return Triple(wire, deliver, job)
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
fun runNetwork(
    program: LoadedProgram,
    logIntervalMs: Long = 1_000L,
    logPorts: Boolean = false,
    sniffConnections: Boolean = false,
    vmFactory: NodeVmFactory? = null,
    stopAfterMs: Long? = null,
    sniffLimit: Int? = null,
    sniffCsv: Boolean = false,
    sniffCsvFile: java.nio.file.Path? = null
) = runBlocking {
    val network = buildNetwork(program, sniffConnections, this, sniffLimit, sniffCsv, sniffCsvFile)
    println("Launching ${network.nodes.size} node(s), ${network.connections.size} connection(s)")
    val factory = vmFactory ?: StubNodeVmFactory(logIntervalMs, logPorts)
    val jobs = network.launch(this, factory)
    if (stopAfterMs != null) {
        println("Network will stop after ${stopAfterMs} ms.")
        val completed = withTimeoutOrNull(stopAfterMs) { jobs.joinAll() } != null
        if (!completed) {
            println("Time limit reached, stopping network.")
        }
        jobs.forEach { it.cancelAndJoin() }
        network.sniffers.forEach { it.cancelAndJoin() }
        network.sniffWriter?.close()
    } else {
        println("Network is running; press Ctrl+C to stop.")
        jobs.joinAll()
        network.sniffers.forEach { it.join() }
        network.sniffWriter?.close()
    }
}
