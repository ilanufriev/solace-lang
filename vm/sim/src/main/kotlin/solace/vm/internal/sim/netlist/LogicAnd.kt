package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.netlist.util.Converter
import solace.vm.internal.sim.types.*

class LogicAnd() : LeafType() {
    override val ports = mutableMapOf<String, Wire<Int>?>(
        "in1" to null,
        "in2" to null,
        "out" to null,
    )

    override fun evaluate() {
        val a = Converter.intToBoolean(getPort("in1").receive() ?: return)
        val b = Converter.intToBoolean(getPort("in2").receive() ?: return)
        getPort("out").send(Converter.booleanToInt(a && b))
    }
}