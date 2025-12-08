package solace.vm.internal.sim.asm.instructions

import solace.vm.internal.sim.asm.AsmParser

class Con() : Instruction {
    // syntax: .con $mux00@in1 $add00@out ?
    //         instr leafName leafPortName leafName leafPortName isInit (optional)
    override val prefix: String = ".con"
    var fromLeafName: String? = null
    var fromLeafPortName: String? = null
    var toLeafName: String? = null
    var toLeafPortName: String? = null

    override var isInit: Boolean = false

    constructor(fromLeafName: String, fromLeafPortName: String,
                toLeafName: String, toLeafPortName: String,
                isInit: Boolean): this() {
        this.fromLeafName = fromLeafName
        this.fromLeafPortName = fromLeafPortName
        this.toLeafName = toLeafName
        this.toLeafPortName = toLeafPortName
        this.isInit = isInit
    }

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
        fromLeafName = matches[1]
            ?: throw NoLeafNameFound(s)
        fromLeafPortName = matches[2]
            ?: throw NoLeafPortNameFound(s)
        toLeafName = matches[3]
            ?: throw NoLeafNameFound(s)
        toLeafPortName = matches[4]
            ?: throw NoLeafPortNameFound(s)

        isInit = matches[5] != null
    }

    override fun toString(): String {
        return ".con \$$fromLeafName@$fromLeafPortName \$$toLeafName@$toLeafPortName" + (if (isInit) "?" else "");
    }
}