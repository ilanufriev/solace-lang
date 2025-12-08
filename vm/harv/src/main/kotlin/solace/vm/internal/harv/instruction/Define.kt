package solace.vm.internal.harv.instruction

import solace.vm.internal.harv.AsmParser

class Define : Instruction {
    var type: String? = null
    var name: String? = null
    override var isInit: Boolean = false

    override fun parse(s: String) {
        val m = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.valueTypePattern,
            AsmParser.valueNamePattern,
            AsmParser.isInitPattern
        ))

        m[0] ?: throw NoInstructionPatternFound(s)
        type = m[1] ?: throw NoValueTypePatternFound(s)
        name = m[2] ?: throw NoIdentifierPatternFound(s)
        isInit = m[3] != null
    }

    override fun toString(): String {
        return AsmParser.instructionPrefix +
                "${this::class.simpleName!!.lowercase()} " +
                "${AsmParser.valueTypePrefix}${type} " +
                "${AsmParser.valueNamePrefix}${name}" +
                if(isInit) " ?" else ""
    }
}