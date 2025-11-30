package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.graph.NetlistGraphFactory
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
        gr.addInputFifo("a")
        gr.addInputFifo("b")
        gr.addOutputFifo("c")

        gr.addLeaf("add1", "Adder")
        gr.addLeaf("mul1", "Multiplier")

        gr.conLeafToInputFifo("add1", "in1", "a")
        gr.conLeafToInputFifo("add1", "in2", "a")
        gr.conLeafToInputFifo("mul1", "in1", "b")
        gr.conLeafToOutputFifo("mul1", "out", "c")

        gr.conLeaf("add1", "out", "mul1", "in2")

        gr.pushImmToInputFifo("a", 2)
        gr.pushImmToInputFifo("a", 3)
        gr.pushImmToInputFifo("a", 6)
        gr.pushImmToInputFifo("b", 5)

        gr.evaluate()

        assertEquals(1, gr.getInputFifoSize("a"))
        assertEquals(0, gr.getInputFifoSize("b"))
        assertEquals(1, gr.getOutputFifoSize("c"))

        val result = gr.pullImmFromOutputFifo("c")

        assertEquals((2 + 3) * 5, result)
    }

    @Test fun testNetlishGraphImmediates() {
        val gr = NetlistGraphFactory.makeNetlistGraphWithRegisteredLeaves()
        gr.addInputFifo("a")
        gr.addOutputFifo("c")

        gr.addLeaf("add1", "Adder")
        gr.addLeaf("mul1", "Multiplier")

        gr.conLeafToInputFifo("add1", "in1", "a")
        gr.conLeafToInputFifo("add1", "in2", "a")
        gr.conLeafToImmediate("mul1", "in1", 5)
        gr.conLeafToOutputFifo("mul1", "out", "c")

        gr.conLeaf("add1", "out", "mul1", "in2")

        gr.pushImmToInputFifo("a", 2)
        gr.pushImmToInputFifo("a", 3)

        gr.evaluate()

        val result = gr.pullImmFromOutputFifo("c")

        assertEquals((2 + 3) * 5, result)
    }
}