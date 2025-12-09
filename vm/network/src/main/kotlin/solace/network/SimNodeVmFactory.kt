package solace.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.yield
import solace.vm.Simulator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.Path

// NodeVmFactory that boots the simulator per node and bridges network channels to simulator FIFOs.
class SimNodeVmFactory : NodeVmFactory {
    override fun create(node: NetworkNode): NodeVm {
        val program = parseSolbc(node.descriptor.bytecode)
        require(program.nodeType == node.descriptor.type) {
            "Node '${node.descriptor.name}' solbc type ${program.nodeType} does not match descriptor ${node.descriptor.type}"
        }

        val simulator = Simulator()
        val initCode = program.initSection.toString(StandardCharsets.UTF_8)
        val runCode = program.runSection.toString(StandardCharsets.UTF_8)
        val combinedBytecode = initCode + runCode
        simulator.loadByteCode(combinedBytecode)
        val initStatus = simulator.tryInit()
        check(initStatus == Simulator.ExecStatus.SUCCESS) {
            "Simulator init failed for node '${node.descriptor.name}' with status $initStatus"
        }

        val initGraph = simulator.dumpInitGraphToDOT()
        val runGraph = simulator.dumpRunGraphToDOT()

        Files.writeString(Path(node.descriptor.name + "_initGraph.dot"), initGraph)
        Files.writeString(Path(node.descriptor.name + "_runGraph.dot"), runGraph)

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
        // Deliver any data produced during init (e.g., self-loop seeds) before entering run loop.
        flushOutputs()
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
