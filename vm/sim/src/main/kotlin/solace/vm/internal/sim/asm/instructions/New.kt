package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class New() : Instruction {
    // syntax: .new %Mux2 $mux00
    //         instr leafType leafName
    override val prefix: String = ".new"
    var leafType: String? = null
    var leafName: String? = null
    override var isInit: Boolean = false

    constructor(leafType: String, leafName: String, isInit: Boolean): this() {
        this.leafType = leafType
        this.leafName = leafName
        this.isInit = isInit
    }

    override fun parse(s: String) {
        val matches = AsmParser.matchPatterns(s, arrayOf(
            AsmParser.instructionPattern,
            AsmParser.leafTypePattern,
            AsmParser.leafNamePattern,
            AsmParser.isInitPattern
        ))

        matches[0]
            ?: throw NoInstructionStartFound(s)
        leafType = matches[1]
            ?: throw NoLeafTypeFound(s)
        leafName = matches[2]
            ?: throw NoLeafNameFound(s)
        isInit = matches[3] != null
    }

    override fun toString(): String {
        return ".new %$leafType \$$leafName ${if (isInit) "?" else ""}"
    }
}