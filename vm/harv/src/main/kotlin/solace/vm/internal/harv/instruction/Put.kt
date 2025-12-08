package solace.vm.internal.harv.instruction

import solace.vm.internal.harv.AsmParser

class Put : Instruction {
    var valueName: String? = null

    override var isInit: Boolean = false
    override fun parse(s: String) {
        val m = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.valueNamePattern,
            AsmParser.isInitPattern
        ))

        m[0] ?: throw NoInstructionPatternFound(s)
        valueName = m[1] ?: throw NoIdentifierPatternFound(s)
        isInit = m[2] != null
    }

    override fun toString(): String {
        return AsmParser.instructionPrefix +
                "${this::class.simpleName!!.lowercase()} " +
                "${AsmParser.valueNamePrefix}$valueName" +
                if (isInit) " ?" else ""
    }

}