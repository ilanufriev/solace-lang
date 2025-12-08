package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.graph.NetlistGraph
import solace.utils.dotgen.*
import solace.vm.internal.sim.graph.NetlistGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import solace.vm.internal.sim.netlist.*
import kotlin.test.assertTrue

class NetlistTest {
    @Test fun testConnections() {
        val w1 = Wire<Int>()
        val w2 = Wire<Int>()
        val w3 = Wire<Int>()
        val w4 = Wire<Int>()
        val w5 = Wire<Int>()

        val adder = Adder()
        adder.connectPort("in1", w1)
        adder.connectPort("in2", w2)
        adder.connectPort("out", w3)
        val multi = Multiplier()
        multi.connectPort("in1", w3)
        multi.connectPort("in2", w4)
        multi.connectPort("out", w5)

        w1.send(2)
        w2.send(3)
        w4.send(5);

        adder.evaluate()
        multi.evaluate()

        assertEquals((2 + 3) * 5, w5.receive(), "Expression result is not as expected")
    }

    @Test fun testNetlishGraph() {
        val gr = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()

        gr.addLeaf("a", "Fifo")
        gr.addLeaf("b", "Fifo")

        gr.addLeaf("add1", "Adder")
        gr.addLeaf("mul1", "Multiplier")

        gr.addLeaf("c", "Fifo")

        gr.conLeaf("a", "out1", "add1", "in1")
        gr.conLeaf("a", "out2", "add1", "in2")
        gr.conLeaf("b", "out1", "mul1", "in1")
        gr.conLeaf("add1", "out", "mul1", "in2")
        gr.conLeaf("mul1", "out", "c", "in")

        gr.pushDataToFifo("a", 2)
        gr.pushDataToFifo("a", 3)
        gr.pushDataToFifo("a", 6)
        gr.pushDataToFifo("b", 5)

        assertEquals(3, gr.getFifoSize("a"))

        gr.evaluate()

        assertEquals(1, gr.getFifoSize("a"))
        assertEquals(0, gr.getFifoSize("b"))
        assertEquals(1, gr.getFifoSize("c"))

        val result = gr.pullDataFromFifo("c")

        assertEquals((2 + 3) * 5, result)
    }

    @Test fun testNetlishGraphImmediates() {
        val gr = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()
        gr.addFifo("a")

        gr.addLeaf("add1", "Adder")
        gr.addLeaf("mul1", "Multiplier")

        gr.addFifo("c")

        gr.conLeaf("a", "out1", "add1", "in1")
        gr.conLeaf("a", "out2", "add1", "in2")
        gr.conLeafToImmediate("mul1", "in1", 5)
        gr.conLeaf("mul1", "out", "c", "in")
        gr.conLeaf("add1", "out", "mul1", "in2")

        gr.pushDataToFifo("a", 2)
        gr.pushDataToFifo("a", 3)

        assertEquals(2, gr.getFifoSize("a"))

        gr.evaluate()

        val result = gr.pullDataFromFifo("c")

        assertEquals((2 + 3) * 5, result)
    }

    @Test fun testNetlistGraphConnectionBuilding() {
        val gr = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()
        gr.addFifo("a")

        gr.addLeaf("add1", "Adder")
        gr.addLeaf("mul1", "Multiplier")

        gr.addFifo("c")

        gr.conLeaf("a", "out1", "add1", "in1")
        gr.conLeaf("a", "out2", "add1", "in2")
        gr.conLeafToImmediate("mul1", "in1", 5)
        gr.conLeaf("mul1", "out", "c", "in")
        gr.conLeaf("add1", "out", "mul1", "in2")

        assertTrue(gr.connectionExists("a", "out1", "add1", "in1"))
        assertTrue(gr.connectionExists("a", "out2", "add1", "in2"))
        assertTrue(gr.connectionExists("5", "mul1", "in1"))
        assertTrue(gr.connectionExists("mul1", "out", "c", "in"))
        assertTrue(gr.connectionExists("add1", "out", "mul1", "in2"))
    }
}