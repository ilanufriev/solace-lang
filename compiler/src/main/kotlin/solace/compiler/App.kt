package solace.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser
import java.nio.file.Files
import java.nio.file.Path

private fun parseFile(path: Path) {
    val input = CharStreams.fromPath(path)
    val lexer = SolaceLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = SolaceParser(tokens)

    val tree = parser.program()
    println("Parsed '${path.toAbsolutePath()}'. Syntax errors: ${parser.numberOfSyntaxErrors}")
    println(prettyTree(tree, parser))
}

fun main(args: Array<String>) {
    val file = args.firstOrNull()?.let { Path.of(it) } ?: Path.of("../pseudocode.solace")
    require(Files.exists(file)) { "Input file not found: ${file.toAbsolutePath()}" }
    parseFile(file)
}
