package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.*

class Multiplier() : LeafType() {
    override val ports = mutableMapOf<String, Wire<Int>?>(
        "in1" to null,
        "in2" to null,
        "out" to null,
    )

    override fun evaluate() {
        val a = getPort("in1").receive() ?: return
        val b = getPort("in2").receive() ?: return
        getPort("out").send(a * b)
    }
}