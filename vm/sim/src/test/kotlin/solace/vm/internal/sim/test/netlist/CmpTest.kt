package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.netlist.Wire
import solace.vm.internal.sim.netlist.CmpEq
import solace.vm.internal.sim.netlist.CmpLeq
import solace.vm.internal.sim.netlist.CmpLess
import solace.vm.internal.sim.netlist.util.Converter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CmpTest {
    @Test fun testCmpEq() {
        val cmpEq = CmpEq()
        cmpEq.connectPort("in1", Wire<Int>())
        cmpEq.connectPort("in2", Wire<Int>())
        cmpEq.connectPort("out", Wire<Int>())

        for(i in 0..100) {
            val a = Random.nextInt(0, 100)
            val b = Random.nextInt(0, 100)

            val expect = Converter.booleanToInt(a == b)

            cmpEq.getPort("in1")!!.send(a)
            cmpEq.getPort("in2")!!.send(b)

            cmpEq.evaluate()

            val got = cmpEq.getPort("out")!!.receive()

            assertEquals(expect, got,
                "Test failed for a = $a, b = $b, got $got")
        }
    }

    @Test fun testCmpLess() {
        val cmpLess = CmpLess()
        cmpLess.connectPort("in1", Wire<Int>())
        cmpLess.connectPort("in2", Wire<Int>())
        cmpLess.connectPort("out", Wire<Int>())

        for(i in 0..100) {
            val a = Random.nextInt(0, 100)
            val b = Random.nextInt(0, 100)

            val expect = Converter.booleanToInt(a < b)

            cmpLess.getPort("in1")!!.send(a)
            cmpLess.getPort("in2")!!.send(b)

            cmpLess.evaluate()

            val got = cmpLess.getPort("out")!!.receive()

            assertEquals(expect, got,
                "Test failed for a = $a, b = $b, got $got")
        }
    }

    @Test fun testCmpLeq() {
        val cmpLeq = CmpLeq()
        cmpLeq.connectPort("in1", Wire<Int>())
        cmpLeq.connectPort("in2", Wire<Int>())
        cmpLeq.connectPort("out", Wire<Int>())

        for(i in 0..100) {
            val a = Random.nextInt(0, 100)
            val b = Random.nextInt(0, 100)

            val expect = Converter.booleanToInt(a <= b)

            cmpLeq.getPort("in1")!!.send(a)
            cmpLeq.getPort("in2")!!.send(b)

            cmpLeq.evaluate()

            val got = cmpLeq.getPort("out")!!.receive()

            assertEquals(expect, got,
                "Test failed for a = $a, b = $b (i = $i), got $got")
        }
    }
}