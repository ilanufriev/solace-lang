package solace.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser

class NetworkAnalysisTest {
    private fun analyze(source: String): NetworkTopology {
        val parser = SolaceParser(CommonTokenStream(SolaceLexer(CharStreams.fromString(source))))
        val tree = parser.program()
        assertEquals(0, parser.numberOfSyntaxErrors, "Parser should not report syntax errors")
        return analyzeProgram(tree)
    }

    @Test
    fun collectsNodesAndConnections() {
        val topology = analyze(
            """
            node A : hardware (
                out: aOut;
            ) { init { } run { } }

            node B : software (
                in: bIn;
            ) { init { } run { } }

            network {
                A.aOut -> B.bIn;
            }
            """.trimIndent()
        )

        assertEquals(2, topology.nodes.size)
        val nodeA = topology.nodes.first { it.name == "A" }
        assertEquals(NodeType.HARDWARE, nodeA.type)
        assertEquals(emptyList<String>(), nodeA.ports.inputs)
        assertEquals(listOf("aOut"), nodeA.ports.outputs)

        val connection = topology.connections.single()
        assertEquals("A", connection.from.node)
        assertEquals("aOut", connection.from.port)
        assertEquals("B", connection.to.node)
        assertEquals("bIn", connection.to.port)
    }

    @Test
    fun failsOnUnknownNode() {
        assertFailsWith<ValidationException> {
            analyze(
                """
                node A : hardware () { init { } run { } }
                network { A.x -> B.y; }
                """.trimIndent()
            )
        }
    }

    @Test
    fun failsOnDuplicateConnections() {
        assertFailsWith<ValidationException> {
            analyze(
                """
                node A : hardware ( out: x; ) { init { } run { } }
                node B : hardware ( in: y; ) { init { } run { } }
                network {
                    A.x -> B.y;
                    A.x -> B.y;
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun failsOnUnusedPorts() {
        assertFailsWith<ValidationException> {
            analyze(
                """
                node A : hardware ( out: used, unused; ) { init { } run { } }
                node B : hardware ( in: recv; ) { init { } run { } }
                network { A.used -> B.recv; }
                """.trimIndent()
            )
        }
    }

    @Test
    fun selfUsedInCodeIsNotReportedUnused() {
        analyze(
            """
            node A : hardware (
                self: loop;
            ) {
                init { }
                run { x = ${'$'}loop?; loop <- x; }
            }
            network { }
            """.trimIndent()
        )
    }

    @Test
    fun failsOnUnusedSelfPort() {
        assertFailsWith<ValidationException> {
            analyze(
                """
                node A : hardware (
                    out: o;
                    self: used, unused;
                ) { init { } run { x = ${'$'}used; } }
                node B : hardware ( in: i; ) { init { } run { } }
                network { A.o -> B.i; }
                """.trimIndent()
            )
        }
    }

    @Test
    fun failsOnDuplicatePorts() {
        assertFailsWith<ValidationException> {
            analyze(
                """
                node A : hardware (
                    in: x, x;
                ) { init { } run { } }
                """.trimIndent()
            )
        }
    }
}
