package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.InFifo
import solace.vm.internal.sim.asm.instructions.InFifoCon
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.OutFifo
import solace.vm.internal.sim.asm.instructions.OutFifoCon
import kotlin.test.Test
import kotlin.test.assertEquals

class InstructionsTests {
    @Test fun testConInstruction() {
        val con = Con()
        con.parse(".con  \$mux00@in1   \$mux01@sel")

        assertEquals("mux00", con.leafName1)
        assertEquals("mux01", con.leafName2)
        assertEquals("in1", con.leafPortName1)
        assertEquals("sel", con.leafPortName2)
    }

    @Test fun testNewInstruction() {
        val new = New()
        new.parse(".new   %Mux2   \$mux00")

        assertEquals("mux00", new.leafName)
        assertEquals("Mux2", new.leafType)
    }

    @Test fun testInFifoConInstruction() {
        val inFifoCon = InFifoCon()
        inFifoCon.parse(".infifocon \$fifo1 \$add01@in1")

        assertEquals("fifo1", inFifoCon.fifoName)
        assertEquals("add01", inFifoCon.leafName)
        assertEquals("in1", inFifoCon.leafPortName)
    }

    @Test fun testOutFifoConInstruction() {
        val outFifoCon = OutFifoCon()
        outFifoCon.parse(".outfifocon    \$fifo1   \$add01@in1")

        assertEquals("fifo1", outFifoCon.fifoName)
        assertEquals("add01", outFifoCon.leafName)
        assertEquals("in1", outFifoCon.leafPortName)
    }

    @Test fun testImmConInstruction() {
        val immCon = ImmCon()
        immCon.parse(".immcon     \$add01@in1   #5")

        assertEquals("5", immCon.immediate)
        assertEquals("add01", immCon.leafName)
        assertEquals("in1", immCon.leafPortName)
    }

    @Test fun testInFifoInstruction() {
        val inFifo = InFifo()
        inFifo.parse(".infifo \$fifo1")

        assertEquals("fifo1", inFifo.fifoName)
    }

    @Test fun testOutFifoInstruction() {
        val outFifo = OutFifo()
        outFifo.parse(".outfifo \$fifo1")

        assertEquals("fifo1", outFifo.fifoName)
    }
}