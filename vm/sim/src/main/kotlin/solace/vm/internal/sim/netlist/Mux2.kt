package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.*

class Mux2() : LeafType() {
    override val ports: MutableMap<String, Wire<Int>?> = mutableMapOf(
        "in0" to null,
        "in1" to null,
        "sel" to null,
        "out" to null
    )

    override fun evaluate() {
        val a = getPort("in0").receive() ?: 0
        val b = getPort("in1").receive() ?: 0
        val s = getPort("sel").receive() ?: 0
        getPort("out").send(if (s == 0) a else b)
    }

}