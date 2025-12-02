package solace.vm.internal.sim.graph

import solace.vm.internal.sim.netlist.*
import solace.vm.internal.sim.types.*

object NetlistGraphFactory {
    fun makeNetlistGraphWithRegisteredLeaves(): NetlistGraph {
        val gr = NetlistGraph()

        gr.registerLeafCtor("Adder", ::Adder)
        gr.registerLeafCtor("CmpEq", ::CmpEq)
        gr.registerLeafCtor("CmpLeq", ::CmpLeq)
        gr.registerLeafCtor("CmpLess", ::CmpLess)
        gr.registerLeafCtor("LogicAnd", ::LogicAnd)
        gr.registerLeafCtor("LogicOr", ::LogicOr)
        gr.registerLeafCtor("LogicNot", ::LogicNot)
        gr.registerLeafCtor("Multiplier", ::Multiplier)
        gr.registerLeafCtor("Mux2", ::Mux2)
        gr.registerLeafCtor("Demux2", ::Demux2)
        gr.registerLeafCtor("Register", ::Register)

        return gr
    }
}

fun makeFifoTypeInstance(): FifoType {
    return mutableListOf<DataType>()
}

// This is the main VM part, a graph engine
class NetlistGraph {
    enum class FifoDirection {
        INPUT,
        OUTPUT,
    }

    data class Fifo(val queue: FifoType, val direction: FifoDirection)
    data class FifoConnection(val leafName: String, val leafPortName: String)

    // Nodes of the graph (called Leaves as in Vivado)
    private val leaves = mutableMapOf<String, LeafType>()

    // Collection of leaves' default constructors
    private val leafCtorRegistry = mutableMapOf<String, () -> LeafType>()

    // Evaluation queue, leaves in this queue are evaluated in the order they were added to graph
    private val evalQueue = arrayListOf<String>()

    // Fifos provide a way to insert values to and extract values from the graph
    private val fifos = mutableMapOf<String, Fifo>()

    // Output fifo name -> input fifo name
    private val fifoLoops = mutableMapOf<String, String>()

    // Fifo name -> leaf name -> leaf port name
    private val fifoConnections = mutableMapOf<String, MutableList<FifoConnection>>()

    private fun isLeafTypeRegistered(name: String): Boolean {
        return leafCtorRegistry.containsKey(name)
    }

    private fun hasLeaf(name: String): Boolean {
        return leaves.containsKey(name)
    }

    @Throws(IllegalArgumentException::class)
    private fun getLeaf(name: String): LeafType {
        if (!hasLeaf(name)) {
            throw IllegalArgumentException("Leaf $name does not exist")
        }

        return leaves[name]!!
    }

    @Throws(IllegalArgumentException::class)
    private fun getCtor(name: String): () -> LeafType {
        if (!isLeafTypeRegistered(name)) {
            throw IllegalArgumentException("Leaf type $name has not been registered")
        }

        return leafCtorRegistry[name]!!
    }

    private fun hasFifo(name: String): Boolean {
        return fifos.containsKey(name)
    }

    @Throws(IllegalArgumentException::class)
    private fun getFifo(name: String, direction: FifoDirection): Fifo {
        if (hasFifo(name) && fifos[name]!!.direction == direction) {
            return fifos[name]!!
        }

        throw IllegalArgumentException("Fifo with name $name and direction ${direction.name} does not exist")
    }

    @Throws(IllegalArgumentException::class)
    private fun getFifo(name: String): Fifo {
        if (hasFifo(name)) {
            return fifos[name]!!
        }

        throw IllegalArgumentException("Fifo with name $name does not exist")
    }

    fun addFifo(name: String, direction: FifoDirection) {
        fifos[name] = Fifo(makeFifoTypeInstance(), direction)
        fifoConnections[name] = mutableListOf<FifoConnection>()
    }

    private fun addFifoConnection(fifoName: String, direction: FifoDirection, leafName: String, leafPortName: String) {
        val fifo = getFifo(fifoName, direction)
        val leaf = getLeaf(leafName)

        fifoConnections[fifoName]!!.addLast(FifoConnection(leafName, leafPortName))
    }

    private fun addFifoConnection(fifoName: String, leafName: String, leafPortName: String) {
        val fifo = getFifo(fifoName)
        val leaf = getLeaf(leafName)

        fifoConnections[fifoName]!!.addLast(FifoConnection(leafName, leafPortName))
    }

