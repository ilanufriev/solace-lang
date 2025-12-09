package solace.vm.internal.harv.instruction

import solace.vm.internal.harv.AsmParser

class Branch() : Instruction {
    var labelIfTrue: String? = null
    var labelIfFalse: String? = null
    var labelEnd: String? = null

    constructor(labelIfTrue: String, labelIfFalse: String, labelEnd: String, isInit: Boolean) : this() {
        this.labelIfTrue = labelIfTrue
        this.labelIfFalse = labelIfFalse
        this.labelEnd = labelEnd
        this.isInit = isInit
    }

    override var isInit: Boolean = false
    override fun parse(s: String) {
        val m = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.valueNamePattern,
            AsmParser.valueNamePattern,
            AsmParser.valueNamePattern,
            AsmParser.isInitPattern
        ))

        m[0] ?: throw NoInstructionPatternFound(s)
        labelIfTrue = m[1] ?: throw NoIdentifierPatternFound(s)
        labelIfFalse = m[2] ?: throw NoIdentifierPatternFound(s)
        labelEnd = m[3] ?: throw NoIdentifierPatternFound(s)
        isInit = m[4] != null
    }

    override fun toString(): String {
        return AsmParser.instructionPrefix +
                "${this::class.simpleName!!.lowercase()} " +
                "${AsmParser.valueNamePrefix}$labelIfTrue " +
                "${AsmParser.valueNamePrefix}$labelIfFalse " +
                "${AsmParser.valueNamePrefix}$labelEnd" +
                if (isInit) " ?" else ""
    }
}