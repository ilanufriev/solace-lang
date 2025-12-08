package solace.compiler

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser
import solace.network.SimNodeVmFactory
import solace.network.buildNetwork
import solace.network.loadProgramPackage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackagingIntegrationTest {
    private fun parse(source: String): SolaceParser.ProgramContext {
        val parser = SolaceParser(CommonTokenStream(SolaceLexer(CharStreams.fromString(source))))
        val tree = parser.program()
        assertEquals(0, parser.numberOfSyntaxErrors, "Parser should not report syntax errors")
        return tree
    }

    @Test
    fun `compiler emits runnable solpkg for hardware network`() = runBlocking {
        val program = parse(
            """
            node Pulse : hardware (
                out: tick;
            ) {
                init {}
                run {
                    tick <- 1;
                }
            }

            node Accumulator : hardware (
                in: inc;
                out: total;
                self: state;
            ) {
                init {
                    state <- 0;
                }
                run {
                    s = ${'$'}state;
                    v = ${'$'}inc;
                    sum = s + v;
                    total <- sum;
                    state <- sum;
                }
            }

            network {
                Pulse.tick -> Accumulator.inc;
            }
            """.trimIndent()
        )

        val bytecode = buildHardwareBytecode(program)
        val topology = analyzeProgram(program)
        val outputDir = Files.createTempDirectory("solace-package")
        val packagePath = writeProgramPackage(topology, bytecode, outputDir)

        val loaded = loadProgramPackage(packagePath)
        assertEquals(2, loaded.nodes.size)
        assertTrue(loaded.nodes.all { it.bytecode.isNotEmpty() })

        val network = buildNetwork(loaded)
        val jobs = network.launch(this, SimNodeVmFactory())

        val totalChannel = network.nodes
            .single { it.descriptor.name == "Accumulator" }
            .ports
            .outputs
            .getValue("total") as Channel<Any?>

        val results = listOf(
            totalChannel.receive() as Int,
            totalChannel.receive() as Int,
            totalChannel.receive() as Int
        )
        assertEquals(listOf(1, 2, 3), results)

        jobs.forEach { it.cancelAndJoin() }
    }
}
