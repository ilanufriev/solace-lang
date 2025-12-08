package solace.vm.internal.harv.instruction

import solace.vm.internal.harv.AsmParser

class Label : Instruction {
    var labelName: String? = null
    override var isInit: Boolean = false

    override fun parse(s: String) {
        val m = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.valueNamePattern,
            AsmParser.isInitPattern
        ))
    }

    override fun toString(): String {
        return AsmParser.instructionPrefix +
                "${this::class.simpleName!!.lowercase()} " +
                "${AsmParser.valueNamePrefix}$labelName" +
                if (isInit) " ?" else ""
    }
}