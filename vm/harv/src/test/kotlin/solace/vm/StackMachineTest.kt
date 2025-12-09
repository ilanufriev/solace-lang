package solace.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import solace.vm.internal.harv.asm.AsmParser

class StackMachineTest {
    @Test
    fun testBlockingBehaviour() {
        val byteCode = AsmParser.encodeInstructionsFromString($$"""
            .define %fifo $in1 ?
            .define %fifo $in2 ?
            .define %fifo $outp ?
            .push $in1
            .push $in2
            .add
            .put $outp
        """.trimIndent()).joinToString("")

        val harv = StackMachine()
        harv.loadByteCode(byteCode)
        var result = harv.tryInit()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)

        harv.pushToFifo("in1", 2)
        harv.pushToFifo("in2", 5)

        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(1, harv.getFifoSize("outp"))
        assertEquals(7, harv.pullFromFifo("outp"))

        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.BLOCKED, result)
        assertFalse(StackMachine.ExecStatus.SUCCESS == result)

        harv.pushToFifo("in1", 2)
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.BLOCKED, result)

        harv.pushToFifo("in2", 3)
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(5, harv.pullFromFifo("outp"))
    }

    @Test
    fun testCounter() {
        // Counter example, written in assembly
        val byteCode = AsmParser.encodeInstructionsFromString($$"""
            .define %fifo $numbers ?
            .define %fifo $loop ?
            .define %int $x ?
            .push #0 ?
            .put $loop ?
            .push $loop
            .push #1
            .add
            .put $x
            .push $x
            .put $numbers
            .push $x
            .put $loop
        """.trimIndent()).joinToString("")

        println(byteCode)

        var harv = StackMachine()
        harv.loadByteCode(byteCode)
        var result = harv.tryInit()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(1, harv.getFifoSize("loop"))
        assertEquals(0, harv.pullFromFifo("loop"))

        harv = StackMachine()
        harv.loadByteCode(byteCode)
        result = harv.tryInit()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(1, harv.getFifoSize("numbers"))
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(2, harv.getFifoSize("numbers"))
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(3, harv.getFifoSize("numbers"))
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(4, harv.getFifoSize("numbers"))

        assertEquals(1, harv.pullFromFifo("numbers"))
        assertEquals(2, harv.pullFromFifo("numbers"))
        assertEquals(3, harv.pullFromFifo("numbers"))
        assertEquals(4, harv.pullFromFifo("numbers"))
    }
}