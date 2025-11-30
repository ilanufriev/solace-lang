package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class OutFifoCon : Instruction {
    // syntax: .outfifocon $fifo1 $add01@in1
    //         instr leafName leafName portName
    var fifoName: String? = null
    var leafName: String? = null
    var leafPortName: String? = null

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafNamePattern,
            AsmParser.leafNamePattern,
            AsmParser.leafPortPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        fifoName = matches[1]
            ?: throw NoFifoNameFound(s)
        leafName = matches[2]
            ?: throw NoLeafNameFound(s)
        leafPortName = matches[3]
            ?: throw NoLeafPortNameFound(s)
    }
}