package solace.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser
import solace.compiler.visitors.HardwareVisitor.HardwareVisitorException
import solace.compiler.visitors.SoftwareVisitor.SoftwareVisitorException
import java.nio.file.Files
import java.nio.file.Path

private data class CliOptions(
    val input: Path,
    val outputDir: Path,
    val printAst: Boolean
)

private fun parseArgs(args: Array<String>): CliOptions {
    var input: Path? = null
    var outputDir = Path.of("build/solace")
    var printAst = false
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--out", "-o" -> {
                i++
                if (i >= args.size) error("Missing value for $arg")
                outputDir = Path.of(args[i])
            }
            "--print-ast" -> {
                printAst = true
            }
            "--help", "-h" -> {
                throw IllegalStateException("")
            }
            else -> {
                if (input != null) error("Unexpected argument: $arg")
                input = Path.of(arg)
            }
        }
        i++
    }
    if (input == null) {
        input = Path.of("../pseudocode.solace")
        println("No input provided, defaulting to ${input.toAbsolutePath()}")
    }
    return CliOptions(input, outputDir, printAst)
}

private fun parseTopology(path: Path): Pair<SolaceParser, SolaceParser.ProgramContext> {
    val input = CharStreams.fromPath(path)
    val lexer = SolaceLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = SolaceParser(tokens)
    val tree = parser.program()
    return parser to tree
}

private fun compileFile(rawInput: Path, outputDir: Path, printAst: Boolean) {
    val input = resolveInputPath(rawInput)
    val (parser, tree) = parseTopology(input)
    if (printAst) {
        println(prettyTree(tree, parser))
    }
    if (parser.numberOfSyntaxErrors > 0) {
        println("Parsed '${input.toAbsolutePath()}'. Syntax errors: ${parser.numberOfSyntaxErrors}")
        return
    }

    try {
        val hardwareBytecode = buildHardwareBytecode(tree)
        val softwareBytecode = buildSoftwareBytecode(tree)
        val bytecodeByNode = hardwareBytecode + softwareBytecode
        val topology = analyzeProgram(tree)
        val outputName = packageNameFor(input)
        val outputPath = writeProgramPackage(topology, bytecodeByNode, outputDir, outputName)
        println("Compiled ${topology.nodes.size} node(s) to ${outputPath.toAbsolutePath()}")
    } catch (ex: ValidationException) {
        println("Validation error: ${ex.message}")
    } catch (ex: HardwareVisitorException) {
        println("Hardware compilation error: ${ex.message}")
    } catch (ex: SoftwareVisitorException) {
        println("Software compilation error: ${ex.message}")
    }
}

fun main(args: Array<String>) {
    val options = try {
        parseArgs(args)
    } catch (ex: IllegalStateException) {
        if (ex.message.isNullOrBlank()) {
            println("Usage: solace-compiler <input.solace> [--out <outputDir>] [--print-ast] [--help]")
            println("Options:")
            println("  --out, -o <dir>     Output directory (default: build/solace)")
            println("  --print-ast         Print parsed AST before validation/compilation")
            println("  --help, -h          Show this help")
        } else {
            println("Error: ${ex.message}")
            println("Usage: solace-compiler <input.solace> [--out <outputDir>] [--print-ast] [--help]")
        }
        return
    }
    compileFile(options.input, options.outputDir, options.printAst)
}

private fun packageNameFor(input: Path): String {
    val fileName = input.fileName.toString()
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    return if (base.isBlank()) "program.solpkg" else "$base.solpkg"
}

private fun resolveInputPath(path: Path): Path {
    if (Files.exists(path)) return path
    if (!path.isAbsolute) {
        val parent = Path.of("").toAbsolutePath().parent
        if (parent != null) {
            val fallback = parent.resolve(path)
            if (Files.exists(fallback)) {
                println("Input not found at ${path.toAbsolutePath()}, using ${fallback.toAbsolutePath()}")
                return fallback
            }
        }
    }
    throw IllegalArgumentException("Input file not found: ${path.toAbsolutePath()}")
}
