package solace.vm.internal.sim.netlist

import solace.vm.internal.sim.netlist.util.Port
import solace.vm.internal.sim.netlist.util.PortArray
import solace.vm.internal.sim.netlist.util.PortNotConnectedError
import solace.vm.internal.sim.netlist.util.PortSentinel

class Mux(
    @PortArray var data_ins: Array<Wire<Int>?>,
    @Port var sel: Wire<Int>?,
    @Port var out: Wire<Int>?
) : Leaf {
    var hidden: Array<String> = arrayOf("Hello", "world")

    override fun evaluate() {
        if (!PortSentinel.portsConnected(this)) throw PortNotConnectedError()

        val sel = sel!!.receive() ?: 0

        out!!.send(data_ins.get(sel)!!.receive() ?: 0)
    }
}