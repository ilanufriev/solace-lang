package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.NewInFifo
import solace.vm.internal.sim.asm.instructions.FifoCon
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.NewOutFifo
import kotlin.test.Test
import kotlin.test.assertEquals

class InstructionsTests {
    @Test fun testConInstruction() {
        val con = Con()
        con.parse(".con  \$mux00@in1   \$mux01@sel")

        assertEquals("mux00", con.fromLeafName)
        assertEquals("mux01", con.toLeafName)
        assertEquals("in1", con.fromLeafPortName)
        assertEquals("sel", con.toLeafPortName)
    }

    @Test fun testNewInstruction() {
        val new = New()
        new.parse(".new   %Mux2   \$mux00")

        assertEquals("mux00", new.leafName)
        assertEquals("Mux2", new.leafType)
    }

    @Test fun testFifoConInstruction() {
        val inFifoCon = FifoCon()
        inFifoCon.parse(".infifocon \$fifo1 \$add01@in1")

        assertEquals("fifo1", inFifoCon.fifoName)
        assertEquals("add01", inFifoCon.leafName)
        assertEquals("in1", inFifoCon.leafPortName)
    }

    @Test fun testImmConInstruction() {
        val immCon = ImmCon()
        immCon.parse(".immcon     \$add01@in1   #5")

        assertEquals("5", immCon.immediate)
        assertEquals("add01", immCon.leafName)
        assertEquals("in1", immCon.leafPortName)
    }

    @Test fun testInFifoInstruction() {
        val inFifo = NewInFifo()
        inFifo.parse(".infifo \$fifo1")

        assertEquals("fifo1", inFifo.fifoName)
    }

    @Test fun testOutFifoInstruction() {
        val outFifo = NewOutFifo()
        outFifo.parse(".outfifo \$fifo1")

        assertEquals("fifo1", outFifo.fifoName)
    }
}