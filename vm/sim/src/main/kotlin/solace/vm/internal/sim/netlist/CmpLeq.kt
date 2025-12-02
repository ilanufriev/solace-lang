package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.*

class CmpLeq() : LeafType() {
    override val ports = mutableMapOf<String, Wire<Int>?>(
        "in1" to null,
        "in2" to null,
        "out" to null,
    )

    override fun evaluate() {
        val cmpEq = CmpEq()
        cmpEq.connectPort("in1", getPort("in1"))
        cmpEq.connectPort("in2", getPort("in2"))
        cmpEq.connectPort("out", Wire<Int>())

        val cmpLess = CmpLess()
        cmpLess.connectPort("in1", getPort("in1"))
        cmpLess.connectPort("in2", getPort("in2"))
        cmpLess.connectPort("out", Wire<Int>())

        val logicOr = LogicOr()
        logicOr.connectPort("in1", cmpLess.getPort("out"))
        logicOr.connectPort("in2", cmpEq.getPort("out"))
        logicOr.connectPort("out", getPort("out"))

        cmpEq.evaluate()
        cmpLess.evaluate()
        logicOr.evaluate()
    }
}