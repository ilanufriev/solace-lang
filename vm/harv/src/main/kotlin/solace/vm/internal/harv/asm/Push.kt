package solace.vm.internal.harv.asm

import solace.vm.internal.harv.types.HarvIdentifier
import solace.vm.internal.harv.types.HarvInt
import solace.vm.internal.harv.types.HarvString

class Push() : Instruction {
    var string: String? = null
    var int: String? = null
    var identifier: String? = null
    override var isInit: Boolean = false

    constructor(
        int: String? = null,
        string: String? = null,
        identifier: String? = null,
        isInit: Boolean = false
    ) : this() {
        this.int = int
        this.string = string
        this.identifier = identifier
        this.isInit = isInit
    }

    override fun parse(s: String) {
        string = null
        int = null
        identifier = null

        val patternVariants = mapOf(
            HarvInt::class.simpleName!! to arrayOf(
                AsmParser.instructionPattern,
                AsmParser.immediateValuePattern,
                AsmParser.isInitPattern,
            ),
            HarvString::class.simpleName!! to arrayOf(
                AsmParser.instructionPattern,
                AsmParser.stringPattern,
                AsmParser.isInitPattern,
            ),
            HarvIdentifier::class.simpleName!! to arrayOf(
                AsmParser.instructionPattern,
                AsmParser.valueNamePattern,
                AsmParser.isInitPattern,
            )
        )

        for ((type, pattern) in patternVariants) {
            val m = AsmParser.matchPatterns(s, pattern)
            m[0] ?: throw NoInstructionPatternFound(s)
            isInit = m[2] != null
            when (type) {
                HarvInt::class.simpleName!! -> {
                    int = m[1] ?: continue
                    return
                }
                HarvString::class.simpleName!! -> {
                    m[1] ?: continue
                    string = m[1]!!.substring(1, m[1]!!.length - 1)
                    return
                }
                HarvIdentifier::class.simpleName!! -> {
                    identifier = m[1] ?: continue
                    return
                }
            }
        }

        throw InstructionException("Illegal instruction format: $s")
    }

    override fun toString(): String {
        // Determine which operand is present and format accordingly
        val operand = when {
            int != null -> "${AsmParser.immediateValuePrefix}$int"
            string != null -> "${AsmParser.stringPrefix}\"$string\""
            identifier != null -> "${AsmParser.valueNamePrefix}$identifier"
            else -> ""
        }

        return AsmParser.instructionPrefix +
                "${this::class.simpleName!!.lowercase()} " +
                operand +
                if (isInit) " ?" else ""
    }
}