package solace.vm.internal.sim.test.netlist

import kotlin.test.Test
import kotlin.test.assertEquals
import solace.vm.internal.sim.netlist.Adder
import solace.vm.internal.sim.netlist.Wire

class AdderTest {
    @Test fun testAdder() {
        val w1 = Wire<Int>()
        val w2 = Wire<Int>()
        val w3 = Wire<Int>()
        val adder1 = Adder()

        adder1.connectPort("in1", w1)
        adder1.connectPort("in2", w2)
        adder1.connectPort("out", w3)

        w1.send(2)
        w2.send(4)

        adder1.evaluate()
        assertEquals(6, w3.receive(), "Adder result did not match expected value")
    }
}
