package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class NewOutFifo : Instruction {
    // Syntax: .outfifo $fifo1
    //         instr leafName
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
            ?: throw NoFifoNameFound(s)
        isInit = matches[2] != null
    }
}