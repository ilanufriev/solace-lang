package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.netlist.util.Converter
import solace.vm.internal.sim.netlist.util.Port
import solace.vm.internal.sim.netlist.util.PortNotConnectedError
import solace.vm.internal.sim.netlist.util.PortSentinel

class LogicNot(
    @Port var inp: Wire<Int>?,
    @Port var out: Wire<Int>?
) : Leaf {
    override fun evaluate() {
        if (!PortSentinel.portsConnected(this)) throw PortNotConnectedError()

        val a = Converter.intToBoolean(inp!!.receive() ?: 0)
        out!!.send(Converter.booleanToInt(!a))
    }
}