package solace.vm.internal.harv

import solace.vm.internal.harv.instruction.Add
import solace.vm.internal.harv.instruction.And
import solace.vm.internal.harv.instruction.Branch
import solace.vm.internal.harv.instruction.Define
import solace.vm.internal.harv.instruction.Div
import solace.vm.internal.harv.instruction.Eq
import solace.vm.internal.harv.instruction.Ge
import solace.vm.internal.harv.instruction.Goto
import solace.vm.internal.harv.instruction.Gt
import solace.vm.internal.harv.instruction.Instruction
import solace.vm.internal.harv.instruction.Label
import solace.vm.internal.harv.instruction.Le
import solace.vm.internal.harv.instruction.Lt
import solace.vm.internal.harv.instruction.Mod
import solace.vm.internal.harv.instruction.Mul
import solace.vm.internal.harv.instruction.Neq
import solace.vm.internal.harv.instruction.Not
import solace.vm.internal.harv.instruction.Or
import solace.vm.internal.harv.instruction.Print
import solace.vm.internal.harv.instruction.Push
import solace.vm.internal.harv.instruction.Put
import solace.vm.internal.harv.instruction.Sub

class NewStackMachine() {
    enum class ExecStatus {
        BLOCKED,
        SUCCESS,
        ERROR,
    }

    private var instructions = listOf<Instruction>()

    private val labels = mutableMapOf<String, Int>()
    private val variables = mutableMapOf<String, HarvVal>()

    private val stack = mutableListOf<HarvVal>()

    private var programCounter = 0;

    private inline fun intBinaryOp(operation: (Int, Int) -> Int) {
        if (stack.size >= 2) {
            val b = stack.removeLast()
            val a = stack.removeLast()

            if (a is HarvInt && b is HarvInt) {
                val result = operation(a.value, b.value)
                stack.addLast(HarvInt(result))
            } else {
                throw Exception("Both operands must be integers. Got: ${a::class.simpleName} and ${b::class.simpleName}")
            }
        } else {
            throw Exception("For this operation there should be two or more arguments in stack")
        }
    }

    private fun step(prg: List<Instruction>) {
        val instruction = prg[programCounter]
        programCounter++
        executeInstruction(instruction)
    }

    fun loadByteCode(byteCode: String) {
        val einstrs = AsmParser.parseEncodedInstructions(byteCode)
        val isntrsStrings = AsmParser.decodeInstructions(einstrs)
        instructions = AsmParser.parseIntoInstrs(isntrsStrings)

        findLabels()
    }

    fun findLabels() {
        for (i in instructions) {
            if (i is Label) {
                executeInstruction(i)
            }
            programCounter++
        }
        programCounter = 0
    }

    fun tryInit(): ExecStatus {
        val initProgram = instructions.filter { x -> x.isInit }
        programCounter = 0

        try {
            while (programCounter < initProgram.size) {
                step(initProgram)
            }
            return ExecStatus.SUCCESS
        } catch (e: Exception) {
            System.err.println(e.message)
            return ExecStatus.ERROR
        }
    }

    fun tryRun(): ExecStatus {
        val runProgram = instructions.filter { x -> !x.isInit }
        programCounter = 0

        try {
            while (programCounter < runProgram.size) {
                step(runProgram)
            }
            return ExecStatus.SUCCESS
        } catch (e: Exception) {
            System.err.println(e.message)
            return ExecStatus.ERROR
        }
    }

    fun getStack(): List<HarvVal> = stack

