package solace.vm

import solace.vm.internal.sim.asm.AsmParser
import solace.vm.internal.sim.asm.instructions.*
import solace.vm.internal.sim.graph.NetlistGraph
import solace.vm.internal.sim.netlist.Fifo
import solace.vm.internal.sim.graph.NetlistGraphFactory
import solace.vm.internal.sim.types.DataType

class Simulator {
    enum class ExecStatus {
        BLOCKED,
        SUCCESS,
        ERROR,
    }

    private val runGraph = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()
    private val initGraph = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()

    private fun executeAndBuildGraph(instrs: List<Instruction>) {
        for (i in instrs) {
            val targetGraph = if (i.isInit) initGraph else runGraph
            when (i) {
                is New -> {
                    targetGraph.addLeaf(i.leafName!!, i.leafType!!)
                }
                is Con -> {
                    targetGraph.conLeaf(i.fromLeafName!!, i.fromLeafPortName!!, i.toLeafName!!, i.toLeafPortName!!)
                }
                is ImmCon -> {
                    targetGraph.conLeafToImmediate(i.leafName!!, i.leafPortName!!, i.immediate!!.toInt())
                }
            }
        }
    }

    fun loadByteCode(byteCode: String) {
        val encodedInstrs = AsmParser.parseEncodedInstructions(byteCode)
        val instrsStrings = AsmParser.decodeInstructions(encodedInstrs)
        val instrs = AsmParser.parseIntoInstrs(instrsStrings)
        executeAndBuildGraph(instrs)
    }

    fun pushToFifo(fifoName: String, value: DataType) {
        runGraph.pushDataToFifo(fifoName, value)
    }

    fun pullFromFifo(fifoName: String): DataType {
        return runGraph.pullDataFromFifo(fifoName)
    }

    fun getFifoSize(fifoName: String): Int {
        return runGraph.getFifoSize(fifoName)
    }

    private fun transferFifoContentsToOtherGraph(fromGraph: NetlistGraph, toGraph: NetlistGraph, fifoName: String) {
        val from = fromGraph.getLeaf(fifoName) as Fifo
        val to = toGraph.getLeaf(fifoName) as Fifo

        to.queue.clear()
        to.queue.addAll(from.queue)
    }

    fun tryInit(): ExecStatus {
        try {
            initGraph.evaluate()

            for (fifoName in initGraph.getFifoNames()) {
                transferFifoContentsToOtherGraph(initGraph, runGraph, fifoName)
            }
        } catch (_: Fifo.FifoIsEmptyException) {
            return ExecStatus.BLOCKED
        } catch (e: Exception) {
            System.err.println(e.message)
            return ExecStatus.ERROR
        }

        return ExecStatus.SUCCESS
    }

    fun tryRun(): ExecStatus {
        try {
            runGraph.evaluate()
        } catch (e: Fifo.FifoIsEmptyException) {
            return ExecStatus.BLOCKED
        } catch (e: Exception) {
            e.printStackTrace()
            return ExecStatus.ERROR
        }

        return ExecStatus.SUCCESS
    }

    fun dumpInitGraphToDOT(): String {
        return initGraph.toDOTNetwork().toString()
    }

    fun dumpRunGraphToDOT(): String {
        return runGraph.toDOTNetwork().toString()
    }
}
