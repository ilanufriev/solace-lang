package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.NewInFifo
import solace.vm.internal.sim.asm.instructions.FifoCon
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.NewLoopFifo
import solace.vm.internal.sim.asm.instructions.NewOutFifo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsmParserTest {
    @Test fun testAsmParser() {
        val source = $$"""
            .new %Adder $add?
            .new %Multiplier $multi?
            .con $add1@out $multi@in1
            .immcon $multi@in2 #10
        """.trimIndent()

        var i = 0
        val instrs = AsmParser.parseIntoInstrs(source)
        assertEquals(4, instrs.size)
        assertTrue(instrs[i++] is New)
        assertTrue(instrs[i++] is New)
        assertTrue(instrs[i++] is Con)
        assertTrue(instrs[i++] is ImmCon)
    }

    @Test fun testEncoding() {
        val einstrs = AsmParser.encodeInstructions(".new %Mux \$mux00 ? .con \$mux00@in1 \$mux01@in2")
        assertEquals(einstrs.first().toString(), "01000b%Mux\$mux00?")

        val einstrString = einstrs.joinToString(separator = "")
        val parsedEinstrs = AsmParser.parseEncodedInstructions(einstrString)
        assertEquals(parsedEinstrs.size, 2)
        assertEquals(parsedEinstrs.first().toString(), "01000b%Mux\$mux00?")

        val dinstrs = AsmParser.decodeInstructions(einstrs)
        assertEquals(dinstrs.first(), ".new%Mux\$mux00?")
    }
}