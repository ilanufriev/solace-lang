package solace.vm.internal.harv

import solace.vm.internal.harv.asm.*
import solace.vm.internal.harv.types.HarvInt
import solace.vm.internal.harv.types.HarvString
import solace.vm.internal.harv.types.HarvVal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import solace.vm.StackMachine

class InstructionTest {
    @Test
    fun testRenderingToText() {
        assertTrue(Add().toString() == ".add")
        assertTrue(Sub().toString() == ".sub")
        assertTrue(Mul().toString() == ".mul")
        assertTrue(Div().toString() == ".div")
        assertTrue(Mod().toString() == ".mod")
        assertTrue(Lt().toString() == ".lt")
        assertTrue(Gt().toString() == ".gt")
        assertTrue(Le().toString() == ".le")
        assertTrue(Ge().toString() == ".ge")
        assertTrue(Eq().toString() == ".eq")
        assertTrue(Neq().toString() == ".neq")
        assertTrue(And().toString() == ".and")
        assertTrue(Or().toString() == ".or")
        assertTrue(Not().toString() == ".not")
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

    @Test
    fun testParsingProgram() {
        val byteCode = AsmParser.encodeInstructionsFromString(
            $$$"""
            .define %int $x
            .push #5
            .push #8
            .add
            .put $x
            .branch $labelif $labelelse $labelend
        """.trimMargin()
        ).joinToString("")

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
    fun p(code: String): Pair<StackMachine.ExecStatus, List<HarvVal>> {
        val byteCode = AsmParser.encodeInstructionsFromString(code.trimMargin()).joinToString("")

        val vm = StackMachine()
        vm.loadByteCode(byteCode)

        val status = vm.tryRun()
        val stack = vm.getStack()

        return Pair(status, stack)
    }

    @Test
    fun basic5plus8minus2shouldBe11() {
        val (status, stack) = p(
            """
            .push #5
            .push #8
            .add
            .push #2
            .sub
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals(11, (stack.last() as? HarvInt)?.value)
    }

    @Test
    fun stringConcatenation() {
        val (status, stack) = p(
            """
            .push +"Ping"
            .push +"Pong"
            .add
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals("PingPong", (stack.last() as? HarvString)?.value)
    }

    @Test
    fun concatStringAndNumber() {
        val (status, stack) = p(
            """
            .push +"Ping"
            .push #69
            .add
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals("Ping69", (stack.last() as? HarvString)?.value)
    }

    @Test
    fun gotoGoesTo() {
        val (status, stack) = p(
            $$"""
            .push #1
            .push #2
            .goto $L1
            .push #3
            .push #4
            .push #5
            .push #6
            .label $L1
            .push #7
        """
        )

        val stackList = stack.toList()

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals(1, (stackList[0] as? HarvInt)?.value)
        assertEquals(2, (stackList[1] as? HarvInt)?.value)
        assertEquals(7, (stackList[2] as? HarvInt)?.value)
    }

    @Test
    fun branchIfTrue() {
        val (status, stack) = p(
            $$"""
            .push #5
            .push #2
            .gt
            .branch $trueLabel $falseLabel $endLabel
            .label $trueLabel
            .push #4
            .goto $endLabel
            .label $falseLabel
            .push #5
            .label $endLabel
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals(4, (stack.last() as? HarvInt)?.value)
    }

    @Test
    fun branchIfFalse() {
        val (status, stack) = p(
            $$"""
            .push #1
            .push #2
            .gt
            .branch $trueLabel $falseLabel $endLabel
            .label $trueLabel
            .push #4
            .goto $endLabel
            .label $falseLabel
            .push #5
            .label $endLabel
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals(5, (stack.last() as? HarvInt)?.value)
    }

    @Test
    fun branchIfWithoutElse() {
        val (status, stack) = p(
            $$"""
            .push #3
            .push #2
            .gt
            .branch $trueLabel $endLabel $endLabel
            .label $trueLabel
            .push #4
            .label $endLabel
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals(4, (stack.last() as? HarvInt)?.value)

        val (statusFalse, stackFalse) = p(
            $$"""
            .push #1
            .push #2
            .gt
            .branch $trueLabel $endLabel $endLabel
            .label $trueLabel
            .push #4
            .label $endLabel
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, statusFalse)
        assertEquals(0, stackFalse.size)
    }

    @Test
    fun variables() {
        val (status, stack) = p(
            $$"""
            .push #3
            .push #1
            .add
            .define %int $aaa
            .put $aaa
            .push #8
            .push $aaa
            .div
        """
        )

        assertEquals(StackMachine.ExecStatus.SUCCESS, status)
        assertEquals(2, (stack.last() as? HarvInt)?.value)
    }
}