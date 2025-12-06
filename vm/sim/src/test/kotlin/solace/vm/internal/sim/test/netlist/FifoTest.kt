package solace.vm.internal.sim.test.netlist

import solace.vm.internal.sim.netlist.Fifo
import solace.vm.internal.sim.types.WireType
import kotlin.test.Test
import kotlin.test.assertEquals

class FifoTest {
    @Test fun testFifo() {
        val fifo = Fifo()
        val w1 = WireType()
        val w2 = WireType()
        fifo.connectPort("in", w1)
        fifo.connectPort("out", w2)

        fifo.pushToFifoDirectly(1)
        fifo.pushToFifoDirectly(2)
        fifo.pushToFifoDirectly(3)

        assertEquals(3, fifo.queue.size)

        fifo.pushToOutputs()

        assertEquals(1, w2.receive()!!)
        assertEquals(2, fifo.queue.size)

        fifo.pushToOutputs()

        assertEquals(2, w2.receive()!!)
        assertEquals(1, fifo.queue.size)

        fifo.pushToOutputs()

        assertEquals(3, w2.receive()!!)
        assertEquals(0, fifo.queue.size)

        w1.send(4)

        fifo.pullFromInput()

        assertEquals(1, fifo.queue.size)

        fifo.pushToOutputs()

        assertEquals(4, w2.receive()!!)
        assertEquals(0, fifo.queue.size)
    }
}