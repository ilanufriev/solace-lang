package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class SetPort : Instruction {
    // syntax: .setport $add@in1 #10
    var leafName: String? = null
    var leafPortName: String? = null
    var immediate: String? = null

    override var isInit: Boolean = false

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafNamePattern,
            AsmParser.leafPortPattern,
            AsmParser.immediateValuePattern,
            AsmParser.isInitPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        leafName = matches[1]
            ?: throw NoLeafNameFound(s)
        leafPortName = matches[2]
            ?: throw NoLeafPortNameFound(s)
        immediate = matches[3]
            ?: throw NoImmediateFound(s)
        isInit = matches[4] != null
    }
}