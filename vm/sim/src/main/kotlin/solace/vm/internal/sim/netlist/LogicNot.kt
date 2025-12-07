package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.netlist.util.Converter
import solace.vm.internal.sim.types.*

class LogicNot() : LeafType() {
    override val ports = mutableMapOf<String, Wire<Int>?>(
        "in" to null,
        "out" to null,
    )

    override fun evaluate() {
        val a = Converter.intToBoolean(getPort("in").receive() ?: return)
        getPort("out").send(Converter.booleanToInt(!a))
    }
}