package solace.vm.internal.sim.asm.instructions

class IllegalInstructionFormat(val msg: String) : Exception(msg)
class NoInstructionStartFound(val instr: String) : Exception("No instruction start found at instruction $instr")
class NoLeafTypeFound(val instr: String) : Exception("No leaf type found at instruction $instr")
class NoLeafNameFound(val instr: String) : Exception("No leaf name found at instruction $instr")
class NoLeafPortNameFound(val instr: String) : Exception("No leaf port name found at instruction $instr")
class NoFifoNameFound(val instr: String) : Exception("No fifo name found at instruction $instr")
class NoImmediateFound(val instr: String) : Exception("No immediate found at instruction $instr")
class IllegalInstruction(val line: String) : Exception("Illegal instruction: $line")

interface Instruction {
    val prefix: String
    var isInit: Boolean
    fun parse(s: String)
}