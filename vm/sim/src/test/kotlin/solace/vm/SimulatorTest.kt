package solace.vm

import solace.vm.internal.sim.asm.AsmParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SimulatorTest {
    @Test fun testSimulatorCounterProgram() {
        // Counter example, written in assembly
        val byteCode = AsmParser.encodeInstructionsFromString($$"""
            .new %Fifo $counter ?
            .immcon $counter@in #0 ?

            .new %Fifo $counter
            .new %Adder $add
            .new %Register $x
            .new %Fifo $numbers
            
            .con $counter@out $add@in1
            .immcon $add@in2 #1
            .con $add@out $x@in
            .con $x@out $numbers@in
            .con $x@out $counter@in
        """.trimIndent()).joinToString("")

        var sim = Simulator()
        sim.loadByteCode(byteCode)
        var result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("counter"))
        assertEquals(0, sim.pullFromFifo("counter"))

        sim = Simulator()
        sim.loadByteCode(byteCode)
        result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(2, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(3, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(4, sim.getFifoSize("numbers"))

        assertEquals(1, sim.pullFromFifo("numbers"))
        assertEquals(2, sim.pullFromFifo("numbers"))
        assertEquals(3, sim.pullFromFifo("numbers"))
        assertEquals(4, sim.pullFromFifo("numbers"))
    }

    @Test fun testSimulatorAdderProgram() {
        val byteCode = AsmParser.encodeInstructionsFromString($$"""
            .new %Fifo $in1
            .new %Fifo $in2
            .new %Adder $add
            .new %Fifo $result
            
            .con $in1@out $add@in1
            .con $in2@out $add@in2
            .con $add@out $result@in
        """.trimIndent()).joinToString("")

        var sim = Simulator()
        sim.loadByteCode(byteCode)
        var result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)

        sim.pushToFifo("in1", 2)
        sim.pushToFifo("in2", 5)

        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("result"))
        assertEquals(7, sim.pullFromFifo("result"))

        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.BLOCKED, result)
        assertFalse(Simulator.ExecStatus.SUCCESS == result)
    }
}