    private fun executeInstruction(inst: Instruction) {
        when (inst) {
            is Push -> {
               if (inst.int != null) {
                   stack.add(HarvInt(inst.int!!.toInt()))
               } else if (inst.string != null) {
                   stack.add(HarvString(inst.string!!))
               } else if (inst.identifier != null) {
                   if (!variables.contains(inst.identifier)) {
                       throw Exception("Variable ${inst.identifier} is not defined")
                   }

                   stack.add(variables[inst.identifier!!]!!)
               }
            }

            is Put -> {
                val name = inst.valueName!!

                if (!variables.contains(name)) {
                    throw Exception("Variable $name is not defined")
                }

                val x = stack.removeLast()

                val existingVariable = variables[name]

                if (existingVariable != null) {
                    when (existingVariable) {
                        is HarvInt -> {
                            if (x !is HarvInt) {
                                throw Exception("Type mismatch for variable $name: expected HarvInt, got ${x::class.simpleName}")
                            }
                        }
                        is HarvString -> {
                            if (x !is HarvString) {
                                throw Exception("Type mismatch for variable $name: expected HarvString, got ${x::class.simpleName}")
                            }
                        }
                        is HarvIdentifier -> {
                            if (x !is HarvIdentifier) {
                                throw Exception("Type mismatch for variable $name: expected HarvIdentifier, got ${x::class.simpleName}")
                            }
                        }
                        is HarvFifo -> {
                            if (x !is HarvFifo) {
                                throw Exception("Type mismatch for variable $name: expected HarvFifo, got ${x::class.simpleName}")
                            }
                        }
                    }
                }

                variables[name] = x
            }

            is Branch -> {
                val condition = stack.removeLast() as? HarvInt ?: throw Exception("Condition should be int")

                if (condition.value != 0) {
                    val adr = labels[inst.labelIfTrue]

                    programCounter = adr!!
                } else {
                    val adr = labels[inst.labelIfFalse]

                    programCounter = adr!!
                }
            }

            is Define -> {
                val name = inst.name!!

                if (variables.contains(name)) {
                    throw Exception("Cannot define variable ${name}. Already exists")
                }

                if (inst.type == "int") {
                    variables[name] = HarvInt(0)
                } else if (inst.type == "string") {
                    variables[name] = HarvString("")
                }

            }

            is Add -> {
                val b = stack.removeLast()
                when (val a = stack.removeLast()) {
                    is HarvInt if b is HarvInt -> {
                        stack.addLast(HarvInt(a.value + b.value))
                    }

                    is HarvInt if b is HarvString -> {
                        stack.addLast(HarvString(a.value.toString() + b.value))
                    }

                    is HarvString if b is HarvInt -> {
                        stack.addLast(HarvString(a.value + b.value.toString()))
                    }

                    is HarvString if b is HarvString -> {
                        stack.addLast(HarvString(a.value + b.value))
                    }
                }
            }

            is Sub -> {
                intBinaryOp { a, b -> a - b }
            }

            is Mul -> {
                intBinaryOp { a, b -> a * b }
            }

            is Div -> {
                intBinaryOp { a, b ->
                    if (b == 0) throw ArithmeticException("Division by zero")
                    a / b
                }
            }

            is Mod -> {
                intBinaryOp { a, b ->
                    if (b == 0) throw ArithmeticException("Modulo by zero")
                    a % b
                }
            }

            is Lt -> {
                intBinaryOp { a, b -> if (a < b) 1 else 0 }
            }

            is Gt -> {
                intBinaryOp { a, b -> if (a > b) 1 else 0 }
            }

            is Le -> {
                intBinaryOp { a, b -> if (a <= b) 1 else 0 }
            }

            is Ge -> {
                intBinaryOp { a, b -> if (a >= b) 1 else 0 }
            }

            is Eq -> {
                intBinaryOp { a, b -> if (a == b) 1 else 0 }
            }

            is Neq -> {
                intBinaryOp { a, b -> if (a != b) 1 else 0 }
            }

            is And -> {
                intBinaryOp { a, b ->
                    val boolA = a != 0
                    val boolB = b != 0
                    if (boolA && boolB) 1 else 0
                }
            }

            is Or -> {
                intBinaryOp { a, b ->
                    val boolA = a != 0
                    val boolB = b != 0
                    if (boolA || boolB) 1 else 0
                }
            }

            is Not -> {
                if (stack.isNotEmpty()) {
                    val value = stack.removeLast()
                    if (value is HarvInt) {
                        val result = if (value.value == 0) 1 else 0
                        stack.add(HarvInt(result))
                    }
                }
            }

            is Label -> {
                if (labels.contains(inst.labelName))
                    throw Exception("Label ${inst.labelName} already exists");

                if (inst.labelName == null)
                    throw IllegalArgumentException("Label name cannot be null")

                labels[inst.labelName!!] = programCounter
            }

            is Goto -> {
                if (!labels.contains(inst.labelName))
                    throw Exception("No label ${inst.labelName}")

                val adr = labels[inst.labelName]

                programCounter = adr!!
            }

            is Print -> {
                when (val x = stack.removeLast()) {
                    is HarvInt -> {
                        println(x.value)
                    }
                    is HarvString -> {
                        println(x.value)
                    }
                }
            }
        }
    }
}