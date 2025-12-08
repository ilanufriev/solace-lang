package solace.compiler

import solace.compiler.antlr.SolaceParser
import solace.compiler.visitors.HardwareVisitor
import solace.vm.internal.sim.asm.AsmParser
import solace.vm.internal.sim.asm.instructions.Instruction
import java.nio.charset.StandardCharsets

fun buildHardwareBytecode(program: SolaceParser.ProgramContext): Map<String, ByteArray> {
    val visited = HardwareVisitor().visit(program)
    val nodes = (visited as? List<*>)?.filterIsInstance<HardwareVisitor.Node>().orEmpty()
    return buildHardwareBytecode(nodes)
}

fun buildHardwareBytecode(nodes: List<HardwareVisitor.Node>): Map<String, ByteArray> {
    val bytecode = linkedMapOf<String, ByteArray>()
    nodes.forEach { node ->
        val name = node.name ?: error("Hardware node without a name")
        require(!bytecode.containsKey(name)) { "Duplicate hardware node '$name' while building bytecode" }
        bytecode[name] = buildSolbcContainer(
            NodeType.HARDWARE,
            encodeInstructions(node.initCode),
            encodeInstructions(node.runCode)
        )
    }
    return bytecode
}

private fun encodeInstructions(instructions: List<Instruction>): ByteArray {
    if (instructions.isEmpty()) return byteArrayOf()
    val encoded = AsmParser.encodeInstructions(instructions)
    val encodedString = encoded.joinToString("")
    return encodedString.toByteArray(StandardCharsets.UTF_8)
}
