package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.Instruction
import solace.vm.internal.sim.asm.instructions.New

class AsmBuilder {
    private val source = mutableListOf<Instruction>()

    fun new(leafType: String, leafName: String, isInit: Boolean = false): AsmBuilder {
        source.addLast(New(leafType, leafName, isInit))
        return this
    }

    fun con(fromLeafName: String, fromLeafPortName: String,
            toLeafName: String, toLeafPortName: String, isInit: Boolean = false): AsmBuilder {
        source.addLast(Con(fromLeafName, fromLeafPortName, toLeafName, toLeafPortName, isInit))
        return this
    }

    fun immCon(fromLeafName: String, fromLeafPortName: String, immediate: String, isInit: Boolean = false): AsmBuilder {
        source.addLast(ImmCon(fromLeafName, fromLeafPortName, immediate, isInit))
        return this
    }

    fun encode(): List<EncodedInstruction> {
        return AsmParser.encodeInstructions(source);
    }

    fun renderToByteCode(): String {
        return AsmParser.encodeInstructions(source).joinToString("")
    }
}