package solace.vm.internal.sim.graph

import solace.vm.internal.sim.netlist.*
import solace.vm.internal.sim.types.*

object NetlistGraphFactory {
    fun makeNetlistGraphWithRegisteredLeaves(): NetlistGraph {
        val gr = NetlistGraph()

        gr.registerLeafCtor(Adder::class.simpleName!!, ::Adder)
        gr.registerLeafCtor(CmpEq::class.simpleName!!, ::CmpEq)
        gr.registerLeafCtor(CmpLeq::class.simpleName!!, ::CmpLeq)
        gr.registerLeafCtor(CmpLess::class.simpleName!!, ::CmpLess)
        gr.registerLeafCtor(LogicAnd::class.simpleName!!, ::LogicAnd)
        gr.registerLeafCtor(LogicOr::class.simpleName!!, ::LogicOr)
        gr.registerLeafCtor(LogicNot::class.simpleName!!, ::LogicNot)
        gr.registerLeafCtor(Multiplier::class.simpleName!!, ::Multiplier)
        gr.registerLeafCtor(Mux2::class.simpleName!!, ::Mux2)
        gr.registerLeafCtor(Demux2::class.simpleName!!, ::Demux2)
        gr.registerLeafCtor(Register::class.simpleName!!, ::Register)
        gr.registerLeafCtor(Fifo::class.simpleName!!, ::Fifo)
        gr.registerLeafCtor(Divider::class.simpleName!!, ::Divider)
        gr.registerLeafCtor(RBitShift::class.simpleName!!, ::RBitShift)
        gr.registerLeafCtor(LBitShift::class.simpleName!!, ::LBitShift)

        return gr
    }
}

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

    private fun isLeafTypeRegistered(name: String): Boolean {
        return leafCtorRegistry.containsKey(name)
    }

    private fun hasLeaf(name: String): Boolean {
        return leaves.containsKey(name)
    }

    @Throws(IllegalArgumentException::class)
    fun getLeaf(name: String): LeafType {
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
        val matches = leaves.filter {
            (leafName: String, leaf: LeafType) -> leaf is Fifo
        }

        return matches.containsKey(name)
    }

    @Throws(IllegalArgumentException::class)
    private fun getFifo(name: String): LeafType {
        if (hasFifo(name)) {
            return leaves[name]!!
        }

        throw IllegalArgumentException("Fifo with name $name does not exist")
    }

    private fun getFifos(): Map<String, LeafType> {
        return leaves.filter { (name: String, leaf: LeafType) -> leaf is Fifo }
    }

    fun getFifoNames(): Set<String> {
        return leaves.filter { (name: String, leaf: LeafType) -> leaf is Fifo }.keys
    }

    fun addFifo(name: String) {
        addLeaf(name, "Fifo")
    }

    // Take data from fifos and send it to the respective wires
    private fun pushDataFromFifosToLeaves(evalQueue: List<String>) {
        val fifos = getFifos()

        for ((leafName, leaf) in fifos.entries) {
            if (!evalQueue.contains(leafName)) {
                continue
            }

            // At this point we know for sure, that this is a fifo
            val fifo = leaf as Fifo
            fifo.pushToOutputs()
        }
    }

    // Pull data from respective wires into fifos
    private fun pullDataFromLeavesToFifos(evalQueue: List<String>) {
        val fifos = getFifos()

        for ((leafName, leaf) in fifos.entries) {
            if (!evalQueue.contains(leafName)) {
                continue
            }

            val fifo = leaf as Fifo

            if (!fifo.isPortConnected("in")) {
                continue
            }

            fifo.pullFromInputs()
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

    // Sends an immediate value into leaf port's wire. Should only be used with
    // leaf ports that are connected to immediate values
    fun setLeafPortToImmediate(leafName: String, leafPortName: String, imm: DataType) {
        val leaf = getLeaf(leafName)
        leaf.getPort(leafPortName).send(imm)
    }

    fun pushDataToFifo(fifoName: String, data:  DataType) {
        val fifo = getFifo(fifoName) as Fifo
        fifo.pushToFifoDirectly(data);
    }

    // Pull immediate value from output fifo
    fun pullDataFromFifo(fifoName: String): DataType {
        val fifo = getFifo(fifoName) as Fifo
        return fifo.pullFromFifoDirectly()
    }

    fun getFifoSize(fifoName: String): Int {
        val fifo = getFifo(fifoName) as Fifo
        return fifo.queue.size
    }

    fun evaluate() {
        evaluate(evalQueue)
    }

    fun evaluate(evalQueue: List<String>) {
        for ((leafName, leaf) in leaves) {
            if (leaf is Fifo) {
                leaf.pushToOutputs()
            }
        }

        for (leafName in evalQueue) {
            val leaf = getLeaf(leafName)
            //if (leaf is Fifo) {
                // if (leaf.isPortConnected("in")) {
                //    leaf.pullFromInput()
                //}
                //leaf.pushToOutputs()
            //}

            getLeaf(leafName).evaluate()
        }

        for ((leafName, leaf) in leaves) {
            if (leaf is Fifo) {
                leaf.pullFromInputs()
            }
        }
    }
}