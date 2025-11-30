package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class Eval : Instruction {
    // Syntax: eval
    //         instr
    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
    }
}