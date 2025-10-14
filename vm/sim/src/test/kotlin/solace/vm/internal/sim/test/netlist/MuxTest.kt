package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.netlist.Mux
import solace.vm.internal.sim.netlist.Wire
import kotlin.test.Test
import kotlin.test.assertEquals

class MuxTest {
    @Test fun testMux() {
        var mux = Mux(
            arrayOf(Wire<Int>(), Wire<Int>(), Wire<Int>()),
            Wire<Int>(),
            Wire<Int>())

        mux.data_ins[0]!!.send(1)
        mux.data_ins[1]!!.send(2)
        mux.data_ins[2]!!.send(3)

        var pos = 1

        mux.sel!!.send(pos)
        mux.evaluate()

        assertEquals(mux.data_ins[pos]!!.receive(), mux.out!!.receive())

        pos = 2

        mux.sel!!.send(pos)
        mux.evaluate()

        assertEquals(mux.data_ins[pos]!!.receive(), mux.out!!.receive())

        pos = 0

        mux.sel!!.send(pos)
        mux.evaluate()

        assertEquals(mux.data_ins[pos]!!.receive(), mux.out!!.receive())
    }
}