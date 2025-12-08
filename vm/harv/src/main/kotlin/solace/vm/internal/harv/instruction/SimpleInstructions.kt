package solace.vm.internal.harv.instruction

import solace.vm.internal.harv.AsmParser

open class SimpleInstruction : Instruction {
    override var isInit: Boolean = false
    override fun parse(s: String) {
        val m = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.isInitPattern
        ))

        m[0] ?: throw NoInstructionPatternFound(s)
        isInit = m[1] != null
    }
}

class Add   : SimpleInstruction()
class Sub   : SimpleInstruction()
class Mul   : SimpleInstruction()
class Div   : SimpleInstruction()
class Mod   : SimpleInstruction()
class Lt    : SimpleInstruction()
class Gt    : SimpleInstruction()
class Le    : SimpleInstruction()
class Ge    : SimpleInstruction()
class Eq    : SimpleInstruction()
class Neq   : SimpleInstruction()
class And   : SimpleInstruction()
class Or    : SimpleInstruction()
class Not   : SimpleInstruction()
class Print : SimpleInstruction()
