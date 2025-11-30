package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class InFifo : Instruction {
    // Syntax: .infifo $fifo1
    //         instr leafName
    var fifoName: String? = null

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafNamePattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        fifoName = matches[1]
            ?: throw NoFifoNameFound(s)
    }
}