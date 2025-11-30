package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.Eval
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.InFifo
import solace.vm.internal.sim.asm.instructions.InFifoCon
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.OutFifo
import solace.vm.internal.sim.asm.instructions.OutFifoCon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsmParserTest {
    @Test fun testAsmParser() {
        val source = $$"""
            .infifo $ififo
            .outfifo $ofifo
            .new %Adder $add
            .new %Multiplier $multi
            .infifocon $ififo $add1@in1
            .infifocon $ififo $add1@in2
            .con $add1@out $multi@in1
            .immcon $multi@in2 #10
            .outfifocon $ofifo $multi@out
            
            .eval
        """.trimIndent()

        val instrs = AsmParser.parseIntoInstrs(source)
        assertEquals(10, instrs.size)
        assertTrue(instrs[0] is InFifo)
        assertTrue(instrs[1] is OutFifo)
        assertTrue(instrs[2] is New)
        assertTrue(instrs[3] is New)
        assertTrue(instrs[4] is InFifoCon)
        assertTrue(instrs[5] is InFifoCon)
        assertTrue(instrs[6] is Con)
        assertTrue(instrs[7] is ImmCon)
        assertTrue(instrs[8] is OutFifoCon)
        assertTrue(instrs[9] is Eval)
    }
}