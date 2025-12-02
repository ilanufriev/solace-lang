package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.types.DataType
import solace.vm.internal.sim.types.LeafType

class Register : LeafType() {
    override val ports: MutableMap<String, Wire<DataType>?> = mutableMapOf(
        "in" to null,
        "out" to null,
    )

    override fun evaluate() {
        getPort("out").send(getPort("in").receive() ?: 0)
    }
}