package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.netlist.Mux2
import solace.vm.internal.sim.netlist.Wire
import kotlin.test.Test
import kotlin.test.assertEquals

class MuxTest {
    @Test fun testMux2() {
        var mux = Mux2()
        mux.connectPort("in0", Wire<Int>())
        mux.connectPort("in1", Wire<Int>())
        mux.connectPort("sel", Wire<Int>())
        mux.connectPort("out", Wire<Int>())

        mux.getPort("in0").send(1)
        mux.getPort("in1").send(2)

        var pos = 1

        mux.getPort("sel").send(pos)
        mux.evaluate()

        assertEquals(mux.getPort("in1").receive(), mux.getPort("out").receive())

        pos = 0

        mux.getPort("sel").send(pos)
        mux.evaluate()

        assertEquals(mux.getPort("in0").receive(), mux.getPort("out").receive())
    }
}