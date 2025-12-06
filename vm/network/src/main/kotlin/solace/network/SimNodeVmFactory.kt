package solace.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.yield
import solace.vm.Simulator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

private const val SOLBC_MAGIC = "SOLB"
private const val SOLBC_HEADER_SIZE = 16

// Parsed solbc container for the sim VM: exposes init/run sections as ASCII-encoded simulator bytecode.
private data class SolbcProgram(
    val nodeType: NodeType,
    val initCode: String,
    val runCode: String
)

// NodeVmFactory that boots the simulator per node and bridges network channels to simulator FIFOs.
class SimNodeVmFactory : NodeVmFactory {
    override fun create(node: NetworkNode): NodeVm {
        val program = parseSolbc(node.descriptor.bytecode)
        require(program.nodeType == node.descriptor.type) {
            "Node '${node.descriptor.name}' solbc type ${program.nodeType} does not match descriptor ${node.descriptor.type}"
        }

        val simulator = Simulator()
        val combinedBytecode = program.initCode + program.runCode
        simulator.loadByteCode(combinedBytecode)
        val initStatus = simulator.tryInit()
        check(initStatus == Simulator.ExecStatus.SUCCESS) {
            "Simulator init failed for node '${node.descriptor.name}' with status $initStatus"
        }

        return SimNodeVm(node, simulator)
    }
}

private class SimNodeVm(
    private val node: NetworkNode,
    private val simulator: Simulator
) : NodeVm {
    private val inputPorts = node.ports.inputs
    private val outputPorts = node.ports.outputs

    override fun launch(scope: CoroutineScope): Job = scope.launch(Dispatchers.Default) {
        while (isActive) {
            val drained = drainInputs()
            val status = simulator.tryRun()
            when (status) {
                Simulator.ExecStatus.SUCCESS -> flushOutputs()
                Simulator.ExecStatus.BLOCKED -> {
                    if (!drained) waitForInput()
                }
                Simulator.ExecStatus.ERROR -> error("Simulator error in node '${node.descriptor.name}'")
            }
            yield()
        }
    }

    private suspend fun drainInputs(): Boolean {
        var drained = false
        inputPorts.forEach { (port, ch) ->
            while (true) {
                val result = ch.tryReceive()
                if (!result.isSuccess) break
                val value = result.getOrNull()
                simulator.pushToFifo(port, coerceToInt(port, value))
                drained = true
            }
        }
        return drained
    }

    private suspend fun flushOutputs() {
        outputPorts.forEach { (port, ch) ->
            while (true) {
                val size = runCatching { simulator.getFifoSize(port) }.getOrElse { break }
                if (size == 0) break
                val value = simulator.pullFromFifo(port)
                ch.send(value)
            }
        }
    }

    private suspend fun waitForInput() {
        select<Unit> {
            inputPorts.forEach { (port, ch) ->
                ch.onReceiveCatching { result ->
                    val value = result.getOrNull() ?: return@onReceiveCatching
                    simulator.pushToFifo(port, coerceToInt(port, value))
                }
            }
        }
    }

    private fun coerceToInt(port: String, value: Any?): Int =
        when (value) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Port '$port' of node '${node.descriptor.name}' expected Int, got ${value?.javaClass?.simpleName}")
        }
}

private fun parseSolbc(bytes: ByteArray): SolbcProgram {
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
    buffer.get() // isa_version
    buffer.get() // flags
    val initSize = buffer.int
    val runSize = buffer.int
    require(initSize >= 0 && runSize >= 0) { "Negative section size in solbc (init=$initSize, run=$runSize)" }
    val expected = SOLBC_HEADER_SIZE + initSize + runSize
    require(expected <= bytes.size) { "solbc size mismatch: expected at least $expected, got ${bytes.size}" }

    val initCode = bytes.copyOfRange(SOLBC_HEADER_SIZE, SOLBC_HEADER_SIZE + initSize)
        .toString(StandardCharsets.UTF_8)
    val runCode = bytes.copyOfRange(SOLBC_HEADER_SIZE + initSize, expected)
        .toString(StandardCharsets.UTF_8)

    return SolbcProgram(nodeType, initCode, runCode)
}
