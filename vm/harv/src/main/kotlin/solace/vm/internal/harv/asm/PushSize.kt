package solace.vm.internal.harv.asm

class PushSize() : Instruction {
    var fifoName: String? = null

    constructor(fifoName: String, isInit: Boolean): this() {
        this.fifoName = fifoName
        this.isInit = isInit
    }

    override var isInit: Boolean = false
    override fun parse(s: String) {
        val m = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.valueNamePattern,
            AsmParser.isInitPattern
        ))

        m[0] ?: throw NoInstructionPatternFound(s)
        fifoName = m[1] ?: throw NoIdentifierPatternFound(s)
        isInit = m[2] != null
    }

    override fun toString(): String {
        return AsmParser.instructionPrefix +
                "${this::class.simpleName!!.lowercase()} " +
                AsmParser.valueNamePrefix + "$fifoName" +
                if (isInit) " ?" else ""
    }
}