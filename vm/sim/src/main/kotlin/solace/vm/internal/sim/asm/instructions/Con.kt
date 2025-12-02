package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class Con : Instruction {
    // syntax: .con $mux00@in1 $add00@out ?
    //         instr leafName leafPortName leafName leafPortName isInit (optional)
    var leafName1: String? = null
    var leafPortName1: String? = null
    var leafName2: String? = null
    var leafPortName2: String? = null

    override var isInit: Boolean = false

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafNamePattern,
            AsmParser.leafPortPattern,
            AsmParser.leafNamePattern,
            AsmParser.leafPortPattern,
            AsmParser.isInitPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        leafName1 = matches[1]
            ?: throw NoLeafNameFound(s)
        leafPortName1 = matches[2]
            ?: throw NoLeafPortNameFound(s)
        leafName2 = matches[3]
            ?: throw NoLeafNameFound(s)
        leafPortName2 = matches[4]
            ?: throw NoLeafPortNameFound(s)

        isInit = matches[5] != null
    }
}