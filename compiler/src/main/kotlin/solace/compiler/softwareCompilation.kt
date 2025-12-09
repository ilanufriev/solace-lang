package solace.compiler

import solace.compiler.antlr.SolaceParser
import solace.compiler.visitors.SoftwareVisitor
import solace.compiler.visitors.SoftwareVisitor.SoftwareVisitorException
import solace.vm.internal.harv.asm.AsmParser
import java.nio.charset.StandardCharsets

fun buildSoftwareBytecode(program: SolaceParser.ProgramContext): Map<String, ByteArray> {
    val nodes = (SoftwareVisitor().visit(program) as? List<*>)?.filterIsInstance<SoftwareVisitor.Node>().orEmpty()
    return buildSoftwareBytecode(nodes)
}

fun buildSoftwareBytecode(nodes: List<SoftwareVisitor.Node>): Map<String, ByteArray> {
    val bytecode = linkedMapOf<String, ByteArray>()
    nodes.forEach { node ->
        val name = node.name ?: error("Software node without a name")
        require(!bytecode.containsKey(name)) { "Duplicate software node '$name' while building bytecode" }

        val initEncoded = AsmParser.encodeInstructions(node.initCode).joinToString("")
        val runEncoded = AsmParser.encodeInstructions(node.runCode).joinToString("")

        val initBytes = initEncoded.toByteArray(StandardCharsets.UTF_8)
        val runBytes = runEncoded.toByteArray(StandardCharsets.UTF_8)

        bytecode[name] = buildSolbcContainer(NodeType.SOFTWARE, initBytes, runBytes)
    }
    return bytecode
}
