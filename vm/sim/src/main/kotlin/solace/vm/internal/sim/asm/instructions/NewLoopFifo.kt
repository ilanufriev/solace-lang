package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class NewLoopFifo : Instruction {
    var fifoName: String? = null
    override var isInit: Boolean = false

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafNamePattern,
            AsmParser.isInitPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        fifoName = matches[1]
            ?: throw NoLeafNameFound(s)

        isInit = matches[2] != null
    }
}