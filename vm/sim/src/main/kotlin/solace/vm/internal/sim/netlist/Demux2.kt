package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.DataType
import solace.vm.internal.sim.types.LeafType

class Demux2 : LeafType() {
    override val ports: MutableMap<String, Wire<DataType>?> = mutableMapOf(
        "in" to null,
        "sel" to null,
        "out0" to null,
        "out1" to null,
    )

    override fun evaluate() {
        val i = getPort("in").receive() ?: return
        val s = getPort("sel").receive() ?: return

        if (s == 0) getPort("out0").send(i)
        else if (s == 1) getPort("out1").send(i)
    }
}