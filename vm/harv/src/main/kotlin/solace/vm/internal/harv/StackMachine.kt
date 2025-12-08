package solace.vm.internal.harv

class StackMachine(private val mem: ReadableProgramMemory) {
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