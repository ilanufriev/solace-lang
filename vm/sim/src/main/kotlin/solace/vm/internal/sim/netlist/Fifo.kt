package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.*

class Fifo : LeafType() {
    class FifoIsEmptyException(val port: String) : NoSuchElementException("Fifo is empty. Could not push to port $port")
    class FifoReceivedNullException() : IllegalArgumentException("Fifo received null on input")

    var queue = mutableListOf<DataType>()
    override val ports: MutableMap<String, Wire<DataType>?> = mutableMapOf(
        "in" to null,
        "size" to null
    )

    @Throws(FifoIsEmptyException::class)
    fun pushToOutputs() {
        for ((portName, _) in ports) {
            if (portName == "in" || portName == "size") {
                continue
            }

            if (queue.isEmpty()) {
                throw FifoIsEmptyException(portName)
            }

            getPort(portName).send(queue.removeAt(0))
        }
    }

    @Throws(FifoReceivedNullException::class)
    fun pullFromInput() {
        queue.addLast(getPort("in").receive()
            ?: return
        )
    }

    fun pushToFifoDirectly(data: DataType) {
        queue.addLast(data)
    }

    @Throws(FifoIsEmptyException::class)
    fun pullFromFifoDirectly(): DataType {
        if (queue.isEmpty()) {
            throw FifoIsEmptyException("direct pull")
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