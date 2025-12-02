package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.*

class Adder() : LeafType() {
    override val ports = mutableMapOf<String, Wire<Int>?>(
        "in1" to null,
        "in2" to null,
        "out" to null,
        "en" to null
    )

    override fun evaluate() {
        val a = getPort("in1").receive() ?: 0
        val b = getPort("in2").receive() ?: 0
        getPort("out").send(a + b)
    }
}
