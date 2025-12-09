package solace.vm.internal.harv.asm

import kotlin.math.min
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class InstructionType(val strCode: String, val opCode: Byte)

data class EncodedInstruction(var opCode: Byte, var length: Short, var params: String) {
    override fun toString(): String {
        return opCode.toHexString() + length.toHexString() + params
    }

    fun toByteArray(): ByteArray {
        val paramBytes = params.toByteArray(Charsets.US_ASCII)
        val buffer = ByteBuffer.allocate(1 + 2 + paramBytes.size)!!
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(opCode)          // opCode (1 byte)
        buffer.putShort(length)     // length (2 bytes)
        buffer.put(paramBytes)      // params (UTF‑8)
        return buffer.array()
    }
}

fun classToInstructionName(instr: Instruction): String {
    return AsmParser.instructionPrefix + instr::class.simpleName!!.lowercase()
}

object AsmParser {
    const val instructionPrefix = "."
    const val initPrefix = "\\?"
    const val valueTypePrefix = "%"
    const val valueNamePrefix = "$"
    const val immediateValuePrefix = "#"
    const val stringPrefix = "+"

    const val identifierPattern = "[a-zA-Z_][a-zA-Z_0-9]*"
    const val numberPattern = "[-]?[0-9]+"
    const val isInitPattern = initPrefix
    const val instructionPattern = instructionPrefix + identifierPattern
    const val valueTypePattern = valueTypePrefix + identifierPattern
    const val valueNamePattern = "\\" + valueNamePrefix + identifierPattern
    const val immediateValuePattern = immediateValuePrefix + numberPattern
    const val stringPattern = "[$stringPrefix]" + "\\\".*\\\""

    val instructionTypes = mapOf<InstructionType, (() -> Instruction)?>(
        InstructionType(instructionPrefix + Branch::class.simpleName!!.lowercase(), 0x01) to ::Branch,
        InstructionType(instructionPrefix + Define::class.simpleName!!.lowercase(), 0x02) to ::Define,
        InstructionType(instructionPrefix + Goto::class.simpleName!!.lowercase(), 0x03) to ::Goto,
        InstructionType(instructionPrefix + Label::class.simpleName!!.lowercase(), 0x04) to ::Label,
        InstructionType(instructionPrefix + Push::class.simpleName!!.lowercase(), 0x05) to ::Push,
        InstructionType(instructionPrefix + Put::class.simpleName!!.lowercase(), 0x06) to ::Put,
        InstructionType(instructionPrefix + Add::class.simpleName!!.lowercase(), 0x07) to ::Add,
        InstructionType(instructionPrefix + Sub::class.simpleName!!.lowercase(), 0x08) to ::Sub,
        InstructionType(instructionPrefix + Mul::class.simpleName!!.lowercase(), 0x09) to ::Mul,
        InstructionType(instructionPrefix + Div::class.simpleName!!.lowercase(), 0x0A) to ::Div,
        InstructionType(instructionPrefix + Mod::class.simpleName!!.lowercase(), 0x0B) to ::Mod,
        InstructionType(instructionPrefix + Lt::class.simpleName!!.lowercase(), 0x0C) to ::Lt,
        InstructionType(instructionPrefix + Gt::class.simpleName!!.lowercase(), 0x0D) to ::Gt,
        InstructionType(instructionPrefix + Le::class.simpleName!!.lowercase(), 0x0E) to ::Le,
        InstructionType(instructionPrefix + Ge::class.simpleName!!.lowercase(), 0x0F) to ::Ge,
        InstructionType(instructionPrefix + Eq::class.simpleName!!.lowercase(), 0x10) to ::Eq,
        InstructionType(instructionPrefix + Neq::class.simpleName!!.lowercase(), 0x11) to ::Neq,
        InstructionType(instructionPrefix + And::class.simpleName!!.lowercase(), 0x12) to ::And,
        InstructionType(instructionPrefix + Or::class.simpleName!!.lowercase(), 0x13) to ::Or,
        InstructionType(instructionPrefix + Not::class.simpleName!!.lowercase(), 0x14) to ::Not,
        InstructionType(instructionPrefix + Print::class.simpleName!!.lowercase(), 0x15) to ::Print,
        InstructionType(instructionPrefix + PushSize::class.simpleName!!.lowercase(), 0x16) to ::Push,
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

    fun parseEncodedInstructions(byteCode: String): List<EncodedInstruction> {
        val encodedList = mutableListOf<EncodedInstruction>()
        var buffer = byteCode
        while (!buffer.isEmpty()) {
            val opCode = buffer.substring(0, 2).hexToByte()
            val length = buffer.substring(2, 6).hexToShort()
            val params = buffer.substring(6, min(6 + length, buffer.length));
            encodedList.addLast(EncodedInstruction(opCode, length, params))
            buffer = buffer.substring(6 + length)
        }

        return encodedList
    }

    fun decodeInstructions(byteCode: ByteArray): List<EncodedInstruction> {
        val instrs = mutableListOf<EncodedInstruction>()
        val buffer = ByteBuffer.wrap(byteCode)!!
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        while (buffer.remaining() > 0) {
            val opCode = buffer.get();
            val length = buffer.getShort();
            val paramBytes = ByteArray(length.toInt())
            buffer.get(paramBytes)
            instrs.addLast(
                EncodedInstruction(
                    opCode,
                    length,
                    paramBytes.toString(Charsets.US_ASCII)
                )
            )
        }

        return instrs
    }

    fun decodeInstructions(byteCode: Iterable<EncodedInstruction>): List<String> {
        val decodedList = mutableListOf<String>()
        for (einstr in byteCode) {
            val itype = matchOpCode(einstr.opCode)
                ?: throw IllegalInstruction(einstr.toString())
            decodedList.addLast(buildString { append(itype.strCode + einstr.params) })
        }

        return decodedList
    }

    fun encodeInstructionsToByteCode(instrs: Iterable<EncodedInstruction>): ByteArray {
        val byteCodes = mutableListOf<ByteArray>()
        for (instr in instrs) {
            byteCodes.addLast(instr.toByteArray())
        }

        val byteBuffer = ByteBuffer.allocate(byteCodes.sumOf { i -> i.size })!!
        for (byteCode in byteCodes) {
            for (byte in byteCode) {
                byteBuffer.put(byte)
            }
        }

        return byteBuffer.array()
    }

    fun encodeInstructions(instrs: Iterable<Instruction>): List<EncodedInstruction> {
        val instrStrings = mutableListOf<String>()
        for (instr in instrs) {
            instrStrings.addLast(instr.toString())
        }

        return encodeInstructionsFromStringList(instrStrings.map { s -> removeWhitespace(s) })
    }

    fun encodeInstructionsFromString(source: String): List<EncodedInstruction> {
        val instrStrings = splitIntoInstrStrings(source).map { s -> removeWhitespace(s) };
        return encodeInstructionsFromStringList(instrStrings)
    }

    fun encodeInstructionsFromStringList(instrStrings: Iterable<String>): List<EncodedInstruction> {
        val encodedList = mutableListOf<EncodedInstruction>()
        for (instrString in instrStrings) {
            val (match, leftover) = matchAndTrim(instrString, Regex(instructionPattern))
            match
                ?: throw IllegalInstruction(instrString)
            val instructionType = matchLongest(match.value)
                ?: throw IllegalInstruction(instrString)

            encodedList.addLast(
                EncodedInstruction(
                    instructionType.opCode,
                    leftover.length.toShort(),
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

    fun parseIntoInstrs(source: Iterable<String>): List<Instruction> {
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

    fun splitIntoInstrStrings(source: String): List<String> {
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


