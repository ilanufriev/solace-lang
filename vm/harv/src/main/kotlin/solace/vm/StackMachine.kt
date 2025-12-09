package solace.vm

import solace.vm.internal.harv.asm.Add
import solace.vm.internal.harv.asm.And
import solace.vm.internal.harv.asm.AsmParser
import solace.vm.internal.harv.asm.Branch
import solace.vm.internal.harv.asm.Define
import solace.vm.internal.harv.asm.Div
import solace.vm.internal.harv.asm.Eq
import solace.vm.internal.harv.asm.Ge
import solace.vm.internal.harv.asm.Goto
import solace.vm.internal.harv.asm.Gt
import solace.vm.internal.harv.asm.Instruction
import solace.vm.internal.harv.asm.Label
import solace.vm.internal.harv.asm.Le
import solace.vm.internal.harv.asm.Lt
import solace.vm.internal.harv.asm.Mod
import solace.vm.internal.harv.asm.Mul
import solace.vm.internal.harv.asm.Neq
import solace.vm.internal.harv.asm.Not
import solace.vm.internal.harv.asm.Or
import solace.vm.internal.harv.asm.Print
import solace.vm.internal.harv.asm.Push
import solace.vm.internal.harv.asm.PushSize
import solace.vm.internal.harv.asm.Put
import solace.vm.internal.harv.asm.Sub
import solace.vm.internal.harv.types.HarvFifo
import solace.vm.internal.harv.types.HarvInt
import solace.vm.internal.harv.types.HarvString
import solace.vm.internal.harv.types.HarvVal
import kotlin.collections.get

class StackMachine() {
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
        executeInstruction(instruction)

        // Должен увеличиваться только в случае успешного выполнения инструкции
        // (если в fifo недостаточно данных то инструкция не завершится успешно)
        programCounter++
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
                if (labels.contains(i.labelName))
                    throw Exception("Label ${i.labelName} already exists");

                if (i.labelName == null)
                    throw IllegalArgumentException("Label name cannot be null")

                labels[i.labelName!!] = programCounter
            }
            programCounter++
        }
        programCounter = 0
    }

    fun tryInit(): ExecStatus {
        val initProgram = instructions.filter { x -> x.isInit }

        try {
            while (programCounter < initProgram.size) {
                step(initProgram)
            }

            // См. tryRun()
            programCounter = 0
            return ExecStatus.SUCCESS
        } catch (e: HarvFifo.HarvFifoIsEmpty) {
            return ExecStatus.BLOCKED
        } catch (e: Exception) {
            System.err.println(e.message)

            programCounter = 0
            return ExecStatus.ERROR
        }
    }

    fun tryRun(): ExecStatus {
        val runProgram = instructions.filter { x -> !x.isInit }

        try {
            while (programCounter < runProgram.size) {
                step(runProgram)
            }

            // Сбрасывается только после успешного выполнения или ошибки
            // в случае блокировки остается на месте пока в fifo не появится
            // достаточного количества данных
            programCounter = 0
            return ExecStatus.SUCCESS
        } catch (e: HarvFifo.HarvFifoIsEmpty) {
            return ExecStatus.BLOCKED
        } catch (e: Exception) {
            System.err.println(e.message)

            programCounter = 0
            return ExecStatus.ERROR
        }
    }

    fun getStack(): List<HarvVal> = stack
    
    private fun getFifo(fifoName: String): HarvFifo {
        val fifo = variables[fifoName]
            ?: throw Exception("No fifo $fifoName found")
        if (fifo !is HarvFifo) {
            throw Exception("$fifoName is not a fifo")
        }

        return fifo
    }

    fun pushToFifo(fifoName: String, value: Int) {
        val fifo = getFifo(fifoName)

        fifo.putToFifo(value)
    }

    fun pullFromFifo(fifoName: String): Int {
        val fifo = getFifo(fifoName)

        return fifo.pushFromFifo()
    }

    fun getFifoSize(fifoName: String): Int {
        val fifo = getFifo(fifoName)

        return fifo.queue.size
    }

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

                   val variable = variables[inst.identifier]!!
                   if (variable is HarvFifo) {
                       stack.add(HarvInt(variable.pushFromFifo()))
                   } else {
                       stack.add(variables[inst.identifier!!]!!)
                   }
               }
            }

            is PushSize -> {
                val fifoName = inst.fifoName!!
                val fifo = getFifo(fifoName)
                stack.add(HarvInt(fifo.queue.size))
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
                    }
                }

                // special case to handle fifos
                if (existingVariable is HarvFifo) {
                    if (x !is HarvInt) {
                        throw Exception("Only integers can be put into fifo")
                    }

                    existingVariable.putToFifo(x.value)
                } else {
                    variables[name] = x
                }
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

                if (inst.type == HarvInt.typeName) {
                    variables[name] = HarvInt(0)
                } else if (inst.type == HarvString.typeName) {
                    variables[name] = HarvString("")
                } else if (inst.type == HarvFifo.typeName) {
                    variables[name] = HarvFifo(name)
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
                // do nothing
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