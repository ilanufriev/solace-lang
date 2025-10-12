package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.netlist.util.Port
import solace.vm.internal.sim.netlist.util.PortNotConnectedError
import solace.vm.internal.sim.netlist.util.PortSentinel

class Multiplier(
    @Port var in1: Wire<Int>?,
    @Port var in2: Wire<Int>?,
    @Port var out: Wire<Int>?
) : Leaf {
    override fun evaluate() {
        if (!PortSentinel.portsConnected(this)) throw PortNotConnectedError()

        val a = in1!!.receive() ?: 0
        val b = in2!!.receive() ?: 0
        out!!.send(a * b)
    }
}