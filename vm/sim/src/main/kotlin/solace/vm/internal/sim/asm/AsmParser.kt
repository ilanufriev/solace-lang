package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.FifoCon
import solace.vm.internal.sim.asm.instructions.IllegalInstruction
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.NewInFifo
import solace.vm.internal.sim.asm.instructions.NewOutFifo
import solace.vm.internal.sim.asm.instructions.Instruction
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.NewLoopFifo
import solace.vm.internal.sim.asm.instructions.NewWire
import solace.vm.internal.sim.asm.instructions.SetWire
import kotlin.math.min

object AsmParser {
    const val identifierPattern = "[a-zA-Z][a-zA-Z0-9]*"
    const val numberPattern = "[0-9]+"
    const val isInitPattern = "\\?"
    const val instructionPattern = ".${identifierPattern}"
    const val leafTypePattern = "%${identifierPattern}"
    const val leafNamePattern = "\\\$${identifierPattern}"
    const val leafPortPattern = "@${identifierPattern}"
    const val immediateValuePattern = "#${numberPattern}"

    data class InstructionType(val strCode: String, val opCode: Byte)

    data class EncodedInstruction(var opCode: Byte, var length: Byte, var params: String) {
        override fun toString(): String {
            return opCode.toHexString() + length.toHexString() + params
        }
    }

    val instructionTypes = mapOf<InstructionType, (() -> Instruction)?>(
        InstructionType(".new", 0x01) to ::New,
        InstructionType(".con", 0x02) to ::Con,
        InstructionType(".fifocon", 0x03) to ::FifoCon,
        InstructionType(".immcon", 0x04) to ::ImmCon,
        InstructionType(".newinfifo", 0x05) to ::NewInFifo,
        InstructionType(".newoutfifo", 0x06) to ::NewOutFifo,
        InstructionType(".newloopfifo", 0x07) to ::NewLoopFifo,
        InstructionType(".newwire", 0x08) to ::NewWire,
        InstructionType(".setwire", 0x09) to ::SetWire,
    )

    fun matchPatterns(s: String, matchPatterns: Array<String>): ArrayList<String?> {
        val matches = arrayListOf<String?>()
        var buffer = s

        for (pat in matchPatterns) {
            val mp = matchAndTrim(buffer, Regex(pat))
            val match = mp.first

            matches.addLast(match?.value?.substring(1))
            buffer = mp.second
        }

        return matches
    }

    private fun removeWhitespace(source: String): String {
        return source.filter { c ->  !c.isWhitespace() }
    }

    fun parseEncodedInstructions(bytecode: String): List<EncodedInstruction> {
        val encodedList = mutableListOf<EncodedInstruction>()
        var buffer = bytecode
        while (!buffer.isEmpty()) {
            val opCode = buffer.substring(0, 2).hexToByte()
            val length = buffer.substring(2, 4).hexToByte()
            val params = buffer.substring(4, min(4 + length, buffer.length));
            encodedList.addLast(EncodedInstruction(opCode, length, params))
            buffer = buffer.substring(4 + length)
        }

        return encodedList
    }

    fun decodeInstructions(bytecode: List<EncodedInstruction>): List<String> {
        val decodedList = mutableListOf<String>()
        for (einstr in bytecode) {
            val itype = matchOpCode(einstr.opCode)
                ?: throw IllegalInstruction(einstr.toString())
            decodedList.addLast(buildString { append(itype.strCode + einstr.params) })
        }

        return decodedList
    }

    fun encodeInstructions(source: String): List<EncodedInstruction> {
        val encodedList = mutableListOf<EncodedInstruction>()
        val instrStrings = splitIntoInstrStrings(source).map { s -> removeWhitespace(s) };
        for (instrString in instrStrings) {
            val (match, leftover) = matchAndTrim(instrString, Regex(instructionPattern))
            match
                ?: throw IllegalInstruction(instrString)
            val instructionType = matchLongest(match.value)
                ?: throw IllegalInstruction(instrString)

            encodedList.addLast(
                EncodedInstruction(
                    instructionType.opCode,
                    leftover.length.toByte(),
                    leftover
                )
            )
        }

        return encodedList
    }

    private fun matchAndTrim(s: String, regex: Regex): Pair<MatchResult?, String> {
        val buffer = s.substring(indexOfFirstNonWs(s, 0))
        val match = regex.find(buffer)
            ?: return Pair(null, s)

        if (match.range.first > 0) {
            return Pair(null, s)
        }

        val newString = buffer.substring(indexOfFirstNonWs(buffer, match.range.last + 1))
        return Pair(match, newString)
    }

    private fun indexOfFirstNonWs(s: String, startPos: Int): Int {
        for (i in startPos..(s.length - 1)) {
            if (s[i] != ' ')
                return i;
        }

        return (s.length - 1)
    }

    private fun matchOpCode(opCode: Byte): InstructionType? {
        val matches = instructionTypes.filter {
            (itype, _) -> itype.opCode == opCode
        }

        if (matches.isEmpty())
            return null

        return matches.keys.first()
    }

    private fun matchLongest(instrString: String): InstructionType? {
        val matches = instructionTypes.filter {
            (itype, ctor) -> instrString.startsWith(itype.strCode)
        }

        if (matches.isEmpty())
            return null

        return matches.maxBy { (i, c) -> i.strCode.length }.key
    }

    fun parseIntoInstrs(source: List<String>): List<Instruction> {
        val instrList = mutableListOf<Instruction>()

        for (instrString in source) {
            val instructionType = matchLongest(instrString)
                ?: throw IllegalInstruction(instrString);

            val ctor = instructionTypes[instructionType]!!
            val instr = ctor.invoke()
            instr.parse(instrString)
            instrList.addLast(instr)
        }

        return instrList
    }

    fun parseIntoInstrs(source: String): List<Instruction> {
        val instrStringList = splitIntoInstrStrings(source);
        return parseIntoInstrs(instrStringList)
    }

    private fun splitIntoInstrStrings(source: String): List<String> {
        val instrList = mutableListOf<String>()

        var first = source.indexOf('.', 0)
        if (first < 0) {
            return instrList
        }

        while (first != source.length) {
            var last = source.indexOf('.', first + 1)
            if (last < 0) {
                last = source.length
            }

            instrList.addLast(source.substring(first, last).removeSurrounding(" "))
            first = last
        }

        return instrList
    }
}