    // Take data from fifos and send it to the respective wires
    @Throws(NoSuchElementException::class)
    private fun pushDataFromFifosToLeaves(direction: FifoDirection) {
        for ((fifoName, fifo) in fifos.entries) {
            if (fifo.direction != direction) {
                continue
            }

            for ((leafName, portName) in fifoConnections[fifoName]!!) {
                if (fifo.queue.isEmpty()) {
                    throw NoSuchElementException("Not enough data in fifo $fifoName to provide inputs")
                }

                val leaf = getLeaf(leafName)
                leaf.getPort(portName).send(fifo.queue.first())
                fifo.queue.removeAt(0)
            }
        }
    }

    // Pull data from respective wires into fifos
    private fun pullDataFromLeavesToFifos(direction: FifoDirection) {
        for ((fifoName, fifo) in fifos.entries) {
            if (fifo.direction != direction) {
                continue
            }

            for ((leafName, portName) in fifoConnections[fifoName]!!) {
                val leaf = getLeaf(leafName)
                val data = leaf.getPort(portName).receive() ?: 0
                fifo.queue.addLast(data)
            }
        }
    }

    fun addLeaf(name: String, typeName: String) {
        val ctor = getCtor(typeName)
        leaves[name] = ctor.invoke()
        evalQueue.addLast(name)
    }

    fun registerLeafCtor(name: String, ref: () -> LeafType) {
        leafCtorRegistry[name] = ref
    }

    // Connect leaves' ports to each other
    @Throws(IllegalArgumentException::class)
    fun conLeaf(fromName: String, fromPortName: String,
                toName: String, toPortName: String) {
        val from = getLeaf(fromName)
        val to = getLeaf(toName)

        val connection = WireType()

        // One to many connection type (usually one output -> many inputs)
        if (from.isPortConnected(fromPortName)) {
            if (to.isPortConnected(toPortName)) {
                throw IllegalArgumentException("Cannot connect multiple wires to one input")
            }

            to.connectPort(toPortName, from.getPort(fromPortName))
        } else {
            from.connectPort(fromPortName, connection)
            to.connectPort(toPortName, connection)
        }
    }

    // Connect leaf's port to immediate value
    fun conLeafToImmediate(toName: String, toPortName: String, imm: DataType) {
        val to = getLeaf(toName)

        val connection = WireType()
        connection.send(imm)
        to.connectPort(toPortName, connection)
    }

    // Connect leaf's port to input fifo
    fun conLeafToFifo(toName: String, toPortName: String, fifoName: String) {
        val to = getLeaf(toName)
        val fifo = getFifo(fifoName)
        val connection = WireType()

        to.connectPort(toPortName, connection)
        addFifoConnection(fifoName, toName, toPortName)
    }

    fun conFifos(fromFifoName: String, toFifoName: String) {
        val fromFifo = getFifo(fromFifoName)
        val toFifo = getFifo(toFifoName)
        fifoLoops[fromFifoName] = toFifoName
    }

    // Push immediate value into fifo
    fun pushDataToFifo(fifoName: String, imm: DataType) {
        val fifo = getFifo(fifoName)
        fifo.queue.addLast(imm)
    }

    // Pull immediate value from output fifo
    @Throws(NoSuchElementException::class)
    fun pullDataFromFifo(fifoName: String): DataType {
        val fifo = getFifo(fifoName)
        if (fifo.queue.isEmpty()) {
            throw NoSuchElementException("No elements in fifo $fifoName")
        }

        val last = fifo.queue.last()
        fifo.queue.removeLast()
        return last
    }

    fun getFifoSize(fifoName: String): Int {
        val fifo = getFifo(fifoName)
        return fifo.queue.size
    }

    fun evaluate() {
        pushDataFromFifosToLeaves(FifoDirection.INPUT)

        for(leafName in evalQueue) {
            getLeaf(leafName).evaluate()
        }

        pullDataFromLeavesToFifos(FifoDirection.OUTPUT)

        for ((fromFifoName, toFifoName) in fifoLoops) {
            val imm = pullDataFromFifo(fromFifoName)
            pushDataToFifo(toFifoName, imm)
        }
    }
}