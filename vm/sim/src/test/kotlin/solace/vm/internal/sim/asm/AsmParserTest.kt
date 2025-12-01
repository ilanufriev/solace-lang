package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.Eval
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.NewInFifo
import solace.vm.internal.sim.asm.instructions.FifoCon
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.NewLoopFifo
import solace.vm.internal.sim.asm.instructions.NewOutFifo
import solace.vm.internal.sim.asm.instructions.NewWire
import solace.vm.internal.sim.asm.instructions.SetWire
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsmParserTest {
    @Test fun testAsmParser() {
        val source = $$"""
            .newinfifo $ififo
            .newoutfifo $ofifo
            .new %Adder $add?
            .new %Multiplier $multi?
            .fifocon $ififo $add1@in1?
            .fifocon $ififo $add1@in2
            .con $add1@out $multi@in1
            .immcon $multi@in2 #10
            .fifocon $ofifo $multi@out
            .newwire $wire00
            .setwire $wire00 #5
            .newloopfifo $lfifo
            .fifocon $lfifo $multi@out
        """.trimIndent()

        var i = 0
        val instrs = AsmParser.parseIntoInstrs(source)
        assertEquals(13, instrs.size)
        assertTrue(instrs[i++] is NewInFifo)
        assertTrue(instrs[i++] is NewOutFifo)
        assertTrue(instrs[i++] is New)
        assertTrue(instrs[i++] is New)
        assertTrue(instrs[i++] is FifoCon)
        assertTrue(instrs[i++] is FifoCon)
        assertTrue(instrs[i++] is Con)
        assertTrue(instrs[i++] is ImmCon)
        assertTrue(instrs[i++] is FifoCon)
        assertTrue(instrs[i++] is NewWire)
        assertTrue(instrs[i++] is SetWire)
        assertTrue(instrs[i++] is NewLoopFifo)
        assertTrue(instrs[i++] is FifoCon)
    }

    @Test fun testEncoding() {
        val einstrs = AsmParser.encodeInstructions(".new %Mux \$mux00 ? .con \$mux00@in1 \$mux01@in2")
        assertEquals(einstrs.first().toString(), "010b%Mux\$mux00?")

        val einstrString = einstrs.joinToString(separator = "")
        val parsedEinstrs = AsmParser.parseEncodedInstructions(einstrString)
        assertEquals(parsedEinstrs.size, 2)
        assertEquals(parsedEinstrs.first().toString(), "010b%Mux\$mux00?")

        val dinstrs = AsmParser.decodeInstructions(einstrs)
        assertEquals(dinstrs.first(), ".new%Mux\$mux00?")
    }
}