package solace.vm.internal.harv.asm

import java.lang.Exception

open class InstructionException(val msg: String) : Exception(msg)
class NoInstructionPatternFound(val instr: String) : InstructionException("No instruction pattern matched in instruction $instr")
class NoValueTypePatternFound(val instr: String) : InstructionException("No value type pattern matched in instruction $instr")
class NoIdentifierPatternFound(val instr: String) : InstructionException("No identifier pattern matched in instruction $instr")
class NoImmediateValuePatternFound(val instr: String) : InstructionException("No immediate value pattern matched in instruction $instr")
class NoStringValuePatternFound(val instr: String) : InstructionException("No string value pattern matched in instruction $instr")
class IllegalInstruction(val instr: String) : InstructionException(instr)

interface Instruction {
    var isInit: Boolean
    abstract fun parse(s: String)
}