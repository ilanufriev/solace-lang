package solace.vm

import solace.vm.internal.sim.asm.AsmParser
import solace.vm.internal.sim.asm.instructions.*
import solace.vm.internal.sim.graph.NetlistGraph
import solace.vm.internal.sim.graph.NetlistGraphFactory
import solace.vm.internal.sim.types.DataType

class Simulator {
    private val gr = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()

    private fun executeAndBuildGraph(instrs: List<Instruction>) {
        for (i in instrs) {
            when (i) {
                is New -> {
                    gr.addLeaf(i.leafName!!, i.leafType!!)
                }
                is Con -> {
                    gr.conLeaf(i.leafName1!!, i.leafPortName1!!, i.leafName2!!, i.leafPortName2!!)
                }
                is FifoCon -> {
                    gr.conLeafToFifo(i.leafName!!, i.leafPortName!!, i.fifoName!!)
                }
                is ImmCon -> {
                    gr.conLeafToImmediate(i.leafName!!, i.leafPortName!!, i.immediate!!.toInt())
                }
                is NewInFifo -> {
                    gr.addFifo(i.fifoName!!, NetlistGraph.FifoDirection.INPUT)
                }
                is NewOutFifo -> {
                    gr.addFifo(i.fifoName!!, NetlistGraph.FifoDirection.OUTPUT)
                }
                is NewLoopFifo -> {
                    val loopFifoInName = i.fifoName!! + "LoopIn"
                    val loopFifoOutName = i.fifoName!! + "LoopOut"
                    gr.addFifo(loopFifoInName, NetlistGraph.FifoDirection.INPUT)
                    gr.addFifo(loopFifoOutName, NetlistGraph.FifoDirection.OUTPUT)
                    gr.conFifos(loopFifoOutName, loopFifoInName)
                }
            }
        }
    }

    fun loadByteCode(bytecodeString: String) {
        val encodedInstrs = AsmParser.parseEncodedInstructions(bytecodeString)
        val instrsStrings = AsmParser.decodeInstructions(encodedInstrs)
        val instrs = AsmParser.parseIntoInstrs(instrsStrings)
        executeAndBuildGraph(instrs)
    }

    fun pushToFifo(fifoName: String, value: DataType) {
        gr.pushDataToFifo(fifoName, value)
    }

    fun pullFromFifo(fifoName: String): DataType {
        return gr.pullDataFromFifo(fifoName)
    }

    fun run() {
        gr.evaluate()
    }
}