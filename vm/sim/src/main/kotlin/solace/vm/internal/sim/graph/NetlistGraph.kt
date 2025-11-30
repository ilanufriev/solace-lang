package solace.vm.internal.sim.graph

import solace.vm.internal.sim.netlist.*

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

        return gr
    }
}

typealias DataType = Int
typealias FifoType = MutableList<DataType>
typealias LeafType = Leaf<DataType>
typealias WireType = Wire<DataType>

fun makeFifoTypeInstance(): FifoType {
    return mutableListOf<DataType>()
}

// This is the main VM part, a graph engine
class NetlistGraph {
    // Nodes of the graph (called Leaves as in Vivado)
    private val leaves = mutableMapOf<String, LeafType>()

    // Collection of leaves' default constructors
    private val leafCtorRegistry = mutableMapOf<String, () -> LeafType>()

    // Evaluation queue, leaves in this queue are evaluated in the order they were added to graph
    private val evalQueue = arrayListOf<String>()

    // Fifos provide a way to insert values to and extract values from the graph
    private val inputFifos = mutableMapOf<String, FifoType>()
    private val outputFifos = mutableMapOf<String, FifoType>()

    // Fifo name -> leaf name -> leaf port name
    private val inputFifoConnections = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val outputFifoConnections = mutableMapOf<String, MutableList<Pair<String, String>>>()

    private fun isLeafTypeRegistered(name: String): Boolean {
        return leafCtorRegistry.containsKey(name)
    }

    private fun hasLeaf(name: String): Boolean {
        return leaves.containsKey(name)
    }

    private fun getLeaf(name: String): LeafType {
        if (!hasLeaf(name)) {
            throw IllegalArgumentException("Leaf $name does not exist")
        }

        return leaves[name]!!
    }

    private fun getCtor(name: String): () -> LeafType {
        if (!isLeafTypeRegistered(name)) {
            throw IllegalArgumentException("Leaf type $name has not been registered")
        }

        return leafCtorRegistry[name]!!
    }

    private fun hasInputFifo(name: String): Boolean {
        return inputFifos.containsKey(name)
    }

    private fun getInputFifo(name: String): FifoType {
        if (hasInputFifo(name)) {
            return inputFifos[name]!!
        }

        throw IllegalArgumentException("Input fifo $name does not exist")
    }

    fun addInputFifo(name: String) {
        inputFifos[name] = makeFifoTypeInstance()
        inputFifoConnections[name] = mutableListOf<Pair<String, String>>()
    }

    private fun addInputFifoConnection(fifoName: String, leafName: String, leafPortName: String) {
        val fifo = getInputFifo(fifoName)
        val leaf = getLeaf(leafName)

        inputFifoConnections[fifoName]!!.addLast(Pair(leafName, leafPortName))
    }

    // Take data from input fifo and send it to the respective wire
    private fun pushDataFromInputFifo() {
        for ((fifoName, fifo) in inputFifos.entries) {
            for ((leafName, portName) in inputFifoConnections[fifoName]!!) {
                if (fifo.isEmpty()) {
                    throw NoSuchElementException("Not enough data in fifo $fifoName to provide inputs")
                }
                val leaf = getLeaf(leafName)
                leaf.getPort(portName).send(fifo.first())
                fifo.removeAt(0)
            }
        }
    }

    private fun hasOutputFifo(name: String): Boolean {
        return outputFifos.containsKey(name)
    }

    private fun getOutputFifo(name: String): FifoType {
        if (hasOutputFifo(name)) {
            return outputFifos[name]!!
        }

        throw IllegalArgumentException("Output fifo $name does not exist")
    }

    fun addOutputFifo(name: String) {
        outputFifos[name] = makeFifoTypeInstance()
        outputFifoConnections[name] = mutableListOf<Pair<String, String>>()
    }

    private fun addOutputFifoConnection(fifoName: String, leafName: String, leafPortName: String) {
        val fifo = getOutputFifo(fifoName)
        val leaf = getLeaf(leafName)

        outputFifoConnections[fifoName]!!.addLast(Pair(leafName, leafPortName))
    }

    // Pull data from respective wires into output fifo
    private fun pullDataToOutputFifo() {
        for ((fifoName, fifo) in outputFifos.entries) {
            for ((leafName, portName) in outputFifoConnections[fifoName]!!) {
                val leaf = getLeaf(leafName)
                val data = leaf.getPort(portName).receive() ?: 0
                fifo.addLast(data)
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
    fun conLeaf(fromName: String, fromPortName: String,
                toName: String, toPortName: String) {
        val from = getLeaf(fromName)
        val to = getLeaf(toName)

        val connection = WireType()
        from.connectPort(fromPortName, connection)
        to.connectPort(toPortName, connection)
    }

    // Connect leaf's port to immediate value
    fun conLeafToImmediate(toName: String, toPortName: String, imm: DataType) {
        val to = getLeaf(toName)

        val connection = WireType()
        connection.send(imm)
        to.connectPort(toPortName, connection)
    }

    // Connect leaf's port to input fifo
    fun conLeafToInputFifo(toName: String, toPortName: String, fifoName: String) {
        val to = getLeaf(toName)
        val fifo = getInputFifo(fifoName)
        val connection = WireType()

        to.connectPort(toPortName, connection)
        addInputFifoConnection(fifoName, toName, toPortName)
    }

    // Connect leaf's port to output fifo
    fun conLeafToOutputFifo(toName: String, toPortName: String, fifoName: String) {
        val to = getLeaf(toName)
        val fifo = getOutputFifo(fifoName)
        val connection = WireType()

        to.connectPort(toPortName, connection)
        addOutputFifoConnection(fifoName, toName, toPortName)
    }

    // Push immediate value into fifo
    fun pushImmToInputFifo(fifoName: String, imm: DataType) {
        val fifo = getInputFifo(fifoName)
        fifo.addLast(imm)
    }

    // Pull immediate value from output fifo
    fun pullImmFromOutputFifo(fifoName: String): DataType {
        val fifo = getOutputFifo(fifoName)
        if (fifo.isEmpty()) {
            throw NoSuchElementException("No elements in fifo $fifoName")
        }

        val last = fifo.last()
        fifo.removeLast()
        return last
    }

    fun getInputFifoSize(fifoName: String): Int {
        val fifo = getInputFifo(fifoName)
        return fifo.size
    }

    fun getOutputFifoSize(fifoName: String): Int {
        val fifo = getOutputFifo(fifoName)
        return fifo.size
    }

    fun evaluate() {
        pushDataFromInputFifo()

        for(leafName in evalQueue) {
            getLeaf(leafName).evaluate()
        }

        pullDataToOutputFifo()
    }
}