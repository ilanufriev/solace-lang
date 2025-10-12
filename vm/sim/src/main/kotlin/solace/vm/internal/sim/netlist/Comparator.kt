package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.netlist.util.Port
import solace.vm.internal.sim.netlist.util.PortNotConnectedError
import solace.vm.internal.sim.netlist.util.PortSentinel

class Comparator(
    @Port var in1: Wire<Int>?,
    @Port var in2: Wire<Int>?,
    @Port var out: Wire<Int>?,
) : Leaf {
    override fun evaluate() {
        if (!PortSentinel.portsConnected(this)) throw PortNotConnectedError()

        val a = in1!!.receive() ?: 0
        val b = in2!!.receive() ?: 0

        if (a == b) {
            out!!.send(ComparatorResult.EQUAL.code);
        } else if (a < b) {
            out!!.send(ComparatorResult.LESS.code);
        } else {
            out!!.send(ComparatorResult.GREATER.code);
        }
    }
}