package solace.vm.internal.sim.test.netlist

import kotlin.test.Test
import kotlin.test.assertEquals
import solace.vm.internal.sim.netlist.Wire
import solace.vm.internal.sim.netlist.Multiplier

class MultiplierTest {
    @Test fun testMultiplier() {
        val w1 = Wire<Int>()
        val w2 = Wire<Int>()
        val w3 = Wire<Int>()

        val multi = Multiplier(w1, w2, w3)

        w1.send(93)
        w2.send(32)

        multi.evaluate()
        assertEquals(93 * 32, w3.receive(), "Multiplier expected value is not correct")
    }
}