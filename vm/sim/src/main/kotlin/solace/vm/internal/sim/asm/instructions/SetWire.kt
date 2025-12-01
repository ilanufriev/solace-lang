package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class SetWire : Instruction {
    // syntax: .setwire $wire #5
    //         instr leafName immediate
    var wireName: String? = null
    var immediate: String? = null
    override var isInit: Boolean = false

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafNamePattern,
            AsmParser.immediateValuePattern,
            AsmParser.isInitPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        wireName = matches[1]
            ?: throw NoLeafNameFound(s)
        immediate = matches[2]
            ?: throw NoImmediateFound(s)

        isInit = matches[3] != null
    }
}