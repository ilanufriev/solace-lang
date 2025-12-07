package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.*

class Fifo : LeafType() {
    class FifoIsEmptyException() : NoSuchElementException("Fifo is empty")
    class FifoReceivedNullException() : IllegalArgumentException("Fifo received null on input")

    var queue = mutableListOf<DataType>()
    override val ports: MutableMap<String, Wire<DataType>?> = mutableMapOf(
        "size" to null
    )

    @Throws(FifoIsEmptyException::class)
    fun pushToOutputs() {
        val outputs = ports.filter {
            (portName, _) -> portName.startsWith("out")
        }

        if (outputs.keys.size > queue.size) {
            throw FifoIsEmptyException()
        }

        for ((portName, _) in outputs) {
            getPort(portName).send(queue.removeAt(0))
        }
    }

    @Throws(FifoReceivedNullException::class)
    fun pullFromInputs() {
        val inputs = ports.filter {
            (portName, _) -> portName.startsWith("in")
        }

        for((portName, _) in inputs) {
            if (!portName.startsWith("in")) {
                continue
            }
            val value = getPort(portName).receive() ?: continue
            queue.addLast(value)
        }
    }

    fun pushToFifoDirectly(data: DataType) {
        queue.addLast(data)
    }

    @Throws(FifoIsEmptyException::class)
    fun pullFromFifoDirectly(): DataType {
        if (queue.isEmpty()) {
            throw FifoIsEmptyException()
        }

        return queue.removeAt(0)
    }

    override fun evaluate() {
        if (isPortConnected("size")) {
            getPort("size").send(queue.size)
        }
    }

    override fun connectPort(name: String, wire: WireType?) {
        if (!portExists(name)) {
            ports[name] = null
        }

        ports[name] = wire
    }

    override fun isPortConnected(name: String): Boolean {
        if (!portExists(name)) {
            ports[name] = null
        }

        return super.isPortConnected(name)
    }
}