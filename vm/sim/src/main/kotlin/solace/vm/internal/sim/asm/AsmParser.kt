package solace.vm.internal.sim.asm

import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.Eval
import solace.vm.internal.sim.asm.instructions.IllegalInstruction
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.InFifo
import solace.vm.internal.sim.asm.instructions.OutFifo
import solace.vm.internal.sim.asm.instructions.InFifoCon
import solace.vm.internal.sim.asm.instructions.Instruction
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.asm.instructions.OutFifoCon

object AsmParser {
    const val identifierPattern = "[a-zA-Z][a-zA-Z0-9]*"
    const val numberPattern = "[0-9]+"
    const val instructionPattern = ".${identifierPattern}"
    const val leafTypePattern = "%${identifierPattern}"
    const val leafNamePattern = "\\\$${identifierPattern}"
    const val leafPortPattern = "@${identifierPattern}"
    const val immediateValuePattern = "#${numberPattern}"

    val instructionTypes = mapOf<String, (() -> Instruction)?>(
        ".new" to ::New,
        ".con" to ::Con,
        ".infifocon" to ::InFifoCon,
        ".outfifocon" to ::OutFifoCon,
        ".immcon" to ::ImmCon,
        ".infifo" to ::InFifo,
        ".outfifo" to ::OutFifo,
        ".eval" to ::Eval
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

    fun matchAndTrim(s: String, regex: Regex): Pair<MatchResult?, String> {
        val buffer = s.substring(indexOfFirstNonWs(s, 0))
        val match = regex.find(buffer)
            ?: return Pair(null, s)

        if (match.range.first > 0) {
            return Pair(null, s)
        }

        val newString = buffer.substring(indexOfFirstNonWs(buffer, match.range.last + 1))
        return Pair(match, newString)
    }

    fun indexOfFirstNonWs(s: String, startPos: Int): Int {
        for (i in startPos..(s.length - 1)) {
            if (s[i] != ' ')
                return i;
        }

        return (s.length - 1)
    }

    fun parseIntoInstrs(source: String): List<Instruction> {
        val instrList = mutableListOf<Instruction>()
        val instrStringList = splitIntoInstrStrings(source);

        for (instrString in instrStringList) {
            var instrFound = false

            for ((prefix, ctor) in instructionTypes) {
                if (!instrString.startsWith(prefix)) {
                    continue
                }

                instrFound = true

                val instr = ctor!!.invoke()
                instr.parse(instrString)
                instrList.addLast(instr)
                break
            }

            if (!instrFound) {
                throw IllegalInstruction(instrString)
            }
        }

        return instrList
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

    fun isInstruction(s: String): Boolean {
        return s.startsWith(".")
    }
}


