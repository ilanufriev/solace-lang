package solace.vm.internal.harv

import solace.vm.internal.harv.instruction.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstructionTest {
    @Test fun testRenderingToText() {
        assertTrue(Add().toString()   == ".add")
        assertTrue(Sub().toString()   == ".sub")
        assertTrue(Mul().toString()   == ".mul")
        assertTrue(Div().toString()   == ".div")
        assertTrue(Mod().toString()   == ".mod")
        assertTrue(Lt().toString()    == ".lt")
        assertTrue(Gt().toString()    == ".gt")
        assertTrue(Le().toString()    == ".le")
        assertTrue(Ge().toString()    == ".ge")
        assertTrue(Eq().toString()    == ".eq")
        assertTrue(Neq().toString()   == ".neq")
        assertTrue(And().toString()   == ".and")
        assertTrue(Or().toString()    == ".or")
        assertTrue(Not().toString()   == ".not")
        assertTrue(Print().toString() == ".print")

        val branch = Branch()
        val branchText = $$".branch $trueLabel $falseLabel $endLabel"
        branch.parse(branchText)
        assertEquals(branchText, branch.toString())

        val define = Define()
        val defineText = $$".define %int $integer"
        define.parse(defineText)
        assertEquals(defineText, define.toString())

        val goto = Goto()
        val gotoText = $$".goto $label"
        goto.parse(gotoText)
        assertEquals(gotoText, goto.toString())

        val label = Label()
        val labelText = $$".label $label"
        label.parse(labelText)
        assertEquals(labelText, label.toString())

        val push = Push()
        val pushText1 = $$".push +\"String\""
        val pushText2 = $$".push #5"
        val pushText3 = $$".push $val"
        push.parse(pushText1)
        assertEquals(pushText1, push.toString())
        push.parse(pushText2)
        assertEquals(pushText2, push.toString())
        push.parse(pushText3)
        assertEquals(pushText3, push.toString())

        val put = Put()
        val putText = $$".put $val"
        put.parse(putText)
        assertEquals(putText, put.toString())
    }

    @Test fun testParsingProgram() {
        val byteCode = AsmParser.encodeInstructionsFromString($$$"""
            .define %int $x
            .push #5
            .push #8
            .add
            .put $x
            .branch $labelif $labelelse $labelend
        """.trimMargin()).joinToString("")

        // println(byteCode)

        val einstrs = AsmParser.parseEncodedInstructions(byteCode)
        val isntrsStrings = AsmParser.decodeInstructions(einstrs)
        val instrs = AsmParser.parseIntoInstrs(isntrsStrings)

        assertEquals(6, instrs.size)
        assertTrue(instrs[0] is Define)
        assertTrue(instrs[1] is Push)
        assertTrue(instrs[2] is Push)
        assertTrue(instrs[3] is Add)
        assertTrue(instrs[4] is Put)
        assertTrue(instrs[5] is Branch)
    }
}

class NewStackMachineTest {
    @Test fun basicArithmetic() {
        val byteCode = AsmParser.encodeInstructionsFromString($$$"""
            .push #5
            .push #8
            .add
            .sub #2
        """.trimMargin()).joinToString("")

        val vm = NewStackMachine()
        vm.loadByteCode(byteCode)


    }
}