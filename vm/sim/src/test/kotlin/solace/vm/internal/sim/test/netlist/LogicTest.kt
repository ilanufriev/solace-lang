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

            val lor = LogicOr()
            lor.connectPort("in1", Wire<Int>())
            lor.connectPort("in2", Wire<Int>())
            lor.connectPort("out", Wire<Int>())

            lor.getPort("in1")!!.send(a)
            lor.getPort("in2")!!.send(b)

            lor.evaluate()

            assertEquals(Converter.booleanToInt(ab || bb),
                lor.getPort("out")!!.receive())

            val land = LogicAnd()
            land.connectPort("in1", Wire<Int>())
            land.connectPort("in2", Wire<Int>())
            land.connectPort("out", Wire<Int>())

            land.getPort("in1")!!.send(a)
            land.getPort("in2")!!.send(b)

            land.evaluate()

            assertEquals(Converter.booleanToInt(ab && bb),
                land.getPort("out")!!.receive())

            val lnot = LogicNot()
            lnot.connectPort("in", Wire<Int>())
            lnot.connectPort("out", Wire<Int>())

            lnot.getPort("in")!!.send(a);
            lnot.evaluate()

            assertEquals(Converter.booleanToInt(!ab),
                lnot.getPort("out")!!.receive())
        }
    }
}