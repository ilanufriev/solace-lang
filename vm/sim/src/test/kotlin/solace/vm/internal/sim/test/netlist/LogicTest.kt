package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.netlist.LogicAnd
import solace.vm.internal.sim.netlist.LogicNot
import solace.vm.internal.sim.netlist.LogicOr
import solace.vm.internal.sim.netlist.Wire
import solace.vm.internal.sim.netlist.util.Converter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.random.Random

class LogicTest {
    @Test fun testLogic() {
        for (i in 0..10) {
            val a = Random.nextInt(0, 10)
            val b = Random.nextInt(0, 10)
            val ab = Converter.intToBoolean(a)
            val bb = Converter.intToBoolean(b)

            val lor = LogicOr(Wire<Int>(a), Wire<Int>(b), Wire<Int>())
            lor.evaluate()
            assertEquals(Converter.booleanToInt(ab || bb),
                lor.out!!.receive())

            val land = LogicAnd(Wire<Int>(a), Wire<Int>(b), Wire<Int>())
            land.evaluate()
            assertEquals(Converter.booleanToInt(ab && bb),
                land.out!!.receive())

            val lnot = LogicNot(Wire<Int>(a), Wire<Int>())
            lnot.evaluate()
            assertEquals(Converter.booleanToInt(!ab),
                lnot.out!!.receive())
        }
    }
}