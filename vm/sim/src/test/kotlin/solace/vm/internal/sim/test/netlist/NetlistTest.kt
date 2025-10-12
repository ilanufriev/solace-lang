package solace.vm.internal.sim.test.netlist

import kotlin.test.Test
import kotlin.test.assertEquals
import solace.vm.internal.sim.netlist.*

class NetlistTest {
    @Test fun testConnections() {
        val w1 = Wire<Int>()
        val w2 = Wire<Int>()
        val w3 = Wire<Int>()
        val w4 = Wire<Int>()
        val w5 = Wire<Int>()

        val adder = Adder(w1, w2, w3)
        val multi = Multiplier(w3, w4, w5)

        w1.send(2)
        w2.send(3)
        w4.send(5);

        adder.evaluate()
        multi.evaluate()

        assertEquals((2 + 3) * 5, w5.receive(), "Expression result is not as expected")
    }
}