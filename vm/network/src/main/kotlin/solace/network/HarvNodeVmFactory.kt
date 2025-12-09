package solace.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.yield
import solace.vm.StackMachine
import java.nio.charset.StandardCharsets

// NodeVmFactory that boots the Harv stack machine for software nodes.
class HarvNodeVmFactory : NodeVmFactory {
    override fun create(node: NetworkNode): NodeVm {
        val program = parseSolbc(node.descriptor.bytecode)
        require(program.nodeType == node.descriptor.type) {
            "Node '${node.descriptor.name}' solbc type ${program.nodeType} does not match descriptor ${node.descriptor.type}"
        }
        require(node.descriptor.type == NodeType.SOFTWARE) {
            "Harv VM supports only software nodes, got ${node.descriptor.type} for '${node.descriptor.name}'"
        }

        val machine = StackMachine()
        val initCode = program.initSection.toString(StandardCharsets.UTF_8)
        val runCode = program.runSection.toString(StandardCharsets.UTF_8)
        machine.loadByteCode(initCode + runCode)
        val initStatus = machine.tryInit()
        check(initStatus == StackMachine.ExecStatus.SUCCESS) {
            "Harv VM init failed for node '${node.descriptor.name}' with status $initStatus"
        }

        return HarvNodeVm(node, machine)
    }
}

private class HarvNodeVm(
    private val node: NetworkNode,
    private val machine: StackMachine
) : NodeVm {
    private val inputPorts = node.ports.inputs
    private val outputPorts = node.ports.outputs

    override fun launch(scope: CoroutineScope): Job = scope.launch(Dispatchers.Default) {
        flushOutputs()
        while (isActive) {
            val drained = drainInputs()
            when (machine.tryRun()) {
                StackMachine.ExecStatus.SUCCESS -> flushOutputs()
                StackMachine.ExecStatus.BLOCKED -> if (!drained) waitForInput()
                StackMachine.ExecStatus.ERROR -> error("Harv VM error in node '${node.descriptor.name}'")
            }
            yield()
        }
    }

    private suspend fun drainInputs(): Boolean {
        var drained = false
        inputPorts.forEach { (port, ch) ->
            val result = ch.tryReceive()
            if (result.isSuccess) {
                val value = result.getOrNull() ?: return@forEach
                machine.pushToFifo(port, coerceToInt(port, value))
                drained = true
            }
        }
        return drained
    }

    private suspend fun flushOutputs() {
        outputPorts.forEach { (port, ch) ->
            while (true) {
                val size = runCatching { machine.getFifoSize(port) }.getOrElse { break }
                if (size == 0) break
                val value = machine.pullFromFifo(port)
                ch.send(value)
            }
        }
    }

    private suspend fun waitForInput() {
        select<Unit> {
            inputPorts.forEach { (port, ch) ->
                ch.onReceiveCatching { result ->
                    val value = result.getOrNull() ?: return@onReceiveCatching
                    machine.pushToFifo(port, coerceToInt(port, value))
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
