package solace.vm.internal.harv

interface ReadableProgramMemory {
    fun readNextInt(): Int
    fun readNextUint(): UInt

    fun seek(adr: UInt)
    fun reset()

    fun isEof(): Boolean
}

class ListProgramMemory(private var mem: List<Int>) : ReadableProgramMemory {
    private var programCounter = 0

    override fun readNextInt(): Int {
        if (programCounter >= mem.size) {
            throw IndexOutOfBoundsException("Read beyond memory bounds")
        }
        val value = mem[programCounter]
        programCounter++
        return value
    }

    override fun readNextUint(): UInt {
        if (programCounter >= mem.size) {
            throw IndexOutOfBoundsException("Read beyond memory bounds")
        }
        val value = mem[programCounter].toUInt()
        programCounter++
        return value
    }

    override fun seek(adr: UInt) {
        programCounter = adr.toInt()
    }

    override fun isEof(): Boolean {
        return programCounter >= mem.size
    }

    // Дополнительные полезные методы
    override fun reset() {
        programCounter = 0
    }

}

class VirtualMachine(private val mem: ReadableProgramMemory) {
    private val stack = mutableListOf<Int>()

    fun step() {
        val opcode = mem.readNextUint()

        val instruction = InstructionOpcode.entries.find { it.code == opcode }
        if (instruction == null) {
            throw IllegalArgumentException("Unknown opcode: 0x${opcode.toString(16)}")
        }

        executeInstruction(instruction)
    }

    fun run() {
        while (!mem.isEof()) {
            step()
        }
    }

    private fun executeInstruction(instruction: InstructionOpcode) {
        when (instruction) {
            // Базовые операции
            InstructionOpcode.PUSH -> {
                val arg = mem.readNextInt()
                stack.add(arg)
            }

            InstructionOpcode.POP -> {
                if (stack.isNotEmpty()) {
                    stack.removeLast()
                }
            }

            // Математика
            InstructionOpcode.ADD -> binaryOp { a, b -> a + b }
            InstructionOpcode.SUBTRACT -> binaryOp { a, b -> a - b }
            InstructionOpcode.MULTIPLY -> binaryOp { a, b -> a * b }
            InstructionOpcode.DIVIDE -> binaryOp { a, b ->
                if (b == 0) throw ArithmeticException("Division by zero")
                a / b
            }

            InstructionOpcode.MOD -> binaryOp { a, b ->
                if (b == 0) throw ArithmeticException("Modulo by zero")
                a % b
            }

            InstructionOpcode.NEGATIVE -> {
                if (stack.isNotEmpty()) {
                    val value = stack.removeLast()
                    stack.add(-value)
                }
            }

            InstructionOpcode.DUPLICATE -> {
                if (stack.isNotEmpty()) {
                    val value = stack.last()
                    stack.add(value)
                }
            }

            InstructionOpcode.SWAP -> {
                if (stack.size >= 2) {
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    stack.add(b)
                    stack.add(a)
                }
            }

            // Операции ветвления
            InstructionOpcode.JUMP -> {
                val address = mem.readNextUint().toInt()
                mem.seek(address.toUInt())
            }

            InstructionOpcode.JUMP_IF_TRUE -> {
                val address = mem.readNextUint().toInt()

                if (stack.isNotEmpty()) {
                    val condition = stack.removeLast()
                    if (condition != 0) {
                        mem.seek(address.toUInt())
                    }
                }
            }

            // Операции сравнения
            InstructionOpcode.EQUAL -> binaryOp { a, b -> if (a == b) 1 else 0 }
            InstructionOpcode.NOT_EQUAL -> binaryOp { a, b -> if (a != b) 1 else 0 }
            InstructionOpcode.LESS_THAN -> binaryOp { a, b -> if (a < b) 1 else 0 }
            InstructionOpcode.GREATER_THAN -> binaryOp { a, b -> if (a > b) 1 else 0 }
            InstructionOpcode.LESS_OR_EQUAL -> binaryOp { a, b -> if (a <= b) 1 else 0 }
            InstructionOpcode.GREATER_OR_EQUAL -> binaryOp { a, b -> if (a >= b) 1 else 0 }

            InstructionOpcode.LOGICAL_AND -> binaryOp { a, b ->
                val boolA = a != 0
                val boolB = b != 0
                if (boolA && boolB) 1 else 0
            }

            InstructionOpcode.LOGICAL_OR -> binaryOp { a, b ->
                val boolA = a != 0
                val boolB = b != 0
                if (boolA || boolB) 1 else 0
            }

            InstructionOpcode.LOGICAL_NOT -> {
                if (stack.isNotEmpty()) {
                    val value = stack.removeLast()
                    val result = if (value == 0) 1 else 0
                    stack.add(result)
                }
            }

            // Специальные
            InstructionOpcode.NOP -> {
                // Ничего не делаем
            }
        }
    }

    private inline fun binaryOp(operation: (Int, Int) -> Int) {
        if (stack.size >= 2) {
            val b = stack.removeLast()
            val a = stack.removeLast()
            stack.addLast(operation(a, b))
        }
    }

    fun getStack(): Iterable<Int> {
        return stack
    }
}

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

enum class InstructionOpcode(val code: UInt) {
    // Базовые операции
    PUSH(0x01u),    // [] -> [<Argument>]
    POP(0x02u),     // [x] -> []

    // Математика
    ADD(0x03u),         // [a, b] -> [a + b]
    SUBTRACT(0x04u),    // [a, b] -> [a - b]
    MULTIPLY(0x05u),    // [a, b] -> [a * b]
    DIVIDE(0x06u),      // a, b] -> [a / b]
    MOD(0x07u),         // [a, b] -> [a % b]
    NEGATIVE(0x08u),    // [x] -> [-x]
    DUPLICATE(0x09u),   // [x] -> [x, x]
    SWAP(0x0Au),        // [a, b] -> [b, a]

    // Операции ветвления
    JUMP(0x10u),            // jump to <Argument>
    JUMP_IF_TRUE(0x11u),    // jump to <Argument> if [x] != 0 ; [x] -> []

    // Операции сравнения
    EQUAL(0x20u),               //  [a, b] -> [a == b]
    NOT_EQUAL(0x21u),           //  [a, b] -> [a != b]
    LESS_THAN(0x22u),           //  [a, b] -> [a < b]
    GREATER_THAN(0x23u),        //  [a, b] -> [a > b]
    LESS_OR_EQUAL(0x24u),       //  [a, b] -> [a <= b]
    GREATER_OR_EQUAL(0x25u),    //  [a, b] -> [a >= b]
    LOGICAL_AND(0x26u),         //  [a, b] -> [a && b]
    LOGICAL_OR(0x27u),          //  [a, b] -> [a || b]
    LOGICAL_NOT(0x28u),         //  [x] -> [!x]

    // Специальные
    NOP(0x00u);
}

