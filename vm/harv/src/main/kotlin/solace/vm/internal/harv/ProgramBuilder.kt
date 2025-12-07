package solace.vm.internal.harv

class ProgramBuilder() {
    private val program = mutableListOf<Int>()

    fun push(arg: Int) = apply { add(InstructionOpcode.PUSH.code.toInt(), arg) }
    fun pop() = apply { add(InstructionOpcode.POP.code.toInt()) }

    // Математика
    fun add() = apply { add(InstructionOpcode.ADD.code.toInt()) }
    fun subtract() = apply { add(InstructionOpcode.SUBTRACT.code.toInt()) }
    fun multiply() = apply { add(InstructionOpcode.MULTIPLY.code.toInt()) }
    fun divide() = apply { add(InstructionOpcode.DIVIDE.code.toInt()) }
    fun mod() = apply { add(InstructionOpcode.MOD.code.toInt()) }
    fun negative() = apply { add(InstructionOpcode.NEGATIVE.code.toInt()) }
    fun duplicate() = apply { add(InstructionOpcode.DUPLICATE.code.toInt()) }
    fun swap() = apply { add(InstructionOpcode.SWAP.code.toInt()) }

    // Операции ветвления
    fun jump(address: UInt) = apply { add(InstructionOpcode.JUMP.code.toInt(), address.toInt()) }
    fun jumpIfTrue(address: UInt) = apply { add(InstructionOpcode.JUMP_IF_TRUE.code.toInt(), address.toInt()) }

    // Операции сравнения
    fun equal() = apply { add(InstructionOpcode.EQUAL.code.toInt()) }
    fun notEqual() = apply { add(InstructionOpcode.NOT_EQUAL.code.toInt()) }
    fun lessThan() = apply { add(InstructionOpcode.LESS_THAN.code.toInt()) }
    fun greaterThan() = apply { add(InstructionOpcode.GREATER_THAN.code.toInt()) }
    fun lessOrEqual() = apply { add(InstructionOpcode.LESS_OR_EQUAL.code.toInt()) }
    fun greaterOrEqual() = apply { add(InstructionOpcode.GREATER_OR_EQUAL.code.toInt()) }
    fun logicalAnd() = apply { add(InstructionOpcode.LOGICAL_AND.code.toInt()) }
    fun logicalOr() = apply { add(InstructionOpcode.LOGICAL_OR.code.toInt()) }
    fun logicalNot() = apply { add(InstructionOpcode.LOGICAL_NOT.code.toInt()) }

    // Специальные
    fun nop() = apply { add(InstructionOpcode.NOP.code.toInt()) }

    private fun add(vararg values: Int) {
        program.addAll(values.toList())
    }

    fun build(): List<Int> = program.toList()
